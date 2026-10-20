package de.steffen.musikmathe;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class PrivateArchiveTest {
    private File root;private byte[] key;private String id;private AtomicFile archive;
    @Before public void setup()throws Exception{root=Files.createTempDirectory(RuntimeEnvironment.getApplication().getCacheDir().toPath(),"archive-test").toFile();key=new byte[96];new java.security.SecureRandom().nextBytes(key);id=PrivateCrypto.id(key);archive=new AtomicFile(new File(root,"encrypted.web"));}
    @Test public void websiteDatabasesAndStorageRoundTripAcrossAuthenticatedSegments()throws Exception{
        File source=new File(root,"source"),db=new File(source,"Default/Cookies"),storage=new File(source,"Default/Local Storage/database");assertTrue(storage.getParentFile().mkdirs());byte[] data=new byte[190000];new java.security.SecureRandom().nextBytes(data);Files.write(db.toPath(),"secret-cookie-for-private.example".getBytes(StandardCharsets.UTF_8));Files.write(storage.toPath(),data);File cache=new File(source,"Default/Cache/test");assertTrue(cache.getParentFile().mkdirs());Files.write(cache.toPath(),new byte[]{9});
        PrivateArchive.save(source,archive,key,id);assertFalse(new String(Files.readAllBytes(archive.getBaseFile().toPath()),StandardCharsets.ISO_8859_1).contains("secret-cookie-for-private.example"));File restored=new File(root,"restored");PrivateArchive.restore(archive,new File(root,"staging"),restored,key,id);
        assertEquals("secret-cookie-for-private.example",new String(Files.readAllBytes(new File(restored,"Default/Cookies").toPath()),StandardCharsets.UTF_8));assertArrayEquals(data,Files.readAllBytes(new File(restored,"Default/Local Storage/database").toPath()));assertFalse(new File(restored,"Default/Cache").exists());
        byte[] before=Files.readAllBytes(archive.getBaseFile().toPath());PrivateArchive.save(new File(root,"already-removed"),archive,key,id);assertArrayEquals(before,Files.readAllBytes(archive.getBaseFile().toPath()));
    }
    @Test public void aTruncatedOrTamperedStreamPublishesNoRestoredFiles()throws Exception{
        File source=new File(root,"source");assertTrue(source.mkdirs());Files.write(new File(source,"Cookies").toPath(),new byte[130000]);PrivateArchive.save(source,archive,key,id);byte[] complete=Files.readAllBytes(archive.getBaseFile().toPath());
        for(byte[] damaged:new byte[][]{Arrays.copyOf(complete,complete.length-10),changed(complete)}){Files.write(archive.getBaseFile().toPath(),damaged);File staging=new File(root,"staging"),out=new File(root,"out");assertThrows(Exception.class,()->PrivateArchive.restore(archive,staging,out,key,id));assertFalse(out.exists());assertFalse(staging.exists());}
    }
    private byte[] changed(byte[] bytes){byte[] copy=bytes.clone();copy[copy.length-1]^=1;return copy;}
    @Test public void archivePathsCannotEscapeThePrivateWorkingDirectory()throws Exception{
        try(OutputStream file=new FileOutputStream(archive.getBaseFile());ZipOutputStream zip=new ZipOutputStream(PrivateCrypto.stream(key).newEncryptingStream(file,PrivateCrypto.aad("web",id)))){zip.putNextEntry(new ZipEntry("../escaped"));zip.write(1);zip.closeEntry();}
        assertThrows(Exception.class,()->PrivateArchive.restore(archive,new File(root,"staging"),new File(root,"out"),key,id));assertFalse(new File(root,"escaped").exists());assertFalse(new File(root,"out").exists());
    }
}
