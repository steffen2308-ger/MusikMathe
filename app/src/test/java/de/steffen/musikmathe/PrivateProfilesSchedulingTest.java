package de.steffen.musikmathe;

import android.content.Context;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class PrivateProfilesSchedulingTest {
    private PrivateProfiles.Session session;
    private Context context;
    @Before public void setup()throws Exception{
        context=RuntimeEnvironment.getApplication();byte[] key=new byte[96];new java.security.SecureRandom().nextBytes(key);
        session=new PrivateProfiles.Session(context,PrivateCrypto.id(key),key,new JSONObject().put("schema",1),true);
    }
    @After public void cleanup()throws Exception{session.close();PrivateProfiles.dataFile(context,session.id).delete();}
    private JSONObject disk()throws Exception{return new JSONObject(new String(PrivateCrypto.decrypt(PrivateCrypto.dataKey(session.material),PrivateCrypto.aad("data",session.id),PrivateProfiles.dataFile(context,session.id).readFully()),StandardCharsets.UTF_8));}
    @Test public void aBlockedDiskCannotBlockChangesOrReads()throws Exception{
        Field field=PrivateProfiles.Session.class.getDeclaredField("diskLock");field.setAccessible(true);Object lock=field.get(session);
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
        Thread disk=new Thread(()->{synchronized(lock){held.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}});disk.start();assertTrue(held.await(2,TimeUnit.SECONDS));
        FutureTask<String> ui=new FutureTask<>(()->{for(int i=0;i<100;i++)session.putAsync("selected",i);return session.integer("selected",-1)+":"+session.string("missing","available");});new Thread(ui).start();
        try{assertEquals("99:available",ui.get(2,TimeUnit.SECONDS));}finally{release.countDown();disk.join(2000);}
        assertTrue(session.flush());assertEquals(99,disk().getInt("selected"));
    }
    @Test public void finalBarrierPersistsTheNewestRevisionAndUnchangedDataKeepsItsCiphertext()throws Exception{
        for(int i=0;i<100;i++)session.putAllAsync(new JSONObject().put("selected",i).put("tabs","tabs-"+i));
        assertTrue(session.flush());assertEquals(99,disk().getInt("selected"));assertEquals("tabs-99",disk().getString("tabs"));
        byte[] before=PrivateProfiles.dataFile(context,session.id).readFully();session.putAsync("selected",99);assertTrue(session.flush());assertArrayEquals(before,PrivateProfiles.dataFile(context,session.id).readFully());
        session.putAsync("history","latest history");byte[] key=session.material.clone();session.close();
        JSONObject finalData=new JSONObject(new String(PrivateCrypto.decrypt(PrivateCrypto.dataKey(key),PrivateCrypto.aad("data",session.id),PrivateProfiles.dataFile(context,session.id).readFully()),StandardCharsets.UTF_8));assertEquals("latest history",finalData.getString("history"));java.util.Arrays.fill(key,(byte)0);
        assertThrows(IllegalStateException.class,()->session.string("history",""));
        assertThrows(IllegalStateException.class,()->session.putAsync("selected",100));
    }
    @Test public void failedWritesRemainInMemoryAndCanBeRetried()throws Exception{
        File file=PrivateProfiles.directory(context);PrivateSession.remove(file);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(1);}
        session.putAsync("history","retained after error");assertFalse(session.flush());assertFalse(session.failure.isEmpty());assertEquals("retained after error",session.string("history",""));
        assertTrue(file.delete());assertTrue(session.flush());assertEquals("",session.failure);assertEquals("retained after error",disk().getString("history"));
    }
    @Test public void closingAnUnconfirmedUntouchedPasswordDoesNotCreateStorage()throws Exception{
        session.close();assertFalse(PrivateProfiles.dataFile(context,session.id).getBaseFile().exists());
    }
}
