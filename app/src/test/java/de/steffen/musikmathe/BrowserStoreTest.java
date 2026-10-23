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
    @Test public void historySuggestionsUseFreshStoreAndNeverReadPrivateHistory(){
        store.visit("https://wiki.example/", "Wissen aus dem Verlauf");
        assertEquals(1,HistorySuggestions.match(new BrowserStore(RuntimeEnvironment.getApplication()).read("history"),"wissen").size());
        assertTrue(HistorySuggestions.match(new BrowserStore(RuntimeEnvironment.getApplication(),true).read("history"),"wissen").isEmpty());
        store.write("history",java.util.Collections.emptyList());
        assertTrue(HistorySuggestions.match(new BrowserStore(RuntimeEnvironment.getApplication()).read("history"),"wissen").isEmpty());
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
    @Test public void cachedRowsAreDefensiveAndRefreshAfterAnotherWindowWrites(){
        Context context=RuntimeEnvironment.getApplication();store.visit("https://first.example/","First");
        java.util.List<BrowserStore.Entry> first=store.read("history");BrowserStore.Entry cached=first.get(0);first.clear();
        assertSame("Unchanged rows do not need another JSON parse",cached,store.read("history").get(0));
        BrowserStore other=new BrowserStore(context,false,"second");other.visit("https://second.example/","Second");
        assertEquals("Second",store.read("history").get(0).title);store.frequentSites().clear();assertFalse(store.frequentSites().isEmpty());
        other.write("history",java.util.Collections.emptyList());assertTrue(store.read("history").isEmpty());assertTrue(store.frequentSites().isEmpty());
    }
    @Test public void writtenHistoryCacheStillFiltersInvalidUrlsAndTracksExternalClears(){
        Context context=RuntimeEnvironment.getApplication();
        store.write("history",Arrays.asList(new BrowserStore.Entry("javascript:alert(1)","Invalid",0),new BrowserStore.Entry("https://good.example/","Valid",0)));
        assertEquals(1,store.read("history").size());assertEquals("Valid",store.read("history").get(0).title);
        store.read("history").clear();assertEquals(1,store.read("history").size());
        context.getSharedPreferences("browser",0).edit().remove("history").commit();assertTrue(store.read("history").isEmpty());
    }
    @Test public void tabSnapshotUpdatesTabsAndSelectionTogetherAndSkipsUnchangedWrites(){
        android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("browser",0);
        java.util.List<BrowserStore.Entry> rows=Arrays.asList(new BrowserStore.Entry(Address.HOME,"Start",0),new BrowserStore.Entry("https://example.org/","Example",0));
        store.saveTabs(rows,1);assertEquals(2,new BrowserStore(RuntimeEnvironment.getApplication()).read("tabs").size());assertEquals(1,new BrowserStore(RuntimeEnvironment.getApplication()).selected());
        java.util.concurrent.atomic.AtomicInteger changed=new java.util.concurrent.atomic.AtomicInteger();
        android.content.SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->changed.incrementAndGet();prefs.registerOnSharedPreferenceChangeListener(listener);
        try{store.saveTabs(rows,1);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(0,changed.get());}finally{prefs.unregisterOnSharedPreferenceChangeListener(listener);}
    }
    @Test public void secondWindowHasIndependentTabsAndSharesOnlyNormalProfileData(){
        Context context=RuntimeEnvironment.getApplication();BrowserStore second=new BrowserStore(context,false,"second");
        store.write("tabs",Arrays.asList(new BrowserStore.Entry("https://example.org/video","Video",0)));store.selected(0);
        second.write("tabs",Arrays.asList(new BrowserStore.Entry(Address.HOME,"Start",0),new BrowserStore.Entry("https://example.org/browse","Surfen",0)));second.selected(1);
        assertEquals("https://example.org/video",new BrowserStore(context).read("tabs").get(0).url);
        assertEquals(0,new BrowserStore(context).selected());assertEquals(2,new BrowserStore(context,false,"second").read("tabs").size());assertEquals(1,new BrowserStore(context,false,"second").selected());
        second.visit("https://example.org/shared","Gemeinsamer Verlauf");assertEquals("Gemeinsamer Verlauf",store.read("history").get(0).title);
        store.engine("brave");assertEquals("brave",second.engine());
        assertTrue(new BrowserStore(context,true,"second").read("tabs").isEmpty());
    }
    @Test public void preloadingDefaultsOnPersistsAndIsAlwaysOffInPrivateTabs(){
        Context context=RuntimeEnvironment.getApplication();assertTrue(store.linkPreloadingEnabled());
        store.linkPreloadingEnabled(false);assertFalse(new BrowserStore(context).linkPreloadingEnabled());
        BrowserStore secret=new BrowserStore(context,true);secret.linkPreloadingEnabled(true);assertFalse(secret.linkPreloadingEnabled());assertFalse(new BrowserStore(context).linkPreloadingEnabled());
        store.linkPreloadingEnabled(true);assertTrue(new BrowserStore(context).linkPreloadingEnabled());assertFalse(new BrowserStore(context,true).linkPreloadingEnabled());
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
    @Test public void parentSiteExceptionsDoNotLeaveHiddenChildExceptionsBehind(){
        store.adBlockException("news.example.org",true);store.adBlockException("example.org",true);
        assertEquals(java.util.Collections.singleton("example.org"),store.adBlockExceptions());
        store.adBlockException("news.example.org",true);
        assertEquals(java.util.Collections.singleton("example.org"),store.adBlockExceptions());
        store.adBlockException("example.org",false);assertTrue(store.adBlockExceptions().isEmpty());
    }
}

