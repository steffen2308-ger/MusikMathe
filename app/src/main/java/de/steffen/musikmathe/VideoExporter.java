package de.steffen.musikmathe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultDecoderFactory;
import androidx.media3.transformer.ExoPlayerAssetLoader;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.util.Map;

/** Main-looper Media3 export: adaptive HLS/DASH audio/video become one MP4. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
final class VideoExporter {
    interface Listener{void completed();void failed(String message);void progress(int percent);}
    private Transformer transformer;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ProgressHolder progress=new ProgressHolder();
    private final Runnable tick=new Runnable(){public void run(){if(transformer==null)return;int state=transformer.getProgress(progress);if(listener!=null)listener.progress(state==Transformer.PROGRESS_STATE_AVAILABLE?progress.progress:-1);handler.postDelayed(this,1000);}};
    private Listener listener;
    void start(Context context,String url,String type,Map<String,String> headers,File output,long clipMs,Listener listener){
        this.listener=listener;
        try{
            DefaultMediaSourceFactory sources=new DefaultMediaSourceFactory(()->new VideoHttpSource(url,headers));
            ExoPlayerAssetLoader.Factory assets=new ExoPlayerAssetLoader.Factory(context,new DefaultDecoderFactory.Builder(context).build(),Clock.DEFAULT,sources);
            transformer=new Transformer.Builder(context).setAssetLoaderFactory(assets).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(new Transformer.Listener(){
                @Override public void onCompleted(Composition composition,ExportResult result){transformer=null;handler.removeCallbacks(tick);listener.completed();}
                @Override public void onError(Composition composition,ExportResult result,ExportException error){transformer=null;handler.removeCallbacks(tick);listener.failed(error.getErrorCodeName()+": "+(error.getCause()==null?error.getMessage():error.getCause().getMessage()));}
            }).build();
            MediaItem.Builder item=new MediaItem.Builder().setUri(url);
            if("HLS".equals(type))item.setMimeType(MimeTypes.APPLICATION_M3U8);if("DASH".equals(type))item.setMimeType(MimeTypes.APPLICATION_MPD);
            if(clipMs>0)item.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(clipMs).build());
            transformer.start(item.build(),output.getAbsolutePath());handler.post(tick);
        }catch(Exception error){cancel();listener.failed(error.getMessage()==null?"Videoexport konnte nicht gestartet werden.":error.getMessage());}
    }
    void cancel(){handler.removeCallbacks(tick);if(transformer!=null){transformer.cancel();transformer=null;}listener=null;}
}
