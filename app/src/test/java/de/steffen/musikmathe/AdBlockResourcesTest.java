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
    }
    @Test public void failedUpdateDoesNotReplaceExistingFilters()throws Exception{
        Context context=RuntimeEnvironment.getApplication();AdBlockResources.ensureLoaded(context);
        File cache=new File(context.getNoBackupFilesDir(),"adblock-hosts.txt");
        boolean existed=cache.exists();int count=AdBlocker.hostCount();
        try{AdBlockResources.installUpdate(context,"example.org\n".getBytes(StandardCharsets.UTF_8));fail("Too few domains");}catch(java.io.IOException expected){}
        assertEquals(existed,cache.exists());assertEquals(count,AdBlocker.hostCount());
    }
}
