package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit page action. Discovery never requests remote links; confirmed transfers use the queue. */
final class PageMediaDownloads {
    static final int PICK_FOLDER=23;
    private final Activity activity;
    private final Runnable progress;
    private final BooleanSupplier available;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,MediaLinks.Item> found=new LinkedHashMap<>();
    private WebView web;
    private BooleanSupplier samePage;
    private AlertDialog dialog;
    private String page,agent,token;
    private int generation;
    private boolean embedded,limited,closed;
    private Selection pending;
    private static final class Selection {
        final List<MediaLinks.Item> items;
        final String page,agent;
        final PrivateProfiles.Session profile;
        Selection(List<MediaLinks.Item> items,String page,String agent,PrivateProfiles.Session profile){this.items=items;this.page=page;this.agent=agent;this.profile=profile;}
    }
    PageMediaDownloads(Activity activity,BooleanSupplier available,Runnable progress){this.activity=activity;this.available=available;this.progress=progress;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private void toast(String message){if(available.getAsBoolean())Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
    void show(WebView view,String url,BooleanSupplier samePage){
        pause();pending=null;closed=false;this.web=view;page=url;agent=view.getSettings().getUserAgentString();this.samePage=samePage;embedded=false;scan();
    }
    private boolean valid(){return !closed&&available.getAsBoolean()&&web!=null&&samePage!=null&&samePage.getAsBoolean();}
    private void dismiss(){AlertDialog previous=dialog;dialog=null;if(previous!=null)previous.dismiss();}
    private void scan(){
        if(!valid()){pause();return;}generation++;int ticket=generation;found.clear();limited=false;token=UUID.randomUUID().toString();dismiss();
        dialog=BrowserDialogs.builder(activity).setTitle("Medien dieser Seite").setMessage("Direkte Medienlinks werden gesammelt …")
            .setNegativeButton("Abbrechen",(d,w)->pause()).setOnCancelListener(d->pause()).show();
        handler.postDelayed(()->{if(ticket==generation){pause();toast("Die Seite antwortet nicht rechtzeitig. Bitte später erneut versuchen.");}},30000);
        next(ticket);
    }
    private void next(int ticket){
        if(ticket!=generation)return;if(!valid()){pause();return;}
        String script=BrowserScripts.read(activity,"media-links.js","");if(script.isEmpty()){pause();toast("Medien konnten nicht gesucht werden.");return;}
        try{web.evaluateJavascript(script+"("+JSONObject.quote(token)+","+embedded+")",result->{
            if(ticket!=generation)return;if(!valid()){pause();return;}
            try{
                JSONObject batch=new JSONObject(new JSONArray("["+result+"]").getString(0));MediaLinks.collect(found,batch.getJSONArray("items"));
                limited|=batch.optBoolean("limited")||found.size()>=MediaLinks.MAX_ITEMS;
                if(batch.optBoolean("done")||limited){handler.removeCallbacksAndMessages(null);cleanupPage();preview();}
                else{if(dialog!=null)dialog.setMessage("Gefundene Medien: "+found.size()+"\nDie aktuelle Seite wird durchsucht …");handler.postDelayed(()->next(ticket),16);}
            }catch(Exception error){pause();toast("Die Medienliste konnte nicht gelesen werden.");}
        });}catch(RuntimeException error){pause();toast("Diese Seite ist nicht mehr verfügbar.");}
    }
    private void preview(){
        if(!valid()){pause();return;}dismiss();
        List<MediaLinks.Item> items=new ArrayList<>(found.values());
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),0);
        TextView summary=new TextView(activity);summary.setTextColor(activity.getColor(R.color.browser_ink));summary.setTextSize(14);
        summary.setText((items.isEmpty()?"Keine direkt erkennbaren Mediendateien gefunden.":items.size()+" Medien gefunden. Mehrere Verweise auf dieselbe Adresse erscheinen nur einmal.")+"\nErkannt werden Dateinamen und angegebene Medientypen. Unterseiten werden nicht geöffnet."+(embedded?" Einige Player stellen keine direkt speicherbare Quelle bereit.":"")+"\nOrdner: "+DownloadFolder.label(activity)+(limited?"\nDie Suche wurde begrenzt: höchstens 100.000 Seitenelemente und 1.000 Medien pro Aufruf.":""));body.addView(summary);
        CheckBox include=new CheckBox(activity);include.setText("Auch eingebettete Bilder, Audio und Videos");include.setChecked(embedded);include.setTextColor(activity.getColor(R.color.browser_ink));body.addView(include);
        CheckBox all=new CheckBox(activity);all.setText("Alle auswählen");all.setChecked(!items.isEmpty());all.setTextColor(activity.getColor(R.color.browser_ink));body.addView(all);
        ListView list=new ListView(activity);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);String[] labels=new String[items.size()];
        for(int i=0;i<items.size();i++){MediaLinks.Item item=items.get(i);String label=item.url.length()>240?item.url.substring(0,240)+"…":item.url;labels[i]=item.kind()+" · "+item.name+"\n"+label;}
        list.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_multiple_choice,labels){@Override public android.view.View getView(int position,android.view.View recycled,android.view.ViewGroup parent){TextView row=(TextView)super.getView(position,recycled,parent);row.setTextColor(activity.getColor(R.color.browser_ink));row.setTextSize(14);row.setMaxLines(3);row.setEllipsize(android.text.TextUtils.TruncateAt.END);return row;}});
        for(int i=0;i<items.size();i++)list.setItemChecked(i,true);
        int height=Math.min(dp(320),Math.max(dp(100),activity.getResources().getDisplayMetrics().heightPixels/3));body.addView(list,new LinearLayout.LayoutParams(-1,height));
        boolean[] updating={false};
        all.setOnCheckedChangeListener((button,checked)->{if(updating[0])return;for(int i=0;i<items.size();i++)list.setItemChecked(i,checked);if(dialog!=null&&dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked&&!items.isEmpty());});
        list.setOnItemClickListener((parent,row,position,id)->{int count=list.getCheckedItemCount();updating[0]=true;all.setChecked(count==items.size());updating[0]=false;if(dialog!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(count>0);});
        dialog=BrowserDialogs.builder(activity).setTitle("Medien dieser Seite · "+items.size()).setView(body).setNegativeButton("Schließen",(d,w)->pause()).setOnCancelListener(d->pause()).setNeutralButton("Fortschritt",(d,w)->{pause();progress.run();}).setPositiveButton("Auswahl herunterladen",null).create();
        include.setOnCheckedChangeListener((button,checked)->{embedded=checked;cleanupPage();scan();});
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!items.isEmpty());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid()){pause();return;}List<MediaLinks.Item> selected=new ArrayList<>();for(int i=0;i<items.size();i++)if(list.isItemChecked(i))selected.add(items.get(i));
            if(selected.isEmpty()){toast("Bitte mindestens ein Medium auswählen.");return;}
            Selection selection=new Selection(selected,page,agent,PrivateProfiles.forContext(activity));pause();
            if(DownloadFolder.selected(activity)==null){pending=selection;pickFolder();}else enqueue(selection);
        });
    }
    private void pickFolder(){
        Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try{activity.startActivityForResult(choose,PICK_FOLDER);}catch(android.content.ActivityNotFoundException error){pending=null;toast("Keine Ordnerauswahl verfügbar.");}
    }
    void folderResult(int result,Intent data){
        Selection selection=pending;pending=null;if(selection==null||closed||!available.getAsBoolean()||selection.profile!=PrivateProfiles.forContext(activity)||result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        try{DownloadFolder.remember(activity,data.getData(),data.getFlags());enqueue(selection);}catch(Exception error){toast("Der Ordner konnte nicht dauerhaft freigegeben werden.");}
    }
    private void enqueue(Selection selection){
        if(closed||!available.getAsBoolean()||selection.profile!=PrivateProfiles.forContext(activity))return;
        try{VideoDownloadService.enqueueMedia(activity,selection.items,DownloadFolder.selected(activity),selection.page,selection.agent);progress.run();if(android.os.Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},21);}
        catch(RuntimeException error){toast("Die Medien-Downloads konnten nicht gestartet werden.");}
    }
    private void cleanupPage(){if(web==null||token==null)return;try{web.evaluateJavascript("if(window.__bestBrowserMediaLinks&&window.__bestBrowserMediaLinks.token==="+JSONObject.quote(token)+")delete window.__bestBrowserMediaLinks;",null);}catch(RuntimeException ignored){}token=null;}
    void pause(){generation++;handler.removeCallbacksAndMessages(null);cleanupPage();dismiss();found.clear();web=null;samePage=null;}
    void close(){pause();pending=null;closed=true;}
}
