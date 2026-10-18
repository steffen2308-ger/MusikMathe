package de.steffen.musikmathe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
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
        start(context,url,type,headers,output,clipMs,"",listener);
    }
    void start(Context context,String url,String type,Map<String,String> headers,File output,long clipMs,String audioUrl,Listener listener){
        this.listener=listener;
        try{
            DefaultMediaSourceFactory sources=new DefaultMediaSourceFactory(()->new VideoHttpSource(url,headers));
            ExoPlayerAssetLoader.Factory assets=new ExoPlayerAssetLoader.Factory(context,new DefaultDecoderFactory.Builder(context).build(),Clock.DEFAULT,sources);
            transformer=new Transformer.Builder(context).setAssetLoaderFactory(assets).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(new Transformer.Listener(){
                @Override public void onCompleted(Composition composition,ExportResult result){transformer=null;handler.removeCallbacks(tick);listener.completed();}
                @Override public void onError(Composition composition,ExportResult result,ExportException error){transformer=null;handler.removeCallbacks(tick);String code=error.getErrorCodeName();
                    listener.failed(code.contains("DRM")?"Die Quelle ist kopiergeschützt.":code.contains("ENCOD")||code.contains("DECOD")?"Dieses Videoformat wird auf dem Gerät nicht unterstützt.":code.contains("IO")?"Die Videoquelle ist nicht erreichbar, abgelaufen oder verlangt zusätzliche Anmeldung.":"Diese Streamingquelle kann nicht als MP4 exportiert werden. Versuche einen begrenzten Ausschnitt oder eine andere gefundene Quelle.");}
            }).build();
            MediaItem.Builder item=new MediaItem.Builder().setUri(url);
            if("HLS".equals(type))item.setMimeType(MimeTypes.APPLICATION_M3U8);if("DASH".equals(type))item.setMimeType(MimeTypes.APPLICATION_MPD);
            if(clipMs>0)item.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(clipMs).build());
            if(Address.isWeb(audioUrl)){
                MediaItem.Builder audio=new MediaItem.Builder().setUri(audioUrl);if(clipMs>0)audio.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(clipMs).build());
                EditedMediaItem videoItem=new EditedMediaItem.Builder(item.build()).setRemoveAudio(true).build();
                EditedMediaItem audioItem=new EditedMediaItem.Builder(audio.build()).setRemoveVideo(true).build();
                Composition composition=new Composition.Builder(new EditedMediaItemSequence.Builder(videoItem).build(),new EditedMediaItemSequence.Builder(audioItem).build()).build();
                transformer.start(composition,output.getAbsolutePath());
            }else transformer.start(item.build(),output.getAbsolutePath());handler.post(tick);
        }catch(Exception error){cancel();listener.failed(error.getMessage()==null?"Videoexport konnte nicht gestartet werden.":error.getMessage());}
    }
    void cancel(){handler.removeCallbacks(tick);if(transformer!=null){transformer.cancel();transformer=null;}listener=null;}
}
