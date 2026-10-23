package de.steffen.musikmathe;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Small, interactive browser controls on either side of the physical camera. */
final class FullscreenToolbar extends ViewGroup {
    final FrameLayout addressSlot;
    final LinearLayout navigation;
    private DisplayCutout cutout;
    private int addressLeft,addressRight,navigationLeft,navigationRight,controlsTop;

    FullscreenToolbar(Context context){
        super(context);setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        addressSlot=new FrameLayout(context);navigation=new LinearLayout(context);
        navigation.setGravity(android.view.Gravity.CENTER_VERTICAL);
        addView(addressSlot);addView(navigation);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void cutout(DisplayCutout value){
        if(java.util.Objects.equals(cutout,value))return;
        cutout=value;requestLayout();
    }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        if(Build.VERSION.SDK_INT>=28)cutout(insets.getDisplayCutout());
        return insets;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),row=dp(28),left=dp(3),right=width-dp(3),safeTop=0;
        Rect camera=null;
        if(Build.VERSION.SDK_INT>=28&&cutout!=null){
            left+=cutout.getSafeInsetLeft();right-=cutout.getSafeInsetRight();safeTop=cutout.getSafeInsetTop();
            for(Rect bounds:cutout.getBoundingRects()){
                if(bounds.top<safeTop&&bounds.bottom>0&&bounds.left<width/2&&bounds.right>width/2){camera=bounds;break;}
            }
        }
        int before=camera==null?(left+right)/2-dp(4):camera.left-dp(4);
        int after=camera==null?(left+right)/2+dp(4):camera.right+dp(4);
        // A very wide notch or an off-centre camera may leave too little room.
        // In that case keep both groups usable in a single row below the notch.
        boolean below=safeTop>0&&(camera==null||before-left<dp(64)||right-after<dp(110));
        int height=below?safeTop+row:Math.max(row,safeTop);
        if(below){before=(left+right)/2-dp(4);after=(left+right)/2+dp(4);}
        addressLeft=left;addressRight=Math.max(left,before);
        navigationLeft=Math.min(right,after);navigationRight=right;
        controlsTop=below?safeTop:(height-row)/2;
        addressSlot.measure(MeasureSpec.makeMeasureSpec(addressRight-addressLeft,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(row,MeasureSpec.EXACTLY));
        navigation.measure(MeasureSpec.makeMeasureSpec(navigationRight-navigationLeft,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(row,MeasureSpec.EXACTLY));
        setMeasuredDimension(width,height);
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        addressSlot.layout(addressLeft,controlsTop,addressRight,controlsTop+addressSlot.getMeasuredHeight());
        navigation.layout(navigationLeft,controlsTop,navigationRight,controlsTop+navigation.getMeasuredHeight());
    }
}
