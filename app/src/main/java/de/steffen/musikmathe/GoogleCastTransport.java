package de.steffen.musikmathe;

import androidx.appcompat.app.AppCompatActivity;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Google Cast adapter. Nothing is initialized until the user opens Cast in a normal tab. */
final class GoogleCastTransport implements BrowserCast.Transport {
    private final AppCompatActivity activity;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private CastContext context;
    private CastSession session;
    private RemoteMediaClient remote;
    private Runnable changed=()->{};
    private Consumer<String> failed=reason->{};
    private boolean destroyed;
    private int commandGeneration;
    private String unavailable="Geräteauswahl wird vorbereitet …";
    private final RemoteMediaClient.Callback media=new RemoteMediaClient.Callback(){
        @Override public void onStatusUpdated(){
            MediaStatus status=remote==null?null:remote.getMediaStatus();
            if(status!=null&&status.getPlayerState()==MediaStatus.PLAYER_STATE_IDLE&&status.getIdleReason()==MediaStatus.IDLE_REASON_ERROR)
                failed.accept("Der Fernseher kann diese Videoquelle nicht abspielen. Prüfe Format, Erreichbarkeit und Website-Anmeldung.");
            changed.run();
        }
        @Override public void onMetadataUpdated(){changed.run();}
    };
    private final SessionManagerListener<CastSession> sessions=new SessionManagerListener<CastSession>(){
        @Override public void onSessionStarting(CastSession s){changed.run();}
        @Override public void onSessionStarted(CastSession s,String id){attach(s);changed.run();}
        @Override public void onSessionStartFailed(CastSession s,int error){attach(null);failed.accept("Verbindung fehlgeschlagen. Prüfe WLAN und Cast-Empfang am Fernseher.");}
        @Override public void onSessionEnding(CastSession s){changed.run();}
        @Override public void onSessionEnded(CastSession s,int error){attach(null);changed.run();}
        @Override public void onSessionResuming(CastSession s,String id){changed.run();}
        @Override public void onSessionResumed(CastSession s,boolean suspended){attach(s);changed.run();}
        @Override public void onSessionResumeFailed(CastSession s,int error){attach(null);failed.accept("Die Verbindung zum Fernseher konnte nicht wiederhergestellt werden.");}
        @Override public void onSessionSuspended(CastSession s,int reason){attach(null);changed.run();}
    };
    GoogleCastTransport(AppCompatActivity activity){this.activity=activity;}
    @Override public void start(Runnable changed,Consumer<String> failed){
        this.changed=changed;this.failed=failed;
        try{
            if(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)!=ConnectionResult.SUCCESS){
                unavailable="Für Chromecast werden aktuelle Google Play-Dienste benötigt. Aktualisiere sie im Play Store und öffne den Browser erneut.";changed.run();return;
            }
            CastContext.getSharedInstance(activity,worker).addOnSuccessListener(value->{
                if(destroyed)return;
                context=value;context.getSessionManager().addSessionManagerListener(sessions,CastSession.class);
                attach(context.getSessionManager().getCurrentCastSession());changed.run();
            }).addOnFailureListener(error->{if(!destroyed){unavailable="Die Cast-Geräteauswahl ist gerade nicht verfügbar. Öffne den Browser erneut und versuche es noch einmal.";changed.run();}});
        }catch(RuntimeException|LinkageError error){unavailable="Cast ist auf diesem Gerät nicht verfügbar. Prüfe die Google Play-Dienste.";changed.run();}
    }
    private void attach(CastSession next){
        commandGeneration++;
        if(remote!=null)remote.unregisterCallback(media);
        session=next;remote=next==null?null:next.getRemoteMediaClient();
        if(remote!=null)remote.registerCallback(media);
    }
    @Override public boolean ready(){return context!=null;}
    @Override public boolean connected(){return session!=null&&session.isConnected()&&remote!=null;}
    @Override public boolean hasMedia(){return connected()&&remote.getMediaStatus()!=null&&remote.getMediaStatus().getPlayerState()!=MediaStatus.PLAYER_STATE_IDLE&&remote.getMediaStatus().getPlayerState()!=MediaStatus.PLAYER_STATE_UNKNOWN;}
    @Override public boolean playing(){return connected()&&remote.isPlaying();}
    @Override public String device(){return session==null||session.getCastDevice()==null?"Fernseher":session.getCastDevice().getFriendlyName();}
    @Override public String title(){MediaInfo info=remote==null?null:remote.getMediaInfo();MediaMetadata metadata=info==null?null:info.getMetadata();String title=metadata==null?null:metadata.getString(MediaMetadata.KEY_TITLE);return title==null?"Video":title;}
    @Override public String unavailable(){return unavailable;}
    @Override public void chooseDevice(){
        if(!ready()||destroyed)return;
        if(activity.getSupportFragmentManager().isStateSaved())return;
        if(activity.getSupportFragmentManager().findFragmentByTag("browser-cast-devices")!=null)return;
        try{
            MediaRouteChooserDialogFragment chooser=new MediaRouteChooserDialogFragment();chooser.setRouteSelector(context.getMergedSelector());
            chooser.show(activity.getSupportFragmentManager(),"browser-cast-devices");
        }catch(RuntimeException error){failed.accept("Die Geräteauswahl konnte nicht geöffnet werden. Versuche es erneut.");}
    }
    @Override public void load(CastVideo video,Consumer<Boolean> completed){
        if(!connected()){completed.accept(false);return;}
        commandGeneration++;
        MediaInfo info=mediaInfo(video);
        try{remote.load(new MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build()).setResultCallback(result->completed.accept(result.getStatus().isSuccess()));}
        catch(RuntimeException error){completed.accept(false);}
    }
    static MediaInfo mediaInfo(CastVideo video){
        MediaMetadata metadata=new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);metadata.putString(MediaMetadata.KEY_TITLE,video.title);
        return new MediaInfo.Builder(video.url).setContentType(video.mime).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build();
    }
    private void command(int action){
        if(!connected())return;
        int request=++commandGeneration;
        try{
            com.google.android.gms.common.api.PendingResult<RemoteMediaClient.MediaChannelResult> result;
            if(action==0)result=remote.play();else if(action==1)result=remote.pause();else result=remote.stop();
            result.setResultCallback(value->{if(!destroyed&&request==commandGeneration&&!value.getStatus().isSuccess())failed.accept("Der Fernseher hat den Wiedergabebefehl nicht ausgeführt. Prüfe die Verbindung.");});
        }catch(RuntimeException error){failed.accept("Der Fernseher ist gerade nicht erreichbar.");}
    }
    @Override public void play(){command(0);}
    @Override public void pause(){command(1);}
    @Override public void stop(){command(2);}
    @Override public void disconnect(){commandGeneration++;if(context!=null)context.getSessionManager().endCurrentSession(true);}
    @Override public void destroy(){destroyed=true;if(context!=null)context.getSessionManager().removeSessionManagerListener(sessions,CastSession.class);attach(null);changed=()->{};failed=reason->{};worker.shutdownNow();}
}
