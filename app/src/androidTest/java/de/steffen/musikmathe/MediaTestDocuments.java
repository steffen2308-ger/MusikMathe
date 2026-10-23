package de.steffen.musikmathe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileNotFoundException;

/** Test APK only: SAF document operations without granting production storage permissions. */
public final class MediaTestDocuments extends ContentProvider {
    private File root(){File root=new File(getContext().getCacheDir(),"media-batch-documents");root.mkdirs();return root;}
    private File target(String name)throws FileNotFoundException{if(name==null||!name.matches("[a-zA-Z0-9._ ()-]{1,180}"))throw new FileNotFoundException();return new File(root(),name);}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "root".equals(uri.getLastPathSegment())?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        String[] columns=projection==null?new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_DOCUMENT_ID}:projection;MatrixCursor cursor=new MatrixCursor(columns);
        File[] entries="children".equals(uri.getLastPathSegment())?root().listFiles():new File[]{root()};
        if(entries!=null)for(File file:entries){MatrixCursor.RowBuilder row=cursor.newRow();for(String column:columns)row.add(column,DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column)?file.getName():DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column)?file.getName():null);}
        return cursor;
    }
    @Override public Bundle call(String method,String arg,Bundle extras){
        try{
            Uri uri=extras.getParcelable("uri");
            if("android:createDocument".equals(method)){String name=extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME);if(!target(name).createNewFile())throw new IllegalArgumentException("Already exists");Bundle result=new Bundle();result.putParcelable("uri",DocumentsContract.buildDocumentUriUsingTree(uri,name));return result;}
            if("android:deleteDocument".equals(method)){target(uri.getLastPathSegment()).delete();return new Bundle();}
        }catch(java.io.IOException error){throw new IllegalArgumentException(error);}
        throw new UnsupportedOperationException(method);
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(target(uri.getLastPathSegment()),ParcelFileDescriptor.parseMode(mode));}
    @Override public int delete(Uri uri,String selection,String[] args){try{return target(uri.getLastPathSegment()).delete()?1:0;}catch(FileNotFoundException ignored){return 0;}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
}
