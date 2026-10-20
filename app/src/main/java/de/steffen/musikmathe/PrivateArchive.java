package de.steffen.musikmathe;

import android.util.AtomicFile;
import java.io.*;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Called only after the private WebView process has stopped; never copy a live SQLite profile. */
@android.annotation.TargetApi(28)
final class PrivateArchive {
    private static final long LIMIT=1024L*1024*1024;
    private static final int FILE_LIMIT=30000;
    static void save(File source,AtomicFile target,byte[] key,String id)throws Exception{
        if(!source.exists())return; // A crash after cleanup must not overwrite the previous archive.
        FileOutputStream output=null;
        try{
            output=target.startWrite();
            // Closing the authenticated stream must not close AtomicFile before finishWrite/fsync.
            OutputStream retained=new FilterOutputStream(output){@Override public void close()throws IOException{flush();}};
            try(ZipOutputStream zip=new ZipOutputStream(PrivateCrypto.stream(key).newEncryptingStream(retained,PrivateCrypto.aad("web",id)))){write(source,source,zip,new long[]{0,0});}
            target.finishWrite(output);
        }catch(Exception error){if(output!=null)target.failWrite(output);throw error;}
    }
    private static void write(File root,File current,ZipOutputStream zip,long[] size)throws Exception{
        if(Files.isSymbolicLink(current.toPath()))return;
        String name=current.getName();
        if(name.equals("Cache")||name.equals("Code Cache")||name.equals("GPUCache")||name.equals("Crashpad")||name.equals("BrowserMetrics")||name.equals("webview_data.lock")||name.startsWith("Singleton"))return;
        if(current.isDirectory()){File[] children=current.listFiles();if(children==null)throw new IOException("Website-Daten sind nicht lesbar.");for(File child:children)write(root,child,zip,size);return;}
        if(!current.isFile())return;
        if(++size[1]>FILE_LIMIT)throw new IOException("Zu viele private Website-Dateien.");
        String relative=root.toPath().relativize(current.toPath()).toString().replace(File.separatorChar,'/');zip.putNextEntry(new ZipEntry(relative));
        try(InputStream input=new FileInputStream(current)){byte[] b=new byte[16384];int n;while((n=input.read(b))!=-1){size[0]+=n;if(size[0]>LIMIT)throw new IOException("Private Website-Daten überschreiten 1 GiB.");zip.write(b,0,n);}}zip.closeEntry();
    }
    static void restore(AtomicFile source,File staging,File destination,byte[] key,String id)throws Exception{
        if(!source.getBaseFile().exists()&&!new File(source.getBaseFile()+".bak").exists())return;
        if(!staging.mkdirs())throw new IOException("Privater Arbeitsbereich konnte nicht vorbereitet werden.");
        try{
            try(InputStream encrypted=source.openRead();InputStream plain=PrivateCrypto.stream(key).newDecryptingStream(encrypted,PrivateCrypto.aad("web",id));ZipInputStream zip=new ZipInputStream(plain)){
                long total=0;int count=0;Set<String> seen=new HashSet<>();ZipEntry entry;byte[] buffer=new byte[16384];
                while((entry=zip.getNextEntry())!=null){
                    String name=entry.getName();File out=new File(staging,name);
                    if(++count>FILE_LIMIT||name.isEmpty()||name.startsWith("/")||name.contains("\\")||!out.getCanonicalPath().startsWith(staging.getCanonicalPath()+File.separator)||!seen.add(name))throw new IOException("Ungültiger privater Website-Speicher.");
                    if(entry.isDirectory()){if(!out.mkdirs()&&!out.isDirectory())throw new IOException("Ordner konnte nicht angelegt werden.");}
                    else{File parent=out.getParentFile();if(!parent.mkdirs()&&!parent.isDirectory())throw new IOException("Ordner konnte nicht angelegt werden.");try(OutputStream file=new FileOutputStream(out)){int n;while((n=zip.read(buffer))!=-1){total+=n;if(total>LIMIT)throw new IOException("Private Website-Daten überschreiten 1 GiB.");file.write(buffer,0,n);}}}zip.closeEntry();
                }
                // Read through the final authenticated segment before publishing any restored files.
                while(plain.read(buffer)!=-1){}
            }
            if(destination.exists())throw new IOException("Privater Arbeitsbereich ist noch belegt.");
            if(!staging.renameTo(destination))throw new IOException("Website-Daten konnten nicht wiederhergestellt werden.");
        }finally{if(staging.exists())PrivateSession.remove(staging);}
    }
}
