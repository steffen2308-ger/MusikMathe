package de.steffen.musikmathe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Only installed with the test APK: real cross-process content output, no storage permission. */
public final class ProgressTestProvider extends ContentProvider {
    private File target(Uri uri)throws FileNotFoundException{
        String name=uri.getLastPathSegment();if(name==null||!name.matches("[a-zA-Z0-9_-]+\\.mp4"))throw new FileNotFoundException();return new File(getContext().getCacheDir(),"progress-"+name);
    }
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(target(uri),ParcelFileDescriptor.parseMode(mode));}
    @Override public int delete(Uri uri,String selection,String[] args){try{return target(uri).delete()?1:0;}catch(FileNotFoundException e){return 0;}}
    @Override public String getType(Uri uri){return "video/mp4";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
