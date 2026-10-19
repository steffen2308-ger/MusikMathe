package de.steffen.musikmathe;

import android.annotation.SuppressLint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Consumer;

/** Page controls receive their touches; a confirmed blank-space double tap replaces double-tap zoom. */
final class FreeSpaceGesture {
    private final WebView web;
    private final BooleanSupplier active;
    private final Runnable toggle;
    private final IntSupplier page;
    private final String check;
    private final GestureDetector detector;
    private final int slop;
    private boolean secondTap, moved, multiple, firstTap, firstFree, interceptSecond;
    private String lastCheck="none";
    private float firstX, firstY, secondX, secondY;
    private int firstWidth, firstHeight, firstScrollX, firstScrollY, firstPage, generation;
    private String firstUrl;

    private FreeSpaceGesture(WebView web,BooleanSupplier active,IntSupplier page,Runnable toggle,String check) {
        this.web=web;this.active=active;this.page=page;this.toggle=toggle;this.check=check;
        slop=ViewConfiguration.get(web.getContext()).getScaledTouchSlop();
        detector=new GestureDetector(web.getContext(),new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event){return true;}
            @Override public boolean onDoubleTap(MotionEvent event){
                secondTap=true;moved=false;
                interceptSecond=firstTap&&firstFree&&samePage()&&active.getAsBoolean();
                lastCheck="double tap, cached free="+firstFree+", intercept="+interceptSecond;
                if(interceptSecond){
                    MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
                    try{web.onTouchEvent(cancel);}finally{cancel.recycle();}
                }
                return true;
            }
            @Override public boolean onDoubleTapEvent(MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){secondX=event.getX();secondY=event.getY();}
                if(Math.abs(event.getX()-secondX)>slop||Math.abs(event.getY()-secondY)>slop)moved=true;
                if(event.getActionMasked()==MotionEvent.ACTION_UP)lastCheck+="; up: firstTap="+firstTap+", moved="+moved+", multiple="+multiple+", samePage="+samePage();
                if(event.getActionMasked()==MotionEvent.ACTION_UP&&firstTap&&event.getEventTime()-event.getDownTime()<ViewConfiguration.getLongPressTimeout()&&!moved&&!multiple&&samePage()&&active.getAsBoolean()){
                    final int ticket=generation;final String url=web.getUrl();
                    String points="[["+firstX+","+firstY+"],["+(event.getX()/Math.max(1,web.getWidth()))+","+(event.getY()/Math.max(1,web.getHeight()))+"]]";
                    web.evaluateJavascript(check+"("+points+")",value->{
                        lastCheck+="; DOM free="+value+", current="+(ticket==generation)+", samePage="+samePage();
                        if(active.getAsBoolean()&&"true".equals(value)&&ticket==generation&&Objects.equals(url,web.getUrl())&&samePage())toggle.run();
                    });
                }
                return true;
            }
        });
        detector.setIsLongpressEnabled(false);
    }
    private boolean samePage(){
        return firstPage==page.getAsInt()&&Objects.equals(firstUrl,web.getUrl())&&firstWidth==web.getWidth()&&firstHeight==web.getHeight()&&firstScrollX==web.getScrollX()&&firstScrollY==web.getScrollY();
    }
    static String diagnostics(WebView web){Object value=web.getTag();return value instanceof FreeSpaceGesture?((FreeSpaceGesture)value).lastCheck:"no gesture listener";}
    @SuppressLint("ClickableViewAccessibility") // Blank containers have a separate accessible menu action.
    static void attachBlankView(View view,Runnable toggle){
        GestureDetector detector=new GestureDetector(view.getContext(),new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event){return true;}
            @Override public boolean onDoubleTap(MotionEvent event){toggle.run();return true;}
        });
        detector.setIsLongpressEnabled(false);
        view.setOnTouchListener((target,event)->detector.onTouchEvent(event));
    }
    @SuppressLint("ClickableViewAccessibility") // Controls keep their native clicks; fullscreen also has an accessible menu action.
    static void attach(WebView web,BooleanSupplier active,IntSupplier page,Runnable toggle,Consumer<MotionEvent> touchObserver){
        String script;
        try(InputStream input=web.getContext().getAssets().open("free-space.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int length;
            while((length=input.read(buffer))!=-1)out.write(buffer,0,length);
            script=new String(out.toByteArray(),StandardCharsets.UTF_8);
        }catch(IOException failure){return;}
        FreeSpaceGesture gesture=new FreeSpaceGesture(web,active,page,toggle,script);
        web.setTag(gesture);
        web.setOnTouchListener((view,event)->{
            touchObserver.accept(event);
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){gesture.generation++;gesture.secondTap=false;gesture.multiple=false;gesture.interceptSecond=false;}
            if(action==MotionEvent.ACTION_POINTER_DOWN){
                gesture.multiple=true;gesture.firstFree=false;
                if(gesture.interceptSecond){
                    // Hand a new multi-touch gesture back to WebView so pinch zoom still works.
                    MotionEvent down=MotionEvent.obtain(event.getDownTime(),event.getEventTime(),MotionEvent.ACTION_DOWN,event.getX(),event.getY(),0);down.setSource(event.getSource());
                    try{web.onTouchEvent(down);}finally{down.recycle();}
                    gesture.interceptSecond=false;
                }
            }
            gesture.detector.onTouchEvent(event);
            if(action==MotionEvent.ACTION_UP&&!gesture.secondTap){
                gesture.firstTap=!gesture.multiple&&event.getEventTime()-event.getDownTime()<ViewConfiguration.getLongPressTimeout();
                gesture.firstFree=false;gesture.firstX=event.getX()/Math.max(1,web.getWidth());gesture.firstY=event.getY()/Math.max(1,web.getHeight());
                gesture.firstWidth=web.getWidth();gesture.firstHeight=web.getHeight();gesture.firstScrollX=web.getScrollX();gesture.firstScrollY=web.getScrollY();gesture.firstUrl=web.getUrl();gesture.firstPage=page.getAsInt();
                int ticket=gesture.generation;
                if(!gesture.multiple)web.evaluateJavascript(scriptFor(gesture),value->{if(ticket==gesture.generation&&gesture.samePage())gesture.firstFree="true".equals(value);});
            }
            if(action==MotionEvent.ACTION_CANCEL){gesture.generation++;gesture.firstFree=false;gesture.interceptSecond=false;}
            return gesture.interceptSecond;
        });
    }
    private static String scriptFor(FreeSpaceGesture gesture){return gesture.check+"([["+gesture.firstX+","+gesture.firstY+"]])";}
}
