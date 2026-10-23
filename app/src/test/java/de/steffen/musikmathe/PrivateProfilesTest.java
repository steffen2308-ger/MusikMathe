package de.steffen.musikmathe;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class PrivateProfilesTest {
    private Context context;
    @Before public void setup()throws Exception{context=RuntimeEnvironment.getApplication();if(PrivateProfiles.active!=null)PrivateProfiles.active.close();BrowserApplication.privateProcess=false;PrivateSession.remove(PrivateProfiles.directory(context));context.getSharedPreferences("browser",0).edit().clear().commit();context.getSharedPreferences("appearance",0).edit().clear().commit();}
    @After public void cleanup(){if(PrivateProfiles.active!=null)PrivateProfiles.active.close();BrowserApplication.privateProcess=false;}
    private PrivateProfiles.Session open(String password)throws Exception{PrivateProfiles.Session s=PrivateProfiles.probe(context,password.toCharArray());PrivateProfiles.active=s;BrowserApplication.privateProcess=true;return s;}
    @Test public void fourCharacterPasswordsReopenTheSameEncryptedProfile()throws Exception{
        assertThrows(Exception.class,()->PrivateProfiles.probe(context,"abc".toCharArray()));
        PrivateProfiles.Session first=open("1234");first.put("history","four-character profile");String id=first.id;first.close();
        PrivateProfiles.Session restored=open("1234");assertFalse(restored.fresh);assertEquals(id,restored.id);assertEquals("four-character profile",restored.string("history",""));assertFalse(restored.matches("1235".toCharArray()));restored.close();
    }
    @Test public void privateTabSnapshotIsDurableAndUnchangedSnapshotDoesNotReencrypt()throws Exception{
        PrivateProfiles.Session session=open("Snapshot-password-42");BrowserStore store=new BrowserStore(context,true);
        java.util.List<BrowserStore.Entry> rows=Arrays.asList(new BrowserStore.Entry(Address.HOME,"Start",0),new BrowserStore.Entry("https://private.example/","Private",0));
        store.saveTabs(rows,1);assertTrue(session.flush());File file=PrivateProfiles.dataFile(context,session.id).getBaseFile();byte[] first=Files.readAllBytes(file.toPath());
        store.saveTabs(rows,1);assertArrayEquals(first,Files.readAllBytes(file.toPath()));session.close();
        PrivateProfiles.Session restored=open("Snapshot-password-42");BrowserStore again=new BrowserStore(context,true);assertEquals(1,again.selected());assertEquals("Private",again.read("tabs").get(1).title);restored.close();
    }
    @Test public void samePasswordRestoresItsHistoryTabsBookmarksSettingsAndFolder()throws Exception{
        BrowserStore normal=new BrowserStore(context);normal.visit("https://normal.example/","Normal");
        PrivateProfiles.Session a=open("Profile-A-password-42");assertTrue(a.fresh);BrowserStore first=new BrowserStore(context,true);
        first.visit("https://private-a.example/","Private A title");first.write("tabs",Collections.singletonList(new BrowserStore.Entry("https://private-a.example/","Private A tab",0,true)));first.selected(0);first.toggleBookmark("https://private-a.example/","A bookmark");first.engine("bing");first.adBlockingEnabled(false);first.adBlockException("private-a.example",true);
        a.put("download_folder",new JSONObject().put("uri","content://folders/tree/private-a").put("label","A videos"));BrowserAppearance.saveNight(context,true);assertTrue(a.flush());String id=a.id;a.close();
        PrivateProfiles.Session b=open("Profile-B-password-84");assertNotEquals(id,b.id);BrowserStore second=new BrowserStore(context,true);assertTrue(second.read("history").isEmpty());assertTrue(second.read("tabs").isEmpty());assertFalse(second.bookmarked("https://private-a.example/"));assertEquals("Noch nicht gewählt",DownloadFolder.label(context));assertFalse(BrowserAppearance.savedNight(context));second.visit("https://private-b.example/","Private B title");assertTrue(b.flush());b.close();
        PrivateProfiles.Session restored=open("Profile-A-password-42");assertFalse(restored.fresh);assertEquals(id,restored.id);BrowserStore again=new BrowserStore(context,true);assertEquals("Private A title",again.read("history").get(0).title);assertTrue(again.read("tabs").get(0).desktop);assertTrue(again.bookmarked("https://private-a.example/"));assertEquals("bing",again.engine());assertFalse(again.adBlockingEnabled());assertTrue(again.adBlockExceptions().contains("private-a.example"));assertEquals("A videos",DownloadFolder.label(context));assertTrue(BrowserAppearance.savedNight(context));assertFalse(again.linkPreloadingEnabled());
        assertEquals("https://normal.example/",normal.read("history").get(0).url);assertFalse(context.getSharedPreferences("appearance",0).getBoolean("night",false));
        String raw=new String(Files.readAllBytes(PrivateProfiles.dataFile(context,id).getBaseFile().toPath()),StandardCharsets.ISO_8859_1);assertFalse(raw.contains("private-a.example"));assertFalse(raw.contains("A videos"));assertFalse(raw.contains("Profile-A-password-42"));
    }
    @Test public void aNewPasswordDoesNotCreateFilesUntilConfirmedAndCannotChangeAnotherArea()throws Exception{
        PrivateProfiles.Session a=open("Original-password-42");a.put("history","[]");String id=a.id;byte[] before=Files.readAllBytes(PrivateProfiles.dataFile(context,id).getBaseFile().toPath());a.close();
        PrivateProfiles.Session typo=PrivateProfiles.probe(context,"Original-passwrod-42".toCharArray());assertTrue(typo.fresh);assertFalse(PrivateProfiles.dataFile(context,typo.id).getBaseFile().exists());typo.close();assertArrayEquals(before,Files.readAllBytes(PrivateProfiles.dataFile(context,id).getBaseFile().toPath()));
        PrivateProfiles.Session correct=PrivateProfiles.probe(context,"Original-password-42".toCharArray());assertFalse(correct.fresh);assertTrue(correct.matches("Original-password-42".toCharArray()));assertFalse(correct.matches("Original-passwrod-42".toCharArray()));correct.close();
    }
    @Test public void damagedCiphertextIsRejectedWithoutReplacingTheOriginal()throws Exception{
        PrivateProfiles.Session a=open("Tamper-check-password");a.put("history","[]");AtomicFile file=PrivateProfiles.dataFile(context,a.id);a.close();byte[] broken=Files.readAllBytes(file.getBaseFile().toPath());broken[broken.length-1]^=1;Files.write(file.getBaseFile().toPath(),broken);
        assertThrows(Exception.class,()->PrivateProfiles.probe(context,"Tamper-check-password".toCharArray()));assertArrayEquals(broken,Files.readAllBytes(file.getBaseFile().toPath()));
    }
    @Test public void metadataEncryptionUsesFreshNoncesAndBindsTheAreaId()throws Exception{
        byte[] salt=new byte[32];Arrays.fill(salt,(byte)7);byte[] key=PrivateCrypto.derive("Nonce-check-password".toCharArray(),salt);String id=PrivateCrypto.id(key);byte[] plain="private-address-and-folder".getBytes(StandardCharsets.UTF_8);
        byte[] one=PrivateCrypto.encrypt(PrivateCrypto.dataKey(key),PrivateCrypto.aad("data",id),plain),two=PrivateCrypto.encrypt(PrivateCrypto.dataKey(key),PrivateCrypto.aad("data",id),plain);assertFalse(Arrays.equals(one,two));assertArrayEquals(plain,PrivateCrypto.decrypt(PrivateCrypto.dataKey(key),PrivateCrypto.aad("data",id),one));assertThrows(Exception.class,()->PrivateCrypto.decrypt(PrivateCrypto.dataKey(key),PrivateCrypto.aad("data","other"),one));Arrays.fill(key,(byte)0);
    }
}
