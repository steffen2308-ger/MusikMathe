package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent progress cards; controls keep their identity during periodic refreshes. */
final class VideoDownloadsDialog {
    private final AlertDialog dialog;
    private final Activity activity;
    private final TextView summary,message,queue,queueHint;
    private final LinearLayout activeRows,waitingRows;
    private final Map<String,ProgressRow> rows=new LinkedHashMap<>();
    private List<String> renderedWaiting=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){if(!dialog.isShowing())return;render();handler.postDelayed(this,500);}};
    VideoDownloadsDialog(Activity activity){
        this.activity=activity;LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(8));
        summary=label(content,16);message=label(content,14);activeRows=new LinearLayout(activity);activeRows.setOrientation(LinearLayout.VERTICAL);content.addView(activeRows);
        queue=label(content,14);waitingRows=new LinearLayout(activity);waitingRows.setOrientation(LinearLayout.VERTICAL);content.addView(waitingRows);
        queueHint=label(content,13);queueHint.setText("Bis zu 3 Downloads gleichzeitig. Weitere Downloads starten automatisch, sobald ein Platz frei wird. Die Restzeit gilt für den jeweiligen Schritt.");
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        dialog=BrowserDialogs.builder(activity).setTitle("Medien-Downloads").setView(scroll).setNegativeButton("Schließen",null).setNeutralButton("Alle abbrechen",null).create();
        dialog.setOnDismissListener(ignored->handler.removeCallbacks(refresh));
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout parent,int size){TextView text=new TextView(activity);text.setTextSize(size);text.setTextColor(activity.getColor(R.color.browser_ink));text.setPadding(0,dp(4),0,dp(4));parent.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;}
    private Button cancelButton(String description){Button button=new Button(activity,null,android.R.attr.borderlessButtonStyle);button.setText("Abbrechen");button.setTextSize(13);button.setAllCaps(false);button.setTextColor(activity.getColor(R.color.browser_accent));button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));button.setContentDescription(description);return button;}
    void show(){dialog.show();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{VideoDownloadService.cancelAll();render();});resume();}
    void pause(){handler.removeCallbacks(refresh);}
    void resume(){pause();if(dialog.isShowing())refresh.run();}
    void dismiss(){pause();dialog.dismiss();}
    private static void put(TextView view,String value){if(!android.text.TextUtils.equals(view.getText(),value))view.setText(value);}
    private void render(){
        VideoDownloadService.Snapshot snapshot=VideoDownloadService.snapshot();boolean busy=snapshot.running||!snapshot.waiting.isEmpty();
        put(summary,snapshot.running?"Gleichzeitig aktiv: "+snapshot.active.size()+" von "+VideoDownloadService.MAX_PARALLEL:"Keine laufenden Medien-Downloads");
        message.setVisibility(busy?View.GONE:View.VISIBLE);put(message,snapshot.message.isEmpty()?"Hier erscheint der Fortschritt, sobald du Medien speicherst.":snapshot.message);
        List<String> ids=new ArrayList<>();for(VideoDownloadService.ActiveVideo video:snapshot.active)ids.add(video.id);
        if(!ids.equals(new ArrayList<>(rows.keySet()))){rows.keySet().retainAll(ids);activeRows.removeAllViews();Map<String,ProgressRow> ordered=new LinkedHashMap<>();for(VideoDownloadService.ActiveVideo video:snapshot.active){ProgressRow row=rows.get(video.id);if(row==null)row=new ProgressRow(video);ordered.put(video.id,row);activeRows.addView(row.body);}rows.clear();rows.putAll(ordered);}
        for(VideoDownloadService.ActiveVideo video:snapshot.active)rows.get(video.id).render(video);
        queue.setVisibility(snapshot.waiting.isEmpty()?View.GONE:View.VISIBLE);put(queue,"In Warteschlange: "+snapshot.waiting.size());renderWaiting(snapshot.waiting);queueHint.setVisibility(busy?View.VISIBLE:View.GONE);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!snapshot.waiting.isEmpty()||snapshot.active.stream().anyMatch(video->!video.cancelling));
    }
    private final class ProgressRow {
        final LinearLayout body=new LinearLayout(activity);
        final TextView phase,numbers;
        final ProgressBar bar;
        final Button cancel;
        ProgressRow(VideoDownloadService.ActiveVideo video){
            body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(6),0,dp(10));LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);body.addView(heading);
            TextView name=new TextView(activity);name.setText(video.title);name.setTextSize(16);name.setTextColor(activity.getColor(R.color.browser_ink));name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            cancel=cancelButton("Download abbrechen: "+video.title);cancel.setOnClickListener(v->{if(!VideoDownloadService.cancelActive(video.id))Toast.makeText(activity,"Dieser Download ist bereits beendet.",Toast.LENGTH_SHORT).show();VideoDownloadsDialog.this.render();});heading.addView(cancel,new LinearLayout.LayoutParams(-2,-2));
            phase=label(body,13);bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setContentDescription("Download-Fortschritt: "+video.title);body.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));numbers=label(body,13);
        }
        void render(VideoDownloadService.ActiveVideo video){VideoProgress.Reading progress=video.progress;cancel.setEnabled(!video.cancelling);put(phase,video.cancelling?"Wird abgebrochen …":progress==null?"Download wird vorbereitet …":progress.phase+(progress.percent<0?"":" · "+progress.percent+" %"));bar.setIndeterminate(progress==null||progress.percent<0);if(progress!=null&&progress.percent>=0)bar.setProgress(progress.percent);put(numbers,progress==null?"":progress.detail());}
    }
    private void renderWaiting(List<VideoDownloadService.QueuedVideo> waiting){
        List<String> ids=new ArrayList<>();for(VideoDownloadService.QueuedVideo video:waiting)ids.add(video.id);if(ids.equals(renderedWaiting))return;renderedWaiting=ids;waitingRows.removeAllViews();
        for(VideoDownloadService.QueuedVideo video:waiting){LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);TextView name=new TextView(activity);name.setText(video.title);name.setTextSize(14);name.setTextColor(activity.getColor(R.color.browser_ink));name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button cancel=cancelButton("Download aus Warteschlange entfernen: "+video.title);cancel.setOnClickListener(v->{if(!VideoDownloadService.cancelWaiting(video.id))Toast.makeText(activity,"Dieser Download wartet nicht mehr.",Toast.LENGTH_SHORT).show();render();});row.addView(cancel,new LinearLayout.LayoutParams(-2,-2));waitingRows.addView(row);}
    }
}
