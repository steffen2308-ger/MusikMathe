package de.steffen.musikmathe;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
public class BrowserStoreTest {
    private BrowserStore store;
    @Before public void setup(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("browser",0).edit().clear().commit();store=new BrowserStore(c);}
    @Test public void visitsAreUniqueRecentAndExcludeLocalPages(){
        store.visit("https://a.example","A");store.visit("https://b.example","B");store.visit("https://a.example","A updated");store.visit(Address.HOME,"Start");
        assertEquals(2,store.read("history").size());assertEquals("A updated",store.read("history").get(0).title);
        assertEquals("https://b.example",store.read("history").get(1).url);
    }
    @Test public void bookmarksAndSettingsSurviveNewStoreInstance(){
        assertTrue(store.toggleBookmark("https://example.org","Beispiel"));store.engine("duckduckgo");
        BrowserStore restored=new BrowserStore(RuntimeEnvironment.getApplication());
        assertTrue(restored.bookmarked("https://example.org"));assertEquals("duckduckgo",restored.engine());
        assertFalse(restored.toggleBookmark("https://example.org","Beispiel"));assertTrue(restored.read("bookmarks").isEmpty());
    }
    @Test public void restoredTabsKeepOrderAndDesktopModeAndRejectUntrustedSchemes(){
        store.write("tabs",Arrays.asList(new BrowserStore.Entry(Address.HOME,"Start",0),new BrowserStore.Entry("https://example.org","Page",0,true),new BrowserStore.Entry("javascript:alert(1)","Bad",0)));
        store.selected(1);BrowserStore restored=new BrowserStore(RuntimeEnvironment.getApplication());
        assertEquals(2,restored.read("tabs").size());assertTrue(restored.read("tabs").get(1).desktop);assertEquals(1,restored.selected());
    }
    @Test public void corruptStorageIsRecoverable(){
        RuntimeEnvironment.getApplication().getSharedPreferences("browser",0).edit().putString("tabs","not json").commit();assertTrue(store.read("tabs").isEmpty());
    }
    @Test public void adBlockChoicePersistsAndPrivateChangesStayInMemory(){
        Context context=RuntimeEnvironment.getApplication();
        assertTrue(store.adBlockingEnabled());store.adBlockingEnabled(false);
        assertFalse(new BrowserStore(context).adBlockingEnabled());
        BrowserStore privateStore=new BrowserStore(context,true);
        assertFalse(privateStore.adBlockingEnabled());privateStore.adBlockingEnabled(true);
        assertTrue(privateStore.adBlockingEnabled());assertFalse(new BrowserStore(context).adBlockingEnabled());
        assertFalse(new BrowserStore(context,true).adBlockingEnabled());
    }
    @Test public void siteExceptionsPersistButPrivateChangesDoNot(){
        Context context=RuntimeEnvironment.getApplication();
        store.adBlockException("example.org",true);
        assertTrue(new BrowserStore(context).adBlockExceptions().contains("example.org"));
        BrowserStore privateStore=new BrowserStore(context,true);privateStore.adBlockException("example.org",false);privateStore.adBlockException("private.example",true);
        assertFalse(privateStore.adBlockExceptions().contains("example.org"));
        assertEquals(java.util.Collections.singleton("example.org"),new BrowserStore(context).adBlockExceptions());
        assertEquals(java.util.Collections.singleton("example.org"),new BrowserStore(context,true).adBlockExceptions());
    }
}

