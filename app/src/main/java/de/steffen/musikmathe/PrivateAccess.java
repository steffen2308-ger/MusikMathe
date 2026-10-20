package de.steffen.musikmathe;

import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native authentication surface. No password or key enters an Intent, Bundle, WebView or log. */
@android.annotation.TargetApi(28)
final class PrivateAccess {
    private final MainActivity activity;
    private final Runnable unlocked,cancel;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Runnable> deferred=new ArrayList<>();
    private PrivateProfiles.Session pending;
    private EditText input;
    private TextView status;
    private Button open;
    private android.app.Dialog dialog;
    private int generation;
    private boolean locked=true,busy,destroyed;
    PrivateAccess(MainActivity activity,Runnable unlocked,Runnable cancel){this.activity=activity;this.unlocked=unlocked;this.cancel=cancel;}
    boolean locked(){return locked;}
    void show(){show(false);}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(activity.getColor(R.color.browser_ink));return v;}
    private void show(boolean confirmation){
        if(destroyed)return;locked=true;
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(40),dp(24),dp(24));body.setBackgroundColor(activity.getColor(R.color.browser_surface));body.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        TextView title=text(confirmation?"Neuen privaten Bereich anlegen":"Privater Bereich",25);body.addView(title);
        String explanation=confirmation?"Dieses Passwort gehört noch zu keinem gespeicherten Bereich. Bitte zur Bestätigung nochmals eingeben. Ein Tippfehler öffnet keinen vorhandenen Bereich.":PrivateProfiles.active==null?"Gleiches Passwort: deinen gespeicherten Bereich öffnen. Neues Passwort: einen eigenen Bereich anlegen.\n\nVerlauf, Tabs, Lesezeichen, Website-Anmeldungen, Einstellungen und Videoordner werden getrennt gespeichert.":"Dein privater Bereich ist gesperrt. Gib sein Passwort ein, um fortzufahren.";
        TextView details=text(explanation,16);details.setPadding(0,dp(18),0,dp(18));body.addView(details);
        input=new EditText(activity);input.setSingleLine();input.setHint(confirmation?"Passwort bestätigen":"Passwort");input.setContentDescription(confirmation?"Passwort bestätigen":"Passwort für privaten Bereich");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);input.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);body.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));
        status=text(confirmation?"Mindestens 8 Zeichen. Ohne das Passwort kann dieser Bereich nicht wiederhergestellt werden.":"Mindestens 8 Zeichen. Das Passwort selbst wird nicht gespeichert.",14);status.setPadding(0,dp(12),0,dp(12));body.addView(status);
        open=new Button(activity);open.setAllCaps(false);open.setText(confirmation?"Bereich anlegen":"Öffnen");open.setOnClickListener(v->submit());body.addView(open,new LinearLayout.LayoutParams(-1,dp(52)));
        Button normal=new Button(activity);normal.setAllCaps(false);normal.setText(confirmation?"Anderes Passwort eingeben":"Zu normalen Tabs");normal.setOnClickListener(v->{if(busy)return;if(confirmation){if(pending!=null){pending.close();pending=null;}show();}else cancel.run();});body.addView(normal,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView note=text("Videos in einem gewählten Android-Ordner sind außerhalb dieses Passwortschutzes zugänglich.",13);note.setPadding(0,dp(20),0,0);body.addView(note);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.addView(body);
        if(dialog!=null)dialog.dismiss();dialog=new android.app.Dialog(activity);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);dialog.setCancelable(false);dialog.setContentView(scroll);dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setBackgroundDrawableResource(R.color.browser_surface);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.getWindow().setStatusBarColor(activity.getColor(R.color.browser_surface));activity.getWindow().setNavigationBarColor(activity.getColor(R.color.browser_surface));
        input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_DONE){submit();return true;}return false;});
        if(busy){open.setEnabled(false);input.setEnabled(false);status.setText("Privater Bereich wird vorbereitet …");}
    }
    private void submit(){
        if(busy||destroyed)return;char[] password=new char[input.length()];input.getText().getChars(0,input.length(),password,0);input.getText().clear();
        if(password.length<8||password.length>256){Arrays.fill(password,'\0');status.setText("Bitte ein Passwort mit 8 bis 256 Zeichen eingeben.");return;}
        busy=true;open.setEnabled(false);input.setEnabled(false);status.setText("Privater Bereich wird geöffnet …");int epoch=generation;
        PrivateProfiles.Session candidate=pending;pending=null;
        boolean resuming=candidate==null&&PrivateProfiles.active!=null;
        worker.execute(()->{
            PrivateProfiles.Session result=null;boolean fresh=false;String error=null;
            try{
                if(candidate!=null){if(!candidate.matches(password))throw new IllegalArgumentException("Die Passwörter stimmen nicht überein. Bitte erneut eingeben.");PrivateProfiles.activate(candidate);result=candidate;}
                else if(PrivateProfiles.active!=null){if(!PrivateProfiles.active.matches(password))throw new IllegalArgumentException("Dieses Passwort passt nicht zum geöffneten Bereich. Über „Zu normalen Tabs“ kannst du einen anderen Bereich öffnen.");result=PrivateProfiles.active;}
                else{result=PrivateProfiles.probe(activity,password);fresh=result.fresh;if(!fresh)PrivateProfiles.activate(result);}
            }catch(Exception e){error=e instanceof IllegalArgumentException?e.getMessage():"Der private Bereich konnte nicht geöffnet werden. Bitte freien Speicher und den privaten Speicher prüfen. Vorhandene Daten wurden nicht ersetzt.";if(result!=null&&result!=PrivateProfiles.active)result.close();}
            finally{Arrays.fill(password,'\0');}
            PrivateProfiles.Session selected=result;boolean needsConfirmation=fresh;String message=error;
            activity.runOnUiThread(()->{
                if(destroyed||epoch!=generation){if(selected!=null&&!resuming)selected.close();if(candidate!=null&&candidate!=selected)candidate.close();if(!destroyed){busy=false;show();}return;}
                busy=false;
                if(message!=null){if(candidate!=null)pending=candidate;show(candidate!=null);status.setText(message);return;}
                if(needsConfirmation){pending=selected;show(true);return;}
                try{BrowserApplication.preparePrivateWebView();BrowserAppearance.sync(activity);locked=false;if(dialog!=null)dialog.dismiss();unlocked.run();List<Runnable> actions=new ArrayList<>(deferred);deferred.clear();for(Runnable action:actions)action.run();}
                catch(RuntimeException|LinkageError unavailable){locked=true;show();status.setText("Der getrennte Browser konnte nicht gestartet werden. Bitte die App vollständig schließen und erneut öffnen.");}
            });
        });
    }
    void background(){
        if(destroyed)return;generation++;if(pending!=null){pending.close();pending=null;}show();
    }
    void defer(Runnable action){if(locked)deferred.add(action);else action.run();}
    void destroy(){destroyed=true;generation++;if(pending!=null)pending.close();pending=null;deferred.clear();if(input!=null)input.getText().clear();if(dialog!=null)dialog.dismiss();worker.shutdownNow();}
}
