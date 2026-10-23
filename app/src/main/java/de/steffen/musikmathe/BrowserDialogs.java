package de.steffen.musikmathe;

import android.app.AlertDialog;
import android.content.Context;
import android.view.WindowManager;

/** Private history, settings and download dialogs need their own secure window as well. */
final class BrowserDialogs {
    private static final java.util.List<java.lang.ref.WeakReference<AlertDialog>> PRIVATE=new java.util.ArrayList<>();
    static AlertDialog.Builder builder(Context context){return new AlertDialog.Builder(context){
        @Override public AlertDialog create(){AlertDialog dialog=super.create();if(context instanceof PrivateActivity||BrowserApplication.privateProcess){dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);if(context instanceof android.app.Activity)dialog.setOwnerActivity((android.app.Activity)context);PRIVATE.add(new java.lang.ref.WeakReference<>(dialog));}return dialog;}
    };}
    static void hidePrivate(android.app.Activity owner){
        for(java.lang.ref.WeakReference<AlertDialog> reference:new java.util.ArrayList<>(PRIVATE)){AlertDialog dialog=reference.get();if(dialog!=null&&dialog.getOwnerActivity()==owner)dialog.dismiss();}
        PRIVATE.removeIf(reference->{AlertDialog dialog=reference.get();return dialog==null||dialog.getOwnerActivity()==owner;});
    }
}
