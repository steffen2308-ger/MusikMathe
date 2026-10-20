package de.steffen.musikmathe;

import android.app.AlertDialog;
import android.content.Context;
import android.view.WindowManager;

/** Private history, settings and download dialogs need their own secure window as well. */
final class BrowserDialogs {
    static AlertDialog.Builder builder(Context context){return new AlertDialog.Builder(context){
        @Override public AlertDialog create(){AlertDialog dialog=super.create();if(context instanceof PrivateActivity||BrowserApplication.privateProcess)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);return dialog;}
    };}
}
