package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.webkit.PermissionRequest;
import android.webkit.WebView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** One explicit protected-media decision for verified Vimeo frames in one active document. */
final class VimeoMediaConsent {
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<PermissionRequest> pending=new ArrayList<>();
    private WebView owner;
    private String page;
    private BooleanSupplier current;
    private AlertDialog dialog;
    private int generation,decision; // 0 undecided, 1 allowed, -1 denied; never persisted.
    private boolean checking;
    VimeoMediaConsent(Activity activity){this.activity=activity;}
    static boolean eligible(String page,PermissionRequest request){
        if(request==null||request.getOrigin()==null||!VimeoVideos.showcase(page))return false;
        android.net.Uri origin=request.getOrigin();String[] resources=request.getResources();
        return "https".equalsIgnoreCase(origin.getScheme())&&"player.vimeo.com".equalsIgnoreCase(origin.getHost())&&origin.getUserInfo()==null&&(origin.getPort()==-1||origin.getPort()==443)&&resources!=null&&resources.length==1&&PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resources[0]);
    }
    void request(WebView web,String url,PermissionRequest request,BooleanSupplier valid){
        if(!eligible(url,request)||!valid.getAsBoolean()){request.deny();return;}
        if(owner!=web||!url.equals(page)||current==null||!current.getAsBoolean()){reset();owner=web;page=url;current=valid;}
        if(decision!=0){if(decision>0&&current.getAsBoolean())request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});else request.deny();return;}
        if(pending.size()>=64){request.deny();return;}pending.add(request);if(checking||dialog!=null)return;checking=true;int ticket=generation;
        handler.postDelayed(()->{if(ticket==generation&&checking)reset();},10000);
        String script="(function(){if(location.href!=="+org.json.JSONObject.quote(page)+")return false;var frames=document.querySelectorAll('iframe');for(var i=0;i<Math.min(frames.length,256);i++)try{var u=new URL(frames[i].src,location.href);if(u.protocol==='https:'&&u.hostname==='player.vimeo.com'&&!u.username&&!u.password&&(!u.port||u.port==='443')&&/^\\/video\\/[0-9]{1,15}\\/?$/.test(u.pathname))return true;}catch(e){}return false;})()";
        try{web.evaluateJavascript(script,value->{if(ticket!=generation)return;checking=false;handler.removeCallbacksAndMessages(null);if(!"true".equals(value)||!current.getAsBoolean()){reset();return;}show();});}catch(RuntimeException unavailable){reset();}
    }
    private void show(){
        dialog=BrowserDialogs.builder(activity).setTitle("Geschützte Inhalte von Vimeo")
            .setMessage("player.vimeo.com ist in dieser Vimeo-Sammlung eingebettet und möchte geschützte Videos wiedergeben. Dafür darf der Player eine Gerätekennung zur Lizenzprüfung verwenden.\n\nDie Entscheidung gilt nur für die Player in dieser geöffneten Seite. Kamera und Mikrofon werden nicht freigegeben.")
            .setPositiveButton("Erlauben",(d,w)->settle(true)).setNegativeButton("Ablehnen",(d,w)->settle(false)).setOnCancelListener(d->settle(false)).show();
    }
    private void settle(boolean allow){
        boolean grant=allow&&current!=null&&current.getAsBoolean();decision=grant?1:-1;AlertDialog old=dialog;dialog=null;if(old!=null)old.dismiss();
        List<PermissionRequest> requests=new ArrayList<>(pending);pending.clear();for(PermissionRequest request:requests)try{if(grant)request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});else request.deny();}catch(RuntimeException expired){}
    }
    void cancelled(PermissionRequest request){pending.remove(request);if(pending.isEmpty()&&decision==0)reset();}
    void navigation(WebView view){if(owner==view)reset();}
    void reset(){
        generation++;handler.removeCallbacksAndMessages(null);checking=false;decision=0;owner=null;page=null;current=null;AlertDialog old=dialog;dialog=null;if(old!=null)old.dismiss();
        List<PermissionRequest> requests=new ArrayList<>(pending);pending.clear();for(PermissionRequest request:requests)try{request.deny();}catch(RuntimeException expired){}
    }
}
