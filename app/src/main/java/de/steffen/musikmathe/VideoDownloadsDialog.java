package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Visible without notification permission; refresh work stops with the activity/dialog. */
final class VideoDownloadsDialog {
    private final AlertDialog dialog;
    private final Activity activity;
    private final TextView title,phase,numbers,queue,queueHint;
    private final LinearLayout waitingRows;
    private List<String> renderedIds=new ArrayList<>();
    private final ProgressBar bar;
    private final Button cancelCurrent;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){if(!dialog.isShowing())return;render();handler.postDelayed(this,500);}};
    VideoDownloadsDialog(Activity activity){
        this.activity=activity;
        int padding=Math.round(24*activity.getResources().getDisplayMetrics().density);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(padding,padding/2,padding,padding/2);
        title=label(activity,content,17);phase=label(activity,content,15);
        bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setContentDescription("Fortschritt des aktuellen Videoschritts");content.addView(bar,new LinearLayout.LayoutParams(-1,padding));
        numbers=label(activity,content,14);
        cancelCurrent=new Button(activity,null,android.R.attr.borderlessButtonStyle);cancelCurrent.setText("Aktuellen abbrechen");cancelCurrent.setContentDescription("Aktuelles Video abbrechen");cancelCurrent.setTextColor(activity.getColor(R.color.browser_accent));
        cancelCurrent.setOnClickListener(v->{VideoDownloadService.cancelCurrent();render();});content.addView(cancelCurrent,new LinearLayout.LayoutParams(-1,-2));
        queue=label(activity,content,14);
        waitingRows=new LinearLayout(activity);waitingRows.setOrientation(LinearLayout.VERTICAL);content.addView(waitingRows,new LinearLayout.LayoutParams(-1,-2));
        queueHint=label(activity,content,14);queueHint.setText("Die Restzeit gilt für den aktuellen Schritt. Für die gesamte Warteschlange ist noch keine verlässliche Schätzung möglich.");
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        dialog=BrowserDialogs.builder(activity).setTitle("Video-Downloads").setView(scroll).setNegativeButton("Schließen",null)
            .setNeutralButton("Alle abbrechen",null).create();
        dialog.setOnDismissListener(ignored->handler.removeCallbacks(refresh));
    }
    private static TextView label(Activity activity,LinearLayout parent,int size){
        TextView text=new TextView(activity);text.setTextSize(size);text.setTextColor(activity.getColor(R.color.browser_ink));text.setPadding(0,8,0,8);parent.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;
    }
    void show(){
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{VideoDownloadService.cancelAll();render();});resume();
    }
    void pause(){handler.removeCallbacks(refresh);}
    void resume(){pause();if(dialog.isShowing())refresh.run();}
    void dismiss(){pause();dialog.dismiss();}
    private static void put(TextView view,String value){
        if(!android.text.TextUtils.equals(view.getText(),value))view.setText(value);
    }
    private void render(){
        VideoDownloadService.Snapshot snapshot=VideoDownloadService.snapshot();VideoProgress.Reading reading=snapshot.progress;
        put(title,snapshot.running?snapshot.title:"Keine laufenden Video-Downloads");
        put(phase,reading==null?(snapshot.message.isEmpty()?"Hier erscheint der Fortschritt, sobald du ein Video speicherst.":snapshot.message):reading.phase+(reading.percent<0?"":" · "+reading.percent+" %"));
        bar.setVisibility(snapshot.running?View.VISIBLE:View.GONE);bar.setIndeterminate(reading==null||reading.percent<0);if(reading!=null&&reading.percent>=0)bar.setProgress(reading.percent);
        numbers.setVisibility(reading==null?View.GONE:View.VISIBLE);put(numbers,reading==null?"":reading.detail());
        put(queue,snapshot.running?"\nNoch offen: "+(1+snapshot.waiting.size())+" Video(s)"+(snapshot.waiting.isEmpty()?"":"\nIn Warteschlange: "+snapshot.waiting.size()):"");
        renderWaiting(snapshot.waiting);queueHint.setVisibility(snapshot.waiting.isEmpty()?View.GONE:View.VISIBLE);
        cancelCurrent.setVisibility(snapshot.running?View.VISIBLE:View.GONE);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(snapshot.running||!snapshot.waiting.isEmpty());
    }
    private void renderWaiting(List<VideoDownloadService.QueuedVideo> waiting){
        List<String> ids=new ArrayList<>();for(VideoDownloadService.QueuedVideo video:waiting)ids.add(video.id);
        // Keep the buttons and accessibility focus stable during progress-only refreshes.
        if(ids.equals(renderedIds))return;renderedIds=ids;waitingRows.removeAllViews();
        int touch=Math.round(48*activity.getResources().getDisplayMetrics().density);
        for(VideoDownloadService.QueuedVideo video:waiting){
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setOrientation(LinearLayout.HORIZONTAL);
            TextView name=new TextView(activity);name.setText(video.title);name.setTextSize(14);name.setTextColor(activity.getColor(R.color.browser_ink));name.setMaxLines(3);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Button cancel=new Button(activity,null,android.R.attr.borderlessButtonStyle);cancel.setText("Abbrechen");cancel.setTextSize(14);cancel.setAllCaps(false);cancel.setTextColor(activity.getColor(R.color.browser_accent));cancel.setMinHeight(touch);cancel.setMinimumHeight(touch);
            cancel.setContentDescription("Video aus Warteschlange entfernen: "+video.title);
            cancel.setOnClickListener(v->{if(!VideoDownloadService.cancelWaiting(video.id))Toast.makeText(activity,"Dieses Video wartet nicht mehr.",Toast.LENGTH_SHORT).show();render();});
            row.addView(cancel,new LinearLayout.LayoutParams(-2,-2));waitingRows.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }
}
