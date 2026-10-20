package de.steffen.musikmathe;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Native controls; transport callbacks and all state transitions run on the UI thread. */
final class BrowserCast {
    interface Transport {
        void start(Runnable changed,Consumer<String> failed);
        boolean ready(); boolean connected(); boolean hasMedia(); boolean playing();
        String device(); String title(); String unavailable();
        void chooseDevice();
        void load(CastVideo video,Consumer<Boolean> completed);
        void play(); void pause(); void stop(); void disconnect(); void destroy();
    }
    private final AppCompatActivity activity;
    private final Transport transport;
    private final Runnable chooseVideo;
    private AlertDialog dialog;
    private TextView status;
    private Button device,video,play,stop,disconnect;
    private CastVideo pending;
    private BooleanSupplier valid;
    private boolean started,destroyed,loading,wasConnected,chooseWhenReady;
    private int generation;
    private String message="";

    BrowserCast(AppCompatActivity activity,Runnable chooseVideo){this(activity,chooseVideo,new GoogleCastTransport(activity));}
    BrowserCast(AppCompatActivity activity,Runnable chooseVideo,Transport transport){this.activity=activity;this.chooseVideo=chooseVideo;this.transport=transport;}
    void show(){
        if(destroyed)return;
        if(dialog!=null&&dialog.isShowing()){render();return;}
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);body.setPadding(pad,0,pad,pad);
        status=new TextView(activity);status.setTextSize(15);status.setTextColor(activity.getColor(R.color.browser_ink));body.addView(status);
        device=button(body,"Gerät auswählen",()->{message="";transport.chooseDevice();});
        video=button(body,"Video auswählen",chooseVideo);
        play=button(body,"Wiedergabe",()->{message="";if(transport.playing())transport.pause();else transport.play();render();});
        stop=button(body,"Wiedergabe stoppen",()->{clearPending();loading=false;message="Wiedergabe gestoppt.";transport.stop();render();});
        disconnect=button(body,"Verbindung trennen",()->{clearPending();loading=false;message="Verbindung getrennt.";transport.disconnect();render();});
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        dialog=new AlertDialog.Builder(activity).setTitle("Auf Fernseher übertragen").setView(scroll).setNegativeButton("Schließen",null).create();
        dialog.setOnDismissListener(d->{dialog=null;status=null;});dialog.show();
        if(!started){started=true;transport.start(this::changed,this::failed);}render();
    }
    private Button button(LinearLayout body,String label,Runnable action){Button button=new Button(activity);button.setText(label);button.setOnClickListener(v->action.run());body.addView(button,new LinearLayout.LayoutParams(-1,-2));return button;}
    void select(CastVideo source,BooleanSupplier stillValid){
        if(destroyed)return;
        clearPending();loading=false;pending=source;valid=stillValid;message="";chooseWhenReady=!transport.connected();show();changed();
    }
    private void clearPending(){pending=null;valid=null;chooseWhenReady=false;generation++;}
    private void changed(){
        if(destroyed)return;
        boolean connected=transport.connected();
        if(wasConnected&&!connected){clearPending();loading=false;message="Die Verbindung zum Fernseher wurde beendet.";}
        wasConnected=connected;
        if(connected&&pending!=null){
            CastVideo source=pending;BooleanSupplier sourceValid=valid;clearPending();
            if(sourceValid==null||!sourceValid.getAsBoolean()){message="Die Seite oder der Tab wurde gewechselt. Bitte wähle das Video erneut aus.";render();return;}
            loading=true;message="Video wird auf dem Fernseher geladen …";int request=generation;
            transport.load(source,success->{
                if(destroyed||request!=generation)return;
                loading=false;message=success?"":"Der Fernseher konnte das Video nicht laden. Die Quelle muss dort ohne Website-Anmeldung erreichbar sein.";render();
            });
        }else if(chooseWhenReady&&transport.ready()&&!connected){chooseWhenReady=false;transport.chooseDevice();}
        render();
    }
    private void failed(String reason){if(destroyed)return;clearPending();loading=false;message=reason;render();}
    private void render(){
        if(dialog==null||status==null)return;
        boolean ready=transport.ready(),connected=transport.connected(),media=connected&&transport.hasMedia();
        String state=connected?"Verbunden mit "+transport.device():ready?"Kein Gerät verbunden":transport.unavailable();
        String title=media?transport.title():"";
        if(!title.isEmpty())state+="\n\n"+title+"\n"+(transport.playing()?"Wiedergabe läuft":"Wiedergabe pausiert oder beendet");
        if(!message.isEmpty())state+="\n\n"+message;
        state+="\n\nHandy und Fernseher müssen im selben WLAN sein. Übertragen werden einzelne Videos, nicht die ganze Browserseite. Der Fernseher lädt die Quelle selbst; geschützte oder anmeldepflichtige Videos können nicht abgespielt werden.";
        status.setText(state);device.setEnabled(ready);video.setEnabled(ready&&!loading);play.setEnabled(media&&!loading);stop.setEnabled(connected&&(media||loading));disconnect.setEnabled(connected);
        play.setText(transport.playing()?"Pause":"Wiedergabe fortsetzen");
    }
    void destroy(){if(destroyed)return;destroyed=true;clearPending();transport.destroy();if(dialog!=null)dialog.dismiss();}
}
