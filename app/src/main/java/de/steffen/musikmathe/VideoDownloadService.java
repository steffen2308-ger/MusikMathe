package de.steffen.musikmathe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Independent, bounded exports per browser process; sources and credentials stay in memory. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
public class VideoDownloadService extends Service {
    static final int MAX_PARALLEL=3;
    static volatile VideoDownloadService current;
    static volatile String status="";
    static final String SHOW_DOWNLOADS="de.steffen.musikmathe.SHOW_VIDEO_DOWNLOADS";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(MAX_PARALLEL);
    private final ExecutorService aborts=Executors.newFixedThreadPool(MAX_PARALLEL);
    private final ArrayDeque<Job> waiting=new ArrayDeque<>();
    // A cancelling worker retains its slot until all streams have closed.
    private final LinkedHashMap<String,Job> running=new LinkedHashMap<>();
    private final List<Runnable> settled=new ArrayList<>();
    private boolean destroyed,advancing;
    private int lastStartId;
    private static final class Job {
        final String id=UUID.randomUUID().toString();
        final Uri destination;
        final String url,type,audio,title;
        final boolean original;
        final long clip;
        final Map<String,String> headers=new LinkedHashMap<>();
        File temporary;
        VideoExporter exporter;
        boolean working;
        volatile Thread thread;
        volatile VideoHttpSource source;
        volatile boolean cancelled;
        volatile VideoProgress progress;
        Job(Intent intent)throws Exception{
            destination=Uri.parse(intent.getStringExtra("destination"));url=intent.getStringExtra("url");type=intent.getStringExtra("type");
            audio=intent.getStringExtra("audio");String label=intent.getStringExtra("title");title=label==null||label.trim().isEmpty()?"Video":label;
            original=intent.getBooleanExtra("original",false);clip=intent.getLongExtra("clip",0);
            if(!Address.isWeb(url)||!"content".equals(destination.getScheme()))throw new IllegalArgumentException("Ungültiges Videoziel.");
            JSONObject json=new JSONObject(intent.getStringExtra("headers"));Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();headers.put(key,json.getString(key));}
        }
    }
    static final class QueuedVideo {
        final String id,title;
        QueuedVideo(Job job){id=job.id;title=job.title;}
    }
    static final class ActiveVideo {
        final String id,title;
        final boolean cancelling;
        final VideoProgress.Reading progress;
        ActiveVideo(Job job,long now){id=job.id;title=job.title;cancelling=job.cancelled;progress=job.progress==null?null:job.progress.read(now);}
    }
    static final class Snapshot {
        final boolean running;
        final String title,message;
        final VideoProgress.Reading progress;
        final List<ActiveVideo> active;
        final List<QueuedVideo> waiting;
        Snapshot(List<ActiveVideo> active,List<QueuedVideo> waiting){this.active=active;this.waiting=waiting;running=!active.isEmpty();title=running?active.get(0).title:"";progress=running?active.get(0).progress:null;message=status;}
    }
    /** Main-thread view: stable opaque identities, independent readings, no sources or credentials. */
    static Snapshot snapshot(){
        VideoDownloadService service=current;List<ActiveVideo> active=new ArrayList<>();List<QueuedVideo> queued=new ArrayList<>();
        if(service!=null){long now=SystemClock.elapsedRealtime();for(Job job:service.running.values())active.add(new ActiveVideo(job,now));for(Job job:service.waiting)queued.add(new QueuedVideo(job));}
        return new Snapshot(active,queued);
    }
    private final Runnable heartbeat=new Runnable(){public void run(){if(destroyed||current!=VideoDownloadService.this)return;publish(false);main.postDelayed(this,1000);}};
    static boolean busy(){return current!=null;}
    static void cancelCurrent(){VideoDownloadService service=current;if(service!=null&&!service.running.isEmpty())service.cancelJob(service.running.values().iterator().next());}
    static boolean cancelActive(String id){VideoDownloadService service=current;Job job=service==null?null:service.running.get(id);if(job==null||job.cancelled)return false;service.cancelJob(job);return true;}
    static void cancelAll(){cancelAll(null);}
    static void cancelAll(Runnable completed){VideoDownloadService service=current;if(service==null){if(completed!=null)completed.run();return;}if(completed!=null)service.settled.add(completed);service.clearAll();}
    /** A stale waiting-row button never cancels a promoted or unrelated job. */
    static boolean cancelWaiting(String id){
        VideoDownloadService service=current;if(service==null)return false;
        for(Iterator<Job> rows=service.waiting.iterator();rows.hasNext();){Job job=rows.next();if(!job.id.equals(id))continue;rows.remove();job.cancelled=true;service.discard(job.destination);service.publish(false);return true;}return false;
    }
    static List<String> queueLabels(){List<String> rows=new ArrayList<>();VideoDownloadService service=current;if(service!=null){for(Job job:service.running.values())rows.add((job.cancelled?"Wird abgebrochen: ":"Wird gespeichert: ")+job.title);for(Job job:service.waiting)rows.add("Wartet: "+job.title);}return rows;}
    @Override public IBinder onBind(Intent intent){return null;}
    private boolean privateMode(){return this instanceof PrivateVideoDownloadService;}
    private int notificationId(){return privateMode()?311:310;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        lastStartId=startId;
        if(intent==null){if(running.isEmpty()&&waiting.isEmpty())stopSelfResult(startId);return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){cancelCurrent();if(running.isEmpty()&&waiting.isEmpty())stopQueue();return START_NOT_STICKY;}
        if("cancel-job".equals(intent.getAction())){cancelActive(intent.getStringExtra("job"));if(running.isEmpty()&&waiting.isEmpty())stopQueue();return START_NOT_STICKY;}
        if("cancel-all".equals(intent.getAction())){clearAll();return START_NOT_STICKY;}
        current=this;publish(true);main.removeCallbacks(heartbeat);main.postDelayed(heartbeat,1000);
        try{
            Job job=new Job(intent);
            if(running.values().stream().anyMatch(row->row.destination.equals(job.destination))||waiting.stream().anyMatch(row->row.destination.equals(job.destination))){Toast.makeText(this,"Diese Zieldatei wird bereits gespeichert.",Toast.LENGTH_LONG).show();return START_NOT_STICKY;}
            waiting.addLast(job);advance();
        }catch(Exception error){String raw=intent.getStringExtra("destination");if(raw!=null){Uri destination=Uri.parse(raw);if(running.values().stream().noneMatch(row->row.destination.equals(destination))&&waiting.stream().noneMatch(row->row.destination.equals(destination)))discard(destination);}status="Videospeicherung konnte nicht vorgemerkt werden.";Toast.makeText(this,status,Toast.LENGTH_LONG).show();advance();}
        return START_NOT_STICKY;
    }
    private void advance(){
        if(destroyed||advancing)return;advancing=true;
        try{while(running.size()<MAX_PARALLEL&&!waiting.isEmpty()){Job job=waiting.removeFirst();running.put(job.id,job);start(job);}}finally{advancing=false;}
        if(running.isEmpty()&&waiting.isEmpty())stopQueue();else publish(false);
    }
    private boolean isActive(Job job){return !destroyed&&running.get(job.id)==job&&!job.cancelled;}
    private void start(Job job){
        try{
            File folder=new File(getCacheDir(),privateMode()?"video-private":"video-export");if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("Kein Speicherplatz für den Videoexport.");job.temporary=File.createTempFile("export-",".part",folder);
            VideoProgress transfer=new VideoProgress(job.original?"Schritt 1/2 · Videodatei herunterladen":"Schritt 1/2 · Video laden und MP4 erstellen",SystemClock.elapsedRealtime());job.progress=transfer;
            if(job.original)runWork(job,()->{
                VideoHttpSource source=new VideoHttpSource(job.url,job.headers);job.source=source;
                try{
                    check(job);long length=source.open(new DataSpec(Uri.parse(job.url)));transfer.bytes(0,length,SystemClock.elapsedRealtime());long total=0;byte[] buffer=new byte[128*1024];int count;
                    try(OutputStream out=new FileOutputStream(job.temporary)){while((count=source.read(buffer,0,buffer.length))!=C.RESULT_END_OF_INPUT){check(job);out.write(buffer,0,count);total+=count;transfer.bytes(total,length,SystemClock.elapsedRealtime());}}
                }finally{source.close();job.source=null;}
                copyResult(job);
            });
            else{
                job.exporter=new VideoExporter();job.exporter.start(this,job.url,job.type,job.headers,job.temporary,job.clip,job.audio==null?"":job.audio,new VideoExporter.Listener(){
                    public void completed(){if(isActive(job))runWork(job,()->copyResult(job));}
                    public void failed(String message){finish(job,false,"MP4-Export nicht möglich. "+message);}
                    public void progress(int value){if(isActive(job))transfer.percent(value,SystemClock.elapsedRealtime());}
                },new TransferListener(){
                    public void onTransferInitializing(DataSource source,DataSpec spec,boolean network){}
                    public void onTransferStart(DataSource source,DataSpec spec,boolean network){}
                    public void onBytesTransferred(DataSource source,DataSpec spec,boolean network,int bytes){transfer.received(bytes,SystemClock.elapsedRealtime());}
                    public void onTransferEnd(DataSource source,DataSpec spec,boolean network){}
                });
            }
        }catch(Exception error){finish(job,false,"Videospeicherung fehlgeschlagen: "+error.getMessage());}
    }
    private interface Work{void run()throws Exception;}
    private void runWork(Job job,Work work){
        job.working=true;workers.execute(()->{
            synchronized(job){job.thread=Thread.currentThread();}boolean success=false;String message="Video gespeichert.";
            try{check(job);work.run();check(job);success=true;}catch(Exception error){message="Videodownload fehlgeschlagen: "+error.getMessage();}
            finally{synchronized(job){job.thread=null;}Thread.interrupted();boolean done=success;String result=message;main.post(()->finish(job,done,result));}
        });
    }
    private static void check(Job job)throws InterruptedException{if(job.cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException("Abgebrochen");}
    private void copyResult(Job job)throws Exception{
        check(job);VideoProgress copying=new VideoProgress("Schritt 2/2 · Im Zielordner speichern",SystemClock.elapsedRealtime());long length=job.temporary.length();copying.bytes(0,length,SystemClock.elapsedRealtime());job.progress=copying;
        try(OutputStream output=openResult(job);InputStream input=new FileInputStream(job.temporary)){
            if(output==null)throw new java.io.IOException("Zieldatei kann nicht geöffnet werden.");byte[] buffer=new byte[128*1024];int count;long total=0;
            while((count=input.read(buffer))!=-1){check(job);output.write(buffer,0,count);total+=count;copying.bytes(total,length,SystemClock.elapsedRealtime());}
        }check(job);
    }
    private OutputStream openResult(Job job)throws Exception{
        check(job);OutputStream output=getContentResolver().openOutputStream(job.destination,"w");if(job.cancelled){if(output!=null)output.close();throw new InterruptedException("Abgebrochen");}return output;
    }
    private void publish(boolean start){
        Snapshot snapshot=snapshot();String text=snapshot.active.size()+" gleichzeitig"+(waiting.isEmpty()?"":" · "+waiting.size()+" in Warteschlange");
        if(snapshot.active.size()==1&&snapshot.progress!=null)text=snapshot.progress.phase+(snapshot.progress.percent<0?"":" · "+snapshot.progress.percent+" %")+(waiting.isEmpty()?"":" · "+waiting.size()+" in Warteschlange");
        status=text;NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("video-downloads","Videos speichern",NotificationManager.IMPORTANCE_LOW));
        PendingIntent clear=PendingIntent.getService(this,33,new Intent(this,getClass()).setAction("cancel-all"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(this,privateMode()?PrivateActivity.class:MainActivity.class).setAction(SHOW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAction=PendingIntent.getActivity(this,32,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"video-downloads"):new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(privateMode()?"Private Videos speichern":"Videos speichern").setContentText(text).setContentIntent(openAction).setOngoing(true).setOnlyAlertOnce(true).setVisibility(privateMode()?Notification.VISIBILITY_SECRET:Notification.VISIBILITY_PRIVATE);
        if(snapshot.active.size()==1){ActiveVideo video=snapshot.active.get(0);int percent=video.progress==null?-1:video.progress.percent;builder.setProgress(100,Math.max(0,percent),percent<0);
            PendingIntent cancel=PendingIntent.getService(this,31,new Intent(this,getClass()).setAction("cancel-job").setData(Uri.parse("bestbrowser://download/"+video.id)).putExtra("job",video.id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Video abbrechen",cancel);}
        else builder.setProgress(0,0,true);
        if(snapshot.active.size()>1||!waiting.isEmpty())builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Alle abbrechen",clear);
        StringBuilder detail=new StringBuilder(text);for(ActiveVideo video:snapshot.active){detail.append("\n\n").append(privateMode()?"Video":video.title).append(video.cancelling?" · Wird abgebrochen …":"");if(video.progress!=null)detail.append("\n").append(video.progress.phase).append(video.progress.percent<0?"":" · "+video.progress.percent+" %").append("\n").append(video.progress.detail());}
        builder.setStyle(new Notification.BigTextStyle().bigText(detail));Notification notification=builder.build();
        if(start)startForeground(notificationId(),notification);else if(Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)manager.notify(notificationId(),notification);
    }
    private void finish(Job job,boolean success,String message){
        if(running.get(job.id)!=job)return;
        if(job.cancelled){success=false;message="Videospeicherung abgebrochen.";}job.cancelled=true;
        if(job.exporter!=null)job.exporter.cancel();if(success)release(job.destination);else discard(job.destination);
        if(job.temporary!=null)job.temporary.delete();running.remove(job.id);status=message;
        if(destroyed){if(running.isEmpty())notifySettled();return;}Toast.makeText(this,message,Toast.LENGTH_LONG).show();advance();
    }
    private void cancelJob(Job job){
        synchronized(job){if(job.cancelled)return;job.cancelled=true;if(job.thread!=null)job.thread.interrupt();}
        if(job.exporter!=null)job.exporter.cancel();
        aborts.execute(()->{VideoHttpSource source=job.source;if(source!=null)source.cancel();});
        // Delete now and again after the worker exits, covering a provider that was still opening.
        discard(job.destination);if(!job.working)finish(job,false,"Videospeicherung abgebrochen.");else if(!destroyed)publish(false);
    }
    private void clearAll(){for(Job job:waiting)discard(job.destination);waiting.clear();for(Job job:new ArrayList<>(running.values()))cancelJob(job);if(running.isEmpty())stopQueue();}
    private void stopQueue(){
        main.removeCallbacks(heartbeat);if(current==this)current=null;stopForeground(true);stopSelfResult(lastStartId);
        notifySettled();
    }
    private void notifySettled(){
        List<Runnable> callbacks=new ArrayList<>(settled);settled.clear();for(Runnable callback:callbacks)callback.run();
    }
    private void release(Uri uri){if(DownloadFolder.isTree(uri))return;try{getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}}
    private void discard(Uri uri){deleteDocument(uri);release(uri);}
    private void deleteDocument(Uri uri){
        if(uri==null||!"content".equals(uri.getScheme()))return;
        try{DocumentsContract.getDocumentId(uri);if(DocumentsContract.deleteDocument(getContentResolver(),uri))return;}catch(Exception ignored){}
        try{getContentResolver().delete(uri,null,null);}catch(Exception ignored){}
    }
    @Override public void onTimeout(int startId,int fgsType){clearAll();stopForeground(true);stopSelf();}
    @Override public void onDestroy(){
        destroyed=true;main.removeCallbacks(heartbeat);for(Job job:waiting)discard(job.destination);waiting.clear();for(Job job:new ArrayList<>(running.values()))cancelJob(job);
        if(current==this)current=null;workers.shutdown();aborts.shutdown();if(running.isEmpty())notifySettled();super.onDestroy();
    }
}
