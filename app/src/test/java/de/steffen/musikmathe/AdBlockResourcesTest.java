package de.steffen.musikmathe;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
public class AdBlockResourcesTest {
    @Test public void bundledListIsLargeAndTemplatesAreFullyExpanded(){
        Context context=RuntimeEnvironment.getApplication();AdBlockResources.ensureLoaded(context);
        assertTrue(AdBlocker.hostCount()>60000);
        assertTrue(AdBlocker.shouldBlock("https://telemetry.001-studio.com/pixel",false,true));
        String script=AdBlockResources.cosmeticScript(context,Collections.singleton("allowed.example"));
        assertTrue(script.contains("allowed.example"));assertTrue(script.contains("selectors"));
        assertFalse(script.contains("'__FILTER_DATA__'"));assertFalse(script.contains("'__SITE_EXCEPTIONS__'"));
        assertFalse(script.contains("'__CONFIG_KEY__'"));
        java.util.regex.Matcher marker=java.util.regex.Pattern.compile("var signature = \\\"(cosmetic-v2:[0-9]+)\\\"").matcher(script);
        assertTrue("Cache marker is a process-local sequence, without exception domains",marker.find());
        assertEquals("Repeated accesses reuse the same configuration",script,AdBlockResources.cosmeticScript(context,Collections.singleton("allowed.example")));
    }
    @Test public void aBlockedHostFilterWorkerDoesNotBlockCosmeticFiltering()throws Exception{
        Context context=RuntimeEnvironment.getApplication();AdBlockResources.cosmeticScript(context,Collections.emptySet());
        java.lang.reflect.Field field=AdBlockResources.class.getDeclaredField("HOST_LOCK");field.setAccessible(true);Object hostLock=field.get(null);
        java.util.concurrent.CountDownLatch held=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        Thread loader=new Thread(()->{synchronized(hostLock){held.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}});loader.start();
        java.util.concurrent.ExecutorService requester=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            assertTrue(held.await(2,java.util.concurrent.TimeUnit.SECONDS));
            String script=requester.submit(()->AdBlockResources.cosmeticScript(context,Collections.singleton("independent.example"))).get(2,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(script.contains("independent.example"));
        }finally{release.countDown();requester.shutdownNow();loader.join(2000);}
    }
    @Test public void failedUpdateDoesNotReplaceExistingFilters()throws Exception{
        Context context=RuntimeEnvironment.getApplication();AdBlockResources.ensureLoaded(context);
        File cache=new File(context.getNoBackupFilesDir(),"adblock-hosts.txt");
        boolean existed=cache.exists();int count=AdBlocker.hostCount();
        try{AdBlockResources.installUpdate(context,"example.org\n".getBytes(StandardCharsets.UTF_8));fail("Too few domains");}catch(java.io.IOException expected){}
        assertEquals(existed,cache.exists());assertEquals(count,AdBlocker.hostCount());
    }
}
