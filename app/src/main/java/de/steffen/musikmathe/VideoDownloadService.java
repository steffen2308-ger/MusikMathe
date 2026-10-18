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
import android.webkit.URLUtil;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSpec;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One foreground export per browser process. No media URL or credentials are persisted. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
public class VideoDownloadService extends Service {
    static volatile VideoDownloadService current;
    static volatile String status="";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private VideoExporter exporter;
    private File temporary;
    private Uri destination;
    private volatile boolean cancelled;
    private boolean finished;
    private int percent=-1;
    private static final int NOTIFICATION=310;
    static boolean busy(){return current!=null;}
    static void cancelCurrent(){VideoDownloadService service=current;if(service!=null)service.cancel();}
    @Override public IBinder onBind(Intent intent){return null;}
    private boolean privateMode(){return this instanceof PrivateVideoDownloadService;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("cancel".equals(intent.getAction())){cancel();return START_NOT_STICKY;}
        if(current!=null){deleteDocument(Uri.parse(intent.getStringExtra("destination")));return START_NOT_STICKY;}
        current=this;status="Video wird vorbereitet …";notifyProgress(true);
        try{
            destination=Uri.parse(intent.getStringExtra("destination"));String url=intent.getStringExtra("url"),type=intent.getStringExtra("type");
            if(!Address.isWeb(url)||!"content".equals(destination.getScheme()))throw new IllegalArgumentException("Ungültiges Videoziel.");
            Map<String,String> headers=new LinkedHashMap<>();JSONObject json=new JSONObject(intent.getStringExtra("headers"));java.util.Iterator<String> keys=json.keys();while(keys.hasNext()){String key=keys.next();headers.put(key,json.getString(key));}
            File folder=new File(getCacheDir(),privateMode()?"video-private":"video-export");if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("Kein Speicherplatz für den Videoexport.");
            temporary=File.createTempFile("export-",".part",folder);
            if(intent.getBooleanExtra("original",false)){
                worker.execute(()->{
                    try{VideoHttpSource source=new VideoHttpSource(url,headers);try{
                        long length=source.open(new DataSpec(Uri.parse(url)));long total=0;byte[] buffer=new byte[128*1024];int count;
                        try(OutputStream out=new FileOutputStream(temporary)){while((count=source.read(buffer,0,buffer.length))!=C.RESULT_END_OF_INPUT){if(cancelled)throw new InterruptedException();out.write(buffer,0,count);total+=count;int value=length>0?(int)Math.min(99,total*100/length):-1;main.post(()->update(value,"Videodatei wird geladen …"));}}
                    }finally{source.close();}copyResult();}catch(Exception error){main.post(()->finish(false,cancelled?"Videospeicherung abgebrochen.":"Videodownload fehlgeschlagen: "+error.getMessage()));}
                });
            }else{
                exporter=new VideoExporter();exporter.start(this,url,type,headers,temporary,intent.getLongExtra("clip",0),new VideoExporter.Listener(){
                    public void completed(){worker.execute(()->{try{copyResult();}catch(Exception error){main.post(()->finish(false,"Speichern fehlgeschlagen: "+error.getMessage()));}});}
                    public void failed(String message){finish(false,"MP4-Export nicht möglich. "+message);}
                    public void progress(int value){update(value,"MP4 mit Bild und Ton wird erstellt …");}
                });
            }
        }catch(Exception error){finish(false,"Videospeicherung fehlgeschlagen: "+error.getMessage());}
        return START_NOT_STICKY;
    }
    private void copyResult()throws Exception{
        if(cancelled)throw new InterruptedException();main.post(()->update(-1,"Video wird im gewählten Ordner gespeichert …"));
        try(InputStream input=new FileInputStream(temporary);OutputStream output=getContentResolver().openOutputStream(destination,"w")){
            if(output==null)throw new java.io.IOException("Zieldatei kann nicht geöffnet werden.");byte[] buffer=new byte[128*1024];int count;
            while((count=input.read(buffer))!=-1){if(cancelled)throw new InterruptedException();output.write(buffer,0,count);}
        }
        main.post(()->finish(!cancelled,cancelled?"Videospeicherung abgebrochen.":"Video gespeichert."));
    }
    private void update(int value,String message){if(finished||cancelled)return;String text=message+(value<0?"":" · "+value+" %");if(percent==value&&status.equals(text))return;percent=value;status=text;notifyProgress(false);}
    private void notifyProgress(boolean start){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("video-downloads","Videos speichern",NotificationManager.IMPORTANCE_LOW));
        Intent cancel=new Intent(this,getClass()).setAction("cancel");
        PendingIntent cancelAction=PendingIntent.getService(this,31,cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent open=new Intent(this,privateMode()?PrivateActivity.class:MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAction=PendingIntent.getActivity(this,32,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"video-downloads"):new Notification.Builder(this);
        Notification notification=builder.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(privateMode()?"Privates Video speichern":"Video speichern").setContentText(status).setContentIntent(openAction).setOngoing(true).setOnlyAlertOnce(true).setVisibility(privateMode()?Notification.VISIBILITY_SECRET:Notification.VISIBILITY_PRIVATE).setProgress(100,Math.max(0,percent),percent<0).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Abbrechen",cancelAction).build();
        if(start)startForeground(NOTIFICATION,notification);else if(Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)manager.notify(NOTIFICATION,notification);
    }
    private void finish(boolean success,String message){
        if(finished)return;finished=true;status=message;if(!success)deleteDocument(destination);
        if(temporary!=null)temporary.delete();if(current==this)current=null;
        if(destination!=null)try{getContentResolver().releasePersistableUriPermission(destination,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}
        stopForeground(true);Toast.makeText(this,message,Toast.LENGTH_LONG).show();stopSelf();
    }
    private void deleteDocument(Uri uri){if(uri==null)return;try{DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){try{getContentResolver().delete(uri,null,null);}catch(Exception ignoredAgain){}}}
    private void cancel(){cancelled=true;if(exporter!=null)exporter.cancel();finish(false,"Videospeicherung abgebrochen.");}
    @Override public void onTimeout(int startId,int fgsType){cancel();}
    @Override public void onDestroy(){if(!finished)cancel();worker.shutdownNow();super.onDestroy();}
}
