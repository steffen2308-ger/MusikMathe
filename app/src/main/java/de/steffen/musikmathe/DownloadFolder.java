package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** A user-selected SAF directory, shared by both browser processes; no browsing data. */
final class DownloadFolder {
    private static AtomicFile file(Context context){return new AtomicFile(new File(context.getFilesDir(),"video-download-folder.json"));}
    private static JSONObject read(Context context){
        try{return new JSONObject(new String(file(context).readFully(),StandardCharsets.UTF_8));}catch(Exception ignored){return new JSONObject();}
    }
    static Uri selected(Context context){
        String value=read(context).optString("uri");if(value.isEmpty())return null;
        try{Uri uri=Uri.parse(value);return "content".equals(uri.getScheme())&&DocumentsContract.isTreeUri(uri)?uri:null;}catch(Exception ignored){return null;}
    }
    static String label(Context context){return read(context).optString("label","Noch nicht gewählt");}
    static void remember(Context context,Uri tree,int flags)throws Exception{
        if(tree==null||!"content".equals(tree.getScheme())||!DocumentsContract.isTreeUri(tree)||(flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0||(flags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0)throw new IOException("Der Ordner erlaubt keinen dauerhaften Schreibzugriff.");
        int access=flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        context.getContentResolver().takePersistableUriPermission(tree,access);
        String name=DocumentsContract.getTreeDocumentId(tree);
        Uri document=DocumentsContract.buildDocumentUriUsingTree(tree,name);
        try(Cursor cursor=query(context,document)){
            if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))name=cursor.getString(0);
        }catch(Exception ignored){}
        byte[] data=new JSONObject().put("uri",tree.toString()).put("label",name).toString().getBytes(StandardCharsets.UTF_8);
        AtomicFile stored=file(context);FileOutputStream output=null;
        try{output=stored.startWrite();output.write(data);stored.finishWrite(output);}catch(Exception error){if(output!=null)stored.failWrite(output);throw error;}
        // Old grants stay valid for downloads already queued in the previous directory.
    }
    static Uri create(Context context,Uri tree,String mime,String requested)throws Exception{
        if(tree==null)throw new IOException("Bitte zuerst einen Downloadordner wählen.");
        String name=safeName(requested);Set<String> existing=new HashSet<>();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try(Cursor cursor=query(context,children)){
            if(cursor==null)throw new IOException("Downloadordner ist nicht erreichbar.");while(cursor.moveToNext())existing.add(cursor.getString(0));
        }
        String unique=name;int dot=name.lastIndexOf('.');String stem=dot>0?name.substring(0,dot):name,extension=dot>0?name.substring(dot):"";
        for(int number=2;existing.contains(unique);number++)unique=stem+" ("+number+")"+extension;
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        Uri result=DocumentsContract.createDocument(context.getContentResolver(),parent,mime,unique);
        if(result==null)throw new IOException("Datei kann im Downloadordner nicht angelegt werden.");return result;
    }
    private static Cursor query(Context context,Uri uri){
        String[] columns={DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        if(Build.VERSION.SDK_INT>=26)return context.getContentResolver().query(uri,columns,(Bundle)null,null);
        return context.getContentResolver().query(uri,columns,null,null,null);
    }
    static String safeName(String value){
        String name=value==null?"":value.replaceAll("[\\\\/\\p{Cntrl}:*?\"<>|]","_").trim();
        if(name.isEmpty()||name.equals(".")||name.equals(".."))return "Video.mp4";
        if(name.length()>160){int dot=name.lastIndexOf('.');String extension=dot>0&&name.length()-dot<=16?name.substring(dot):"";name=name.substring(0,160-extension.length())+extension;}return name;
    }
    static void delete(Context context,Uri uri){
        if(uri==null)return;try{DocumentsContract.deleteDocument(context.getContentResolver(),uri);}catch(Exception ignored){}
    }
}
