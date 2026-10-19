package de.steffen.musikmathe;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Chrome-style quick actions and a scrollable icon list with a fixed settings entry. */
final class BrowserMenu {
    private static final int INK=0xff3c4043, ACCENT=0xff17685f;
    private final Activity activity;
    private final IntConsumer action;
    private final View anchor;
    private final PopupWindow popup=new PopupWindow();
    private final LinearLayout panel, quick;
    private final List<Entry> entries=new ArrayList<>();
    private static final class Entry {
        final int id; final String icon,label,state; final Boolean checked;
        Entry(int id,String icon,String label,String state,Boolean checked){this.id=id;this.icon=icon;this.label=label;this.state=state;this.checked=checked;}
    }
    BrowserMenu(Activity activity,View anchor,IntConsumer action){
        this.activity=activity;this.anchor=anchor;this.action=action;
        panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background=new GradientDrawable();background.setColor(0xffffffff);background.setCornerRadius(dp(18));
        panel.setBackground(background);panel.setClipToOutline(true);
        quick=new LinearLayout(activity);quick.setPadding(dp(8),dp(4),dp(8),dp(4));
        panel.addView(quick,new LinearLayout.LayoutParams(-1,dp(56)));
        View line=new View(activity);line.setBackgroundColor(0xffe8eaed);panel.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        popup.setContentView(panel);popup.setBackgroundDrawable(background);popup.setElevation(dp(12));
        popup.setFocusable(true);popup.setOutsideTouchable(true);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    }
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    BrowserMenu quick(int id,String kind,String label,boolean enabled){
        BrowserIcon icon=new BrowserIcon(activity,kind,label);icon.tint=INK;icon.setEnabled(enabled);
        if(Build.VERSION.SDK_INT>=26)icon.setTooltipText(label);
        quick.addView(icon,new LinearLayout.LayoutParams(0,dp(48),1));icon.setOnClickListener(v->select(id));return this;
    }
    BrowserMenu item(int id,String icon,String label){entries.add(new Entry(id,icon,label,null,null));return this;}
    BrowserMenu status(int id,String icon,String label,String state){entries.add(new Entry(id,icon,label,state,null));return this;}
    BrowserMenu checked(int id,String icon,String label,boolean checked){entries.add(new Entry(id,icon,label,null,checked));return this;}
    BrowserMenu divider(){entries.add(new Entry(-1,null,null,null,null));return this;}
    private View row(Entry entry){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),0,dp(16),0);row.setMinimumHeight(dp(48));
        BrowserIcon icon=new BrowserIcon(activity,entry.icon,entry.label);icon.tint=INK;icon.count=0;
        icon.setClickable(false);icon.setFocusable(false);icon.setBackground(null);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(48)));
        TextView label=new TextView(activity);label.setText(entry.label);label.setTextSize(16);label.setTextColor(INK);label.setPadding(dp(6),dp(12),dp(8),dp(12));
        row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        if(entry.state!=null){
            TextView state=new TextView(activity);state.setText(entry.state);state.setTextSize(13);state.setTextColor(ACCENT);
            row.addView(state,new LinearLayout.LayoutParams(-2,-2));row.setContentDescription(entry.label+", "+entry.state);
        }
        if(entry.checked!=null){
            CheckBox check=new CheckBox(activity);check.setChecked(entry.checked);check.setClickable(false);check.setFocusable(false);check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));row.addView(check,new LinearLayout.LayoutParams(dp(32),dp(48)));
            row.setContentDescription(entry.label);row.setAccessibilityDelegate(new View.AccessibilityDelegate(){
                @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(CheckBox.class.getName());info.setCheckable(true);info.setChecked(entry.checked);}
            });
        }
        return row;
    }
    void show(){
        ListView list=new ListView(activity);list.setDivider(null);list.setCacheColorHint(0);list.setVerticalScrollBarEnabled(true);
        list.setAdapter(new BaseAdapter(){
            @Override public int getCount(){return entries.size();}
            @Override public Entry getItem(int position){return entries.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public boolean areAllItemsEnabled(){return false;}
            @Override public boolean isEnabled(int position){return entries.get(position).id>=0;}
            @Override public int getViewTypeCount(){return 2;}
            @Override public int getItemViewType(int position){return isEnabled(position)?0:1;}
            @Override public View getView(int position,View convertView,ViewGroup parent){
                Entry entry=entries.get(position);if(entry.id>=0)return row(entry);
                LinearLayout gap=new LinearLayout(activity);gap.setGravity(Gravity.CENTER_VERTICAL);gap.setPadding(dp(16),0,dp(16),0);
                gap.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(13)));gap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                View line=new View(activity);line.setBackgroundColor(0xffe8eaed);gap.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));return gap;
            }
        });
        list.setOnItemClickListener((parent,view,position,id)->{Entry entry=entries.get(position);if(entry.id>=0)select(entry.id);});
        panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        View line=new View(activity);line.setBackgroundColor(0xffe8eaed);panel.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        View settings=row(new Entry(17,"settings","Einstellungen",null,null));settings.setClickable(true);settings.setFocusable(true);
        android.util.TypedValue feedback=new android.util.TypedValue();activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,feedback,true);settings.setBackgroundResource(feedback.resourceId);
        settings.setOnClickListener(v->select(17));panel.addView(settings,new LinearLayout.LayoutParams(-1,-2));
        Rect bounds=new Rect();anchor.getWindowVisibleDisplayFrame(bounds);int[] location=new int[2];anchor.getLocationOnScreen(location);
        int width=Math.min(dp(336),Math.max(1,bounds.width()-dp(16)));
        // Measure at the actual width, including wrapped labels and the system font size.
        int desired=dp(58);for(Entry entry:entries){if(entry.id<0)desired+=dp(13);else{View row=row(entry);row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));desired+=row.getMeasuredHeight();}}
        settings.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));desired+=settings.getMeasuredHeight();
        int height=Math.min(desired,Math.max(1,location[1]-bounds.top-dp(8)));
        popup.setWidth(width);popup.setHeight(height);
        popup.showAsDropDown(anchor,anchor.getWidth()-width,-height-anchor.getHeight(),Gravity.LEFT);
        list.setSelection(0);
    }
    private void select(int id){popup.dismiss();action.accept(id);}
    boolean dismiss(){if(!popup.isShowing())return false;popup.dismiss();return true;}
}
