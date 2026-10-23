package de.steffen.musikmathe;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class PrivateAccessTest {
    @Test public void backgroundDeadlineAlsoAppliesWhileAnAsyncCloseIsPending()throws Exception{
        try(var controller=Robolectric.buildActivity(PrivateActivity.class).setup()){
            PrivateActivity activity=controller.get();PrivateProfiles.active=PrivateUnitSupport.session(activity);
            Field access=MainActivity.class.getDeclaredField("privateAccess");access.setAccessible(true);((PrivateAccess)access.get(activity)).destroy();
            AtomicLong clock=new AtomicLong(1000);PrivateAccess gate=new PrivateAccess(activity,()->{},()->{},clock::get);access.set(activity,gate);
            Field locked=PrivateAccess.class.getDeclaredField("locked");locked.setAccessible(true);locked.setBoolean(gate,false);
            Field closing=MainActivity.class.getDeclaredField("closingPrivate");closing.setAccessible(true);closing.setBoolean(activity,true);
            try{
                controller.pause();assertTrue("Backgrounding locks even if final storage is pending",gate.locked());
                clock.addAndGet(PrivateAccess.BACKGROUND_TIMEOUT_MS+1);closing.setBoolean(activity,false);controller.resume();
                assertTrue("A failed close cannot bypass the background password deadline",gate.locked());
            }finally{gate.destroy();PrivateProfiles.active.close();}
        }
    }
    @Test public void backgroundDeadlineRetainsOnlyAnAuthenticatedArea()throws Exception{
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
            MainActivity activity=controller.get();PrivateProfiles.active=PrivateUnitSupport.session(activity);
            AtomicLong clock=new AtomicLong(1000);AtomicInteger resumed=new AtomicInteger(),actions=new AtomicInteger();
            PrivateAccess gate=new PrivateAccess(activity,resumed::incrementAndGet,()->{},clock::get);
            Field locked=PrivateAccess.class.getDeclaredField("locked");locked.setAccessible(true);locked.setBoolean(gate,false);
            try{
                gate.background();assertTrue(gate.locked());gate.defer(actions::incrementAndGet);
                clock.addAndGet(PrivateAccess.BACKGROUND_TIMEOUT_MS);gate.foreground();assertFalse("Exactly five minutes still retained",gate.locked());assertEquals(1,resumed.get());assertEquals(1,actions.get());
                gate.background();clock.addAndGet(PrivateAccess.BACKGROUND_TIMEOUT_MS-1);gate.background();clock.addAndGet(2);gate.foreground();assertTrue("Repeated pause must not restart the deadline",gate.locked());assertEquals(1,resumed.get());
                gate.background();clock.incrementAndGet();gate.foreground();assertTrue("Short return cannot bypass an expired password gate",gate.locked());
            }finally{gate.destroy();PrivateProfiles.active.close();}
            PrivateAccess cold=new PrivateAccess(activity,resumed::incrementAndGet,()->{},clock::get);try{cold.background();clock.incrementAndGet();cold.foreground();assertTrue("Cold area always requires authentication",cold.locked());}finally{cold.destroy();}
        }
    }
}
