package de.steffen.musikmathe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Native, page-scoped buttons. Web content exposes no Android bridge or download action. */
final class VideoMarkers {
    private final Context context;
    private final FrameLayout host, overlay;
    private final BooleanSupplier available;
    private final BiConsumer<WebView,JSONObject> selected;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String,Dot> dots=new HashMap<>();
    private final Map<WebView,Object> pending=new java.util.WeakHashMap<>();
    private final String script;
    private WebView web;
    private boolean running,destroyed,dragging;
    private String requestedCapture="";
    private int generation;
    private final Runnable poll=()->sample("");

    VideoMarkers(Context context,FrameLayout host,BooleanSupplier available,BiConsumer<WebView,JSONObject> selected){
        this.context=context;this.host=host;this.available=available;this.selected=selected;
        overlay=new FrameLayout(context);overlay.setClipChildren(true);overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        String source="function(){return JSON.stringify({rows:[]});}";
        try(java.io.InputStream in=context.getAssets().open("video-markers.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);source=new String(out.toByteArray(),StandardCharsets.UTF_8);
        }catch(java.io.IOException ignored){}
        script=source;
    }
    private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    void attach(WebView view){
        generation++;requestedCapture="";dragging=false;clear();web=view;
        if(overlay.getParent() instanceof ViewGroup)((ViewGroup)overlay.getParent()).removeView(overlay);
        host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));refreshAfter(220);
    }
    void navigation(WebView view){if(web==view)refresh();}
    void changed(WebView view){if(web==view)refresh();}
    void touch(WebView view,MotionEvent event){
        if(web!=view)return;int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){dragging=true;handler.removeCallbacks(poll);clear();}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){dragging=false;refresh();}
    }
    void refresh(){refreshAfter(160);}
    private void refreshAfter(long delay){generation++;clear();handler.removeCallbacks(poll);if(running&&!destroyed)handler.postDelayed(poll,delay);}
    void resume(){if(destroyed)return;running=true;refreshAfter(220);}
    void pause(){running=false;generation++;requestedCapture="";handler.removeCallbacks(poll);clear();}
    void destroy(){pause();destroyed=true;pending.clear();web=null;if(overlay.getParent() instanceof ViewGroup)((ViewGroup)overlay.getParent()).removeView(overlay);}
    private boolean ready(){return running&&!destroyed&&!dragging&&available.getAsBoolean()&&web!=null&&web.isShown()&&web.getWidth()>0&&web.getHeight()>0&&Address.isWeb(web.getUrl());}
    private void clear(){if(dots.isEmpty())return;dots.clear();overlay.removeAllViews();}
    private void later(long delay){handler.removeCallbacks(poll);if(running&&!destroyed)handler.postDelayed(poll,delay);}
    private boolean finish(WebView source,Object request,int token){
        if(pending.get(source)!=request)return false;
        pending.remove(source);
        if(destroyed)return false;
        if(token!=generation||source!=web){if(source==web)later(160);return false;}
        return true;
    }
    private void sample(String capture){
        if(!capture.isEmpty())requestedCapture=capture;
        if(!ready()){clear();later(1000);return;}if(pending.containsKey(web))return;
        WebView source=web;int token=generation;Object request=new Object();pending.put(source,request);
        String captureId=requestedCapture;requestedCapture="";
        String call="typeof window.__mmVideoMarkerScan==='function'?window.__mmVideoMarkerScan("+((double)dp(44)/source.getWidth())+","+((double)dp(44)/source.getHeight())+","+JSONObject.quote(captureId)+"):null";
        source.evaluateJavascript(call,value->{
            if(token!=generation||source!=web||destroyed){finish(source,request,token);return;}
            if(!ready()){finish(source,request,token);clear();later(1000);return;}
            if("null".equals(value)){
                source.evaluateJavascript("(function(){window.__mmVideoMarkerScan="+script+";return true;})()",installed->{
                    if(!finish(source,request,token))return;
                    if("true".equals(installed))sample(captureId);else{clear();later(1000);}
                });return;
            }
            if(!finish(source,request,token))return;
            try{
                if(value==null||value.length()>700000)throw new IllegalArgumentException();
                JSONObject report=new JSONObject(String.valueOf(new JSONTokener(value).nextValue()));
                if(!java.util.Objects.equals(source.getUrl(),report.optString("href")))throw new IllegalArgumentException();
                JSONArray rows=report.optJSONArray("rows");double width=report.optDouble("width"),height=report.optDouble("height");
                if(rows==null||!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0)throw new IllegalArgumentException();
                if(!captureId.isEmpty()){
                    for(int i=0;i<Math.min(rows.length(),24);i++){JSONObject row=rows.getJSONObject(i);if(captureId.equals(row.optString("id"))){selected.accept(source,row);break;}}
                }else render(source,rows,width,height);
                later(rows.length()==0?1000:750);
            }catch(Exception ignored){clear();later(1000);}
        });
    }
    private void render(WebView source,JSONArray rows,double width,double height)throws Exception{
        Set<String> keep=new HashSet<>();int size=dp(44);
        for(int i=0;i<Math.min(rows.length(),24);i++){
            JSONObject row=rows.getJSONObject(i);String id=row.optString("id");double x=row.optDouble("x"),y=row.optDouble("y");
            if(id.isEmpty()||id.length()>32||!Double.isFinite(x)||!Double.isFinite(y)||x<0||y<0||x>width||y>height)continue;
            int left=source.getLeft()+(int)Math.round(x*source.getWidth()/width)-size/2,top=source.getTop()+(int)Math.round(y*source.getHeight()/height)-size/2;
            if(left<0||top<0||left+size>overlay.getWidth()||top+size>overlay.getHeight())continue;
            Dot dot=dots.get(id);if(dot==null){dot=new Dot(context);dots.put(id,dot);overlay.addView(dot);final String key=id;dot.setOnClickListener(v->{generation++;handler.removeCallbacks(poll);sample(key);});}
            String title=row.optString("title");if(title.length()>160)title=title.substring(0,160);
            dot.setContentDescription("Video herunterladen"+(title.isEmpty()?"":": "+title));
            ViewGroup.LayoutParams old=dot.getLayoutParams();FrameLayout.LayoutParams previous=old instanceof FrameLayout.LayoutParams?(FrameLayout.LayoutParams)old:null;
            if(previous==null||previous.width!=size||previous.height!=size||previous.leftMargin!=left||previous.topMargin!=top){FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(size,size);params.leftMargin=left;params.topMargin=top;dot.setLayoutParams(params);}
            keep.add(id);
        }
        for(String id:new HashSet<>(dots.keySet()))if(!keep.contains(id)){overlay.removeView(dots.remove(id));}
    }
    private static final class Dot extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Dot(Context context){super(context);setClickable(true);setFocusable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.34f;
            paint.setStyle(Paint.Style.STROKE);paint.setColor(0x99000000);paint.setStrokeWidth(r*.35f);canvas.drawCircle(cx,cy,r,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(isPressed()?0xff087d40:0xff0aab54);canvas.drawCircle(cx,cy,r,paint);
            paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(r*.15f);canvas.drawCircle(cx,cy,r,paint);
            paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(cx,cy-r*.5f,cx,cy+r*.2f,paint);canvas.drawLine(cx-r*.28f,cy-r*.06f,cx,cy+r*.24f,paint);canvas.drawLine(cx+r*.28f,cy-r*.06f,cx,cy+r*.24f,paint);canvas.drawLine(cx-r*.35f,cy+r*.5f,cx+r*.35f,cy+r*.5f,paint);
        }
    }
}
