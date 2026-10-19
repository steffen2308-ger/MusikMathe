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
import android.provider.DocumentsContract;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSpec;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** FIFO exports per browser process. Media URLs and credentials stay in memory. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
public class VideoDownloadService extends Service {
    static volatile VideoDownloadService current;
    static volatile String status="";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayDeque<Job> waiting=new ArrayDeque<>();
    private Job active;
    private boolean destroyed;
    private int lastStartId;
    private int percent=-1;
    private String progressText="";
    private static final int NOTIFICATION=310;
    private static final class Job {
        final Uri destination;
        final String url,type,audio,title;
        final boolean original;
        final long clip;
        final Map<String,String> headers=new LinkedHashMap<>();
        File temporary;
        VideoExporter exporter;
        volatile boolean cancelled;
        Job(Intent intent)throws Exception{
            destination=Uri.parse(intent.getStringExtra("destination"));url=intent.getStringExtra("url");type=intent.getStringExtra("type");
            audio=intent.getStringExtra("audio");String label=intent.getStringExtra("title");title=label==null||label.trim().isEmpty()?"Video":label;
            original=intent.getBooleanExtra("original",false);clip=intent.getLongExtra("clip",0);
            if(!Address.isWeb(url)||!"content".equals(destination.getScheme()))throw new IllegalArgumentException("Ungültiges Videoziel.");
            JSONObject json=new JSONObject(intent.getStringExtra("headers"));java.util.Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();headers.put(key,json.getString(key));}
        }
    }
    static boolean busy(){return current!=null;}
    static void cancelCurrent(){VideoDownloadService service=current;if(service!=null)service.cancel();}
    static void cancelAll(){VideoDownloadService service=current;if(service!=null)service.clearAll();}
    /** Called by the browser on the main thread; never includes URLs or credentials. */
    static List<String> queueLabels(){
        List<String> rows=new ArrayList<>();VideoDownloadService service=current;
        if(service!=null){if(service.active!=null)rows.add("Wird gespeichert: "+service.active.title);for(Job job:service.waiting)rows.add("Wartet: "+job.title);}return rows;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private boolean privateMode(){return this instanceof PrivateVideoDownloadService;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        lastStartId=startId;
        if(intent==null){if(active==null)stopSelfResult(startId);return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){cancel();return START_NOT_STICKY;}
        if("cancel-all".equals(intent.getAction())){clearAll();return START_NOT_STICKY;}
        // Every startForegroundService call is acknowledged, including additions to an existing queue.
        current=this;if(active==null)progressText="Video wird vorbereitet …";publish(true);
        try{
            Job job=new Job(intent);
            if((active!=null&&active.destination.equals(job.destination))||waiting.stream().anyMatch(row->row.destination.equals(job.destination))){
                Toast.makeText(this,"Diese Zieldatei steht bereits in der Warteschlange.",Toast.LENGTH_LONG).show();return START_NOT_STICKY;
            }
            waiting.addLast(job);if(active==null)next();else publish(false);
        }catch(Exception error){
            String raw=intent.getStringExtra("destination");if(raw!=null)discard(Uri.parse(raw));
            Toast.makeText(this,"Videospeicherung konnte nicht vorgemerkt werden.",Toast.LENGTH_LONG).show();
            if(active==null){status="Videospeicherung konnte nicht vorgemerkt werden.";stopQueue();}
        }
        return START_NOT_STICKY;
    }
    private void next(){
        if(destroyed)return;
        active=waiting.pollFirst();
        if(active==null){stopQueue();return;}
        Job job=active;percent=-1;progressText="Video wird vorbereitet …";publish(false);
        // Wait for the previous worker to leave its streams before starting another export.
        worker.execute(()->main.post(()->{if(isActive(job))start(job);}));
    }
    private boolean isActive(Job job){return !destroyed&&active==job&&!job.cancelled;}
    private void start(Job job){
        try{
            File folder=new File(getCacheDir(),privateMode()?"video-private":"video-export");if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("Kein Speicherplatz für den Videoexport.");
            job.temporary=File.createTempFile("export-",".part",folder);
            if(job.original){
                worker.execute(()->{
                    try{
                        VideoHttpSource source=new VideoHttpSource(job.url,job.headers);
                        try{
                            if(job.cancelled)throw new InterruptedException();
                            long length=source.open(new DataSpec(Uri.parse(job.url)));long total=0;byte[] buffer=new byte[128*1024];int count;
                            try(OutputStream out=new FileOutputStream(job.temporary)){
                                while(!job.cancelled&&(count=source.read(buffer,0,buffer.length))!=C.RESULT_END_OF_INPUT){
                                    if(job.cancelled)throw new InterruptedException();out.write(buffer,0,count);total+=count;
                                    int value=length>0?(int)Math.min(99,total*100/length):-1;main.post(()->update(job,value,"Videodatei wird geladen …"));
                                }
                            }
                        }finally{source.close();}
                        copyResult(job);
                    }catch(Exception error){main.post(()->finish(job,false,job.cancelled?"Videospeicherung abgebrochen.":"Videodownload fehlgeschlagen: "+error.getMessage()));}
                    finally{if(job.cancelled&&job.temporary!=null)job.temporary.delete();}
                });
            }else{
                job.exporter=new VideoExporter();job.exporter.start(this,job.url,job.type,job.headers,job.temporary,job.clip,job.audio==null?"":job.audio,new VideoExporter.Listener(){
                    public void completed(){if(isActive(job))worker.execute(()->{try{copyResult(job);}catch(Exception error){main.post(()->finish(job,false,"Speichern fehlgeschlagen: "+error.getMessage()));}});}
                    public void failed(String message){finish(job,false,"MP4-Export nicht möglich. "+message);}
                    public void progress(int value){update(job,value,"MP4 mit Bild und Ton wird erstellt …");}
                });
            }
        }catch(Exception error){finish(job,false,"Videospeicherung fehlgeschlagen: "+error.getMessage());}
    }
    private void copyResult(Job job)throws Exception{
        if(job.cancelled)throw new InterruptedException();main.post(()->update(job,-1,"Video wird im gewählten Ordner gespeichert …"));
        try(OutputStream output=openResult(job);InputStream input=new FileInputStream(job.temporary)){
            if(output==null)throw new java.io.IOException("Zieldatei kann nicht geöffnet werden.");byte[] buffer=new byte[128*1024];int count;
            while((count=input.read(buffer))!=-1){if(job.cancelled)throw new InterruptedException();output.write(buffer,0,count);}
        }
        if(job.cancelled)throw new InterruptedException();main.post(()->finish(job,true,"Video gespeichert."));
    }
    private OutputStream openResult(Job job)throws Exception{
        synchronized(job){if(job.cancelled)throw new InterruptedException();return getContentResolver().openOutputStream(job.destination,"w");}
    }
    private void update(Job job,int value,String message){
        if(!isActive(job)||(percent==value&&progressText.equals(message)))return;percent=value;progressText=message;publish(false);
    }
    private void publish(boolean start){
        status=progressText+(percent<0?"":" · "+percent+" %")+(waiting.isEmpty()?"":" · "+waiting.size()+" in Warteschlange");
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("video-downloads","Videos speichern",NotificationManager.IMPORTANCE_LOW));
        PendingIntent cancelAction=PendingIntent.getService(this,31,new Intent(this,getClass()).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent clearAction=PendingIntent.getService(this,33,new Intent(this,getClass()).setAction("cancel-all"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(this,privateMode()?PrivateActivity.class:MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAction=PendingIntent.getActivity(this,32,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"video-downloads"):new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(privateMode()?"Private Videos speichern":"Videos speichern").setContentText(status).setContentIntent(openAction).setOngoing(true).setOnlyAlertOnce(true).setVisibility(privateMode()?Notification.VISIBILITY_SECRET:Notification.VISIBILITY_PRIVATE).setProgress(100,Math.max(0,percent),percent<0).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Aktuellen abbrechen",cancelAction);
        if(!waiting.isEmpty())builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Alle abbrechen",clearAction);
        Notification notification=builder.build();
        if(start)startForeground(NOTIFICATION,notification);else if(Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)manager.notify(NOTIFICATION,notification);
    }
    private void finish(Job job,boolean success,String message){
        if(active!=job||destroyed)return;
        synchronized(job){job.cancelled=true;if(!success)deleteDocument(job.destination);}
        if(job.exporter!=null)job.exporter.cancel();release(job.destination);
        if(job.temporary!=null)job.temporary.delete();active=null;status=message;
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();next();
    }
    private void stopQueue(){if(current==this)current=null;stopForeground(true);stopSelfResult(lastStartId);}
    private void release(Uri uri){if(DownloadFolder.isTree(uri))return;try{getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}}
    private void discard(Uri uri){deleteDocument(uri);release(uri);}
    private void deleteDocument(Uri uri){if(uri==null||!"content".equals(uri.getScheme()))return;try{DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){try{getContentResolver().delete(uri,null,null);}catch(Exception ignoredAgain){}}}
    private void cancel(){if(active!=null)finish(active,false,"Videospeicherung abgebrochen.");else stopQueue();}
    private void clearAll(){for(Job job:waiting)discard(job.destination);waiting.clear();cancel();}
    @Override public void onTimeout(int startId,int fgsType){clearAll();}
    @Override public void onDestroy(){
        destroyed=true;for(Job job:waiting)discard(job.destination);waiting.clear();
        if(active!=null){synchronized(active){active.cancelled=true;discard(active.destination);}if(active.exporter!=null)active.exporter.cancel();if(active.temporary!=null)active.temporary.delete();active=null;}
        if(current==this)current=null;worker.shutdownNow();super.onDestroy();
    }
}
