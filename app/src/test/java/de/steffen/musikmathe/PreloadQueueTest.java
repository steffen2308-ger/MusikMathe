package de.steffen.musikmathe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreloadQueueTest {
    private static final class Request implements PreloadQueue.Cancel {
        final String url;final Consumer<Boolean> done;boolean cancelled,adopted;
        Request(String url,Consumer<Boolean> done){this.url=url;this.done=done;}
        @Override public void cancel(){cancelled=true;}
        @Override public void adopt(){adopted=true;}
    }
    private static final class Fixture {
        final List<Request> requests=new ArrayList<>();
        final PreloadQueue queue=new PreloadQueue((url,done)->{Request r=new Request(url,done);requests.add(r);return r;});
    }
    @Test public void twoRequestsRunAtOnceAndDuplicateLinksAreNotFetchedAgain(){
        Fixture f=new Fixture();f.queue.enable();f.queue.add(Arrays.asList("one","two","one","three"));
        assertEquals(2,f.requests.size());assertEquals(1,f.queue.waiting());
        f.requests.get(0).done.accept(true);assertEquals("three",f.requests.get(2).url);assertEquals(2,f.queue.active());
        f.requests.get(1).done.accept(false);f.requests.get(2).done.accept(true);f.queue.add(Arrays.asList("one","two","three"));
        assertEquals(3,f.requests.size());assertEquals(2,f.queue.completed);assertEquals(1,f.queue.failed);
    }
    @Test public void wifiLossCancelsPendingWorkAndLateCallbacksCannotRestartIt(){
        Fixture f=new Fixture();f.queue.enable();f.queue.add(Arrays.asList("one","two","three"));f.queue.pause();
        assertTrue(f.requests.get(0).cancelled);assertTrue(f.requests.get(1).cancelled);
        f.requests.get(0).done.accept(true);f.queue.add(Arrays.asList("four"));
        assertEquals(2,f.requests.size());assertEquals(0,f.queue.active());assertEquals(0,f.queue.waiting());assertEquals(0,f.queue.completed);
        f.queue.enable();f.queue.add(Arrays.asList("one","two","three"));assertEquals(4,f.requests.size());
        f.requests.get(1).done.accept(true);assertEquals(2,f.queue.active());
    }
    @Test public void navigationAdoptsOnlyTheSelectedRequestAndResetsThePage(){
        Fixture f=new Fixture();f.queue.enable();f.queue.add(Arrays.asList("one","two","three"));f.queue.reset("two");
        assertTrue(f.requests.get(0).cancelled);assertFalse(f.requests.get(1).cancelled);assertTrue(f.requests.get(1).adopted);
        f.requests.get(1).done.accept(true);assertEquals(0,f.queue.completed);assertEquals(2,f.requests.size());
        f.queue.enable();f.queue.add(Arrays.asList("new"));assertEquals("new",f.requests.get(2).url);
    }
    @Test public void synchronousFailuresCannotRecurseOrExceedThePageLimit(){
        List<String> requests=new ArrayList<>();PreloadQueue queue=new PreloadQueue((url,done)->{requests.add(url);done.accept(false);return ()->{};});
        List<String> urls=new ArrayList<>();for(int i=0;i<1200;i++)urls.add("url"+i);
        queue.enable();queue.add(urls);queue.add(urls);
        assertEquals(PreloadLinks.MAX_LINKS,requests.size());assertEquals(PreloadLinks.MAX_LINKS,queue.failed);assertEquals(0,queue.active());
    }
}
