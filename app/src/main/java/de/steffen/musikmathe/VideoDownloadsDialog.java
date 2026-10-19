package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Visible without notification permission; refresh work stops with the activity/dialog. */
final class VideoDownloadsDialog {
    private final AlertDialog dialog;
    private final TextView title,phase,numbers,queue;
    private final ProgressBar bar;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){if(!dialog.isShowing())return;render();handler.postDelayed(this,500);}};
    VideoDownloadsDialog(Activity activity){
        int padding=Math.round(24*activity.getResources().getDisplayMetrics().density);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(padding,padding/2,padding,padding/2);
        title=label(activity,content,17);phase=label(activity,content,15);
        bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setContentDescription("Fortschritt des aktuellen Videoschritts");content.addView(bar,new LinearLayout.LayoutParams(-1,padding));
        numbers=label(activity,content,14);queue=label(activity,content,14);
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        dialog=new AlertDialog.Builder(activity).setTitle("Video-Downloads").setView(scroll).setNegativeButton("Schließen",null)
            .setPositiveButton("Abbrechen",null).setNeutralButton("Alle abbrechen",null).create();
        dialog.setOnDismissListener(ignored->handler.removeCallbacks(refresh));
    }
    private static TextView label(Activity activity,LinearLayout parent,int size){
        TextView text=new TextView(activity);text.setTextSize(size);text.setTextColor(0xff234a43);text.setPadding(0,8,0,8);parent.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;
    }
    void show(){
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setContentDescription("Aktuelles Video abbrechen");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{VideoDownloadService.cancelCurrent();render();});
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{VideoDownloadService.cancelAll();render();});resume();
    }
    void pause(){handler.removeCallbacks(refresh);}
    void resume(){pause();if(dialog.isShowing())refresh.run();}
    void dismiss(){pause();dialog.dismiss();}
    private void render(){
        VideoDownloadService.Snapshot snapshot=VideoDownloadService.snapshot();VideoProgress.Reading reading=snapshot.progress;
        title.setText(snapshot.running?snapshot.title:"Keine laufenden Video-Downloads");
        phase.setText(reading==null?(snapshot.message.isEmpty()?"Hier erscheint der Fortschritt, sobald du ein Video speicherst.":snapshot.message):reading.phase+(reading.percent<0?"":" · "+reading.percent+" %"));
        bar.setVisibility(snapshot.running?View.VISIBLE:View.GONE);bar.setIndeterminate(reading==null||reading.percent<0);if(reading!=null&&reading.percent>=0)bar.setProgress(reading.percent);
        numbers.setVisibility(reading==null?View.GONE:View.VISIBLE);numbers.setText(reading==null?"":reading.detail());
        StringBuilder waiting=new StringBuilder(snapshot.running?"\nNoch offen: "+(1+snapshot.waiting.size())+" Video(s)":"");
        if(!snapshot.waiting.isEmpty()){
            waiting.append("\nIn Warteschlange: ").append(snapshot.waiting.size());for(String name:snapshot.waiting)waiting.append("\n• ").append(name);
            waiting.append("\n\nDie Restzeit gilt für den aktuellen Schritt. Für die gesamte Warteschlange ist noch keine verlässliche Schätzung möglich.");
        }
        queue.setText(waiting.toString());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(snapshot.running);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(snapshot.running||!snapshot.waiting.isEmpty());
    }
}
