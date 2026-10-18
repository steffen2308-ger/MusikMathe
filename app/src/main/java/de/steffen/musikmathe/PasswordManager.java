package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

/** Native consent and exact-origin checks surround every save/fill; never installed privately. */
final class PasswordManager {
    private final Activity activity;
    private final PasswordVault vault;
    private final java.util.function.Supplier<WebView> active;
    private final WeakHashMap<WebView,State> states=new WeakHashMap<>();
    private AlertDialog dialog;
    private boolean destroyed,resumed;
    private static final class State {String token=UUID.randomUUID().toString();long touch=-10000,prompt=-100000;boolean bridge;}
    PasswordManager(Activity activity,java.util.function.Supplier<WebView> active){this.activity=activity;this.active=active;vault=new PasswordVault(activity,false);}
    private boolean automatic(){return activity.getSharedPreferences("password-options",0).getBoolean("ask",true);}
    private void notice(String text){if(!destroyed)Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private void problem(){notice("Der Passwortspeicher konnte nicht geöffnet oder geändert werden. Bestehende Einträge wurden nicht überschrieben.");}
    private void show(AlertDialog next){if(destroyed)return;if(dialog!=null)dialog.dismiss();dialog=next;next.setOnDismissListener(d->{if(dialog==next)dialog=null;});next.show();if(next.getWindow()!=null)next.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    void attach(WebView web){
        State state=new State();states.put(web,state);
        web.setOnTouchListener((view,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)state.touch=SystemClock.elapsedRealtime();return false;});
        try{
            if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))return;
            WebViewCompat.addWebMessageListener(web,"musikPasswords",Collections.singleton("*"),(view,message,source,main,reply)->{
                if(!main||message.getType()!=WebMessageCompat.TYPE_STRING||view!=active.get()||destroyed||!resumed)return;
                String origin=PasswordVault.origin(source.toString());
                if(origin.isEmpty()||!origin.equals(PasswordVault.origin(view.getUrl())))return;
                String value=message.getData();if(value==null||value.length()>15000)return;
                try{JSONObject data=new JSONObject(value);if(!state.token.equals(data.optString("token")))return;
                    if("save".equals(data.optString("action"))&&automatic())offerSave(origin,data,false,state);
                    else if("focus".equals(data.optString("action"))&&SystemClock.elapsedRealtime()-state.touch<4000&&dialog==null)chooseFill(view,false);
                }catch(Exception ignored){}
            });state.bridge=true;
        }catch(RuntimeException|LinkageError ignored){ /* Explicit menu actions work on older providers. */ }
    }
    void pageStarted(WebView web){State s=states.get(web);if(s!=null){s.token=UUID.randomUUID().toString();s.touch=-10000;s.prompt=-100000;}}
    void pageFinished(WebView web){
        State state=states.get(web);if(state==null||PasswordVault.origin(web.getUrl()).isEmpty())return;
        try(java.io.InputStream in=activity.getAssets().open("passwords.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            web.evaluateJavascript(new String(out.toByteArray(),StandardCharsets.UTF_8).replace("__MM_TOKEN__",quote(state.token)),null);
        }catch(Exception ignored){}
    }
    private static String quote(String value){return JSONObject.quote(value).replace("\u2028","\\u2028").replace("\u2029","\\u2029");}
    private void offerSave(String origin,JSONObject data,boolean manual,State state){
        String username=data.optString("username"),password=data.optString("password");
        if(password.isEmpty()||password.length()>4096||username.length()>320){if(manual)notice("Bitte zuerst Benutzername und Passwort auf der Seite eingeben.");return;}
        if(!manual&&(dialog!=null||SystemClock.elapsedRealtime()-state.prompt<30000))return;
        try{
            boolean update=false;for(PasswordVault.Login row:vault.forSite(origin))if(row.username.equals(username)){
                if(row.password.equals(password)){if(manual)notice("Diese Zugangsdaten sind bereits gespeichert.");return;}update=true;
            }
            state.prompt=SystemClock.elapsedRealtime();
            show(new AlertDialog.Builder(activity).setTitle(update?"Passwort aktualisieren?":"Passwort merken?").setMessage(origin+"\n\n"+(username.isEmpty()?"Ohne Benutzername":username)+"\nPasswort: ••••••••").setNegativeButton("Nicht jetzt",null).setPositiveButton(update?"Aktualisieren":"Speichern",(d,w)->{
                try{vault.save(origin,username,password);notice("Zugangsdaten verschlüsselt gespeichert.");}catch(Exception e){problem();}
            }).create());
        }catch(Exception e){problem();}
    }
    private void capture(WebView web){
        if(web==null)return;String origin=PasswordVault.origin(web.getUrl());State state=states.get(web);
        if(origin.isEmpty()||state==null){notice("Passwörter werden nur für HTTPS-Websites gespeichert.");return;}
        String token=state.token;pageFinished(web);
        web.evaluateJavascript("JSON.stringify(window.__mmPasswords?window.__mmPasswords.read():null)",value->{
            if(destroyed||!resumed||active.get()!=web||!token.equals(state.token)||!origin.equals(PasswordVault.origin(web.getUrl())))return;
            try{Object decoded=new JSONTokener(value).nextValue();JSONObject data=new JSONObject(String.valueOf(decoded));if(!token.equals(data.optString("token")))return;offerSave(origin,data,true,state);}
            catch(Exception e){notice("Kein einfaches Login-Formular gefunden. Unterstützt werden sichtbare Formulare mit einem Passwortfeld.");}
        });
    }
    private void chooseFill(WebView web,boolean manual){
        if(web==null)return;String origin=PasswordVault.origin(web.getUrl());State state=states.get(web);
        if(origin.isEmpty()||state==null){if(manual)notice("Passwörter werden nur auf der passenden HTTPS-Website ausgefüllt.");return;}
        try{
            List<PasswordVault.Login> rows=vault.forSite(origin);if(rows.isEmpty()){if(manual)notice("Für diese Website sind noch keine Zugangsdaten gespeichert.");return;}
            String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=rows.get(i).username.isEmpty()?"Ohne Benutzername":rows.get(i).username;
            String token=state.token;pageFinished(web);
            show(new AlertDialog.Builder(activity).setTitle("Ausfüllen für\n"+origin).setItems(labels,(d,i)->fill(web,origin,token,rows.get(i))).setNegativeButton("Abbrechen",null).create());
        }catch(Exception e){problem();}
    }
    private void fill(WebView web,String origin,String token,PasswordVault.Login row){
        State state=states.get(web);
        if(destroyed||!resumed||web!=active.get()||state==null||!token.equals(state.token)||!row.origin.equals(origin)||!origin.equals(PasswordVault.origin(web.getUrl())))return;
        String script="(function(o,t,u,p){if(window.top!==window||location.origin!==o||!window.__mmPasswords||window.__mmPasswords.token!==t)return false;function visible(e){return !e.disabled&&!e.readOnly&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden'&&getComputedStyle(e).display!=='none';}var a=Array.from(document.querySelectorAll('input[type=password]')).filter(visible);if(a.length!==1||a[0].autocomplete==='new-password')return false;var pass=a[0],root=pass.form||document,users=Array.from(root.querySelectorAll('input')).filter(function(e){return visible(e)&&['text','email','tel'].indexOf(e.type)>=0;}),user=users.find(function(e){return e.autocomplete==='username';})||users.find(function(e){return /user|email|login|benutzer/i.test(e.name+' '+e.id);})||users[users.length-1];function set(e,v){if(!e)return;Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}set(user,u);set(pass,p);return true;})("+quote(origin)+","+quote(token)+","+quote(row.username)+","+quote(row.password)+")";
        web.evaluateJavascript(script,value->{if("true".equals(value))notice("Zugangsdaten ausgefüllt.");else notice("Kein passendes sichtbares Login-Formular gefunden.");});
    }
    void menu(){
        show(new AlertDialog.Builder(activity).setTitle("Passwörter").setItems(new String[]{"Zugangsdaten ausfüllen","Anmeldung dieser Seite speichern","Gespeicherte Logins verwalten","Alle Passwörter löschen","Automatisch nach Speichern fragen: "+(automatic()?"an":"aus")},(d,i)->{
            if(i==0)chooseFill(active.get(),true);else if(i==1)capture(active.get());else if(i==2)manage();
            else if(i==3)show(new AlertDialog.Builder(activity).setTitle("Alle Passwörter löschen?").setMessage("Alle gespeicherten Logins werden von diesem Gerät entfernt.").setNegativeButton("Abbrechen",null).setPositiveButton("Alle löschen",(a,w)->{try{vault.clear();notice("Alle Passwörter gelöscht.");}catch(Exception e){problem();}}).create());
            else{activity.getSharedPreferences("password-options",0).edit().putBoolean("ask",!automatic()).apply();notice(automatic()?"Speichern wird angeboten.":"Automatische Speichernachfrage ausgeschaltet.");}
        }).setNegativeButton("Schließen",null).create());
    }
    private void manage(){
        try{
            List<PasswordVault.Login> rows=vault.all();if(rows.isEmpty()){notice("Noch keine Zugangsdaten gespeichert.");return;}
            rows.sort((a,b)->(a.origin+"\n"+a.username).compareTo(b.origin+"\n"+b.username));String[] labels=new String[rows.size()];for(int i=0;i<labels.length;i++)labels[i]=rows.get(i).origin+"\n"+(rows.get(i).username.isEmpty()?"Ohne Benutzername":rows.get(i).username);
            show(new AlertDialog.Builder(activity).setTitle("Gespeicherte Logins").setItems(labels,(d,i)->{
                PasswordVault.Login row=rows.get(i);show(new AlertDialog.Builder(activity).setTitle("Gespeicherter Login").setMessage(row.origin+"\n\n"+(row.username.isEmpty()?"Ohne Benutzername":row.username)+"\nPasswort: ••••••••").setNegativeButton("Schließen",null).setPositiveButton("Login löschen",(a,w)->{try{vault.remove(row.id);notice("Login gelöscht.");}catch(Exception e){problem();}}).create());
            }).setNegativeButton("Schließen",null).create());
        }catch(Exception e){problem();}
    }
    void resume(){resumed=true;}
    void pause(){resumed=false;tabChanged();}
    void tabChanged(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    void destroy(){destroyed=true;tabChanged();states.clear();}
}
