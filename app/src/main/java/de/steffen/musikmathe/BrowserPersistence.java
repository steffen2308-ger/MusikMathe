package de.steffen.musikmathe;

import android.webkit.CookieManager;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One ordered disk queue per process. Lifecycle transitions never wait for cookie fsync. */
final class BrowserPersistence {
    private static final ScheduledExecutorService DISK=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"browser-storage");t.setDaemon(true);return t;});
    private static final AtomicBoolean COOKIES_PENDING=new AtomicBoolean();
    private BrowserPersistence(){}
    static void execute(Runnable action){DISK.execute(action);}
    static void later(Runnable action){DISK.schedule(action,150,TimeUnit.MILLISECONDS);}
    static void cookies(CookieManager manager){
        if(!COOKIES_PENDING.compareAndSet(false,true))return;
        execute(()->{try{manager.flush();}finally{COOKIES_PENDING.set(false);}});
    }
}
