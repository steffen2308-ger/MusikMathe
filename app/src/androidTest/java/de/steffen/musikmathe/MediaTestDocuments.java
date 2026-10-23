package de.steffen.musikmathe;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;

/** Test APK only: a real DocumentsProvider for exercising lazy SAF output creation. */
public class MediaTestDocuments extends DocumentsProvider {
    private File directory(){File folder=new File(getContext().getFilesDir(),"media-batch-test");folder.mkdirs();return folder;}
    private File file(String id)throws FileNotFoundException{if(id.isEmpty()||id.contains("/")||id.equals(".")||id.equals(".."))throw new FileNotFoundException("Invalid ID");return new File(directory(),id);}
    private static final String[] COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS};
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID});}
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{MatrixCursor rows=new MatrixCursor(COLUMNS);rows.addRow(new Object[]{id,id,id.equals("root")?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream",DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_SUPPORTS_DELETE|DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE});return rows;}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort){MatrixCursor rows=new MatrixCursor(COLUMNS);File[] files=directory().listFiles();if(files!=null)for(File file:files)rows.addRow(new Object[]{file.getName(),file.getName(),"application/octet-stream",DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_SUPPORTS_DELETE});return rows;}
    @Override public boolean isChildDocument(String parent,String child){return parent.equals("root")&&!child.equals("root")&&!child.contains("/");}
    @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{if(!parent.equals("root"))throw new FileNotFoundException("Not a folder");try{if(!file(name).createNewFile())throw new FileNotFoundException("Exists");return name;}catch(java.io.IOException error){throw new FileNotFoundException(error.getMessage());}}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));}
    @Override public void deleteDocument(String id)throws FileNotFoundException{file(id).delete();}
}
