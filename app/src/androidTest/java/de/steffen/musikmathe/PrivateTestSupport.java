package de.steffen.musikmathe;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.util.UUID;
import static org.junit.Assert.*;

/** Uses the real native password screen in the separate process, never a production bypass. */
final class PrivateTestSupport {
    static String password(){return "Device-test-"+UUID.randomUUID();}
    static void unlock(UiDevice device)throws Exception{unlock(device,password());}
    static void unlock(UiDevice device,String password)throws Exception{
        UiObject2 input=device.wait(Until.findObject(By.desc("Passwort für privaten Bereich")),20000);assertNotNull("Private password gate",input);input.setText(password);
        UiObject2 open=device.findObject(By.text("Öffnen"));assertNotNull(open);open.click();
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(70);
        while(System.nanoTime()<end){
            UiObject2 confirmation=device.findObject(By.desc("Passwort bestätigen"));
            if(confirmation!=null&&confirmation.isEnabled()){confirmation.setText(password);UiObject2 create=device.findObject(By.text("Bereich anlegen"));assertNotNull(create);create.click();}
            if(device.hasObject(By.descStartsWith("Privater Tab"))&&!device.hasObject(By.desc("Passwort für privaten Bereich"))&&!device.hasObject(By.desc("Passwort bestätigen")))return;
            Thread.sleep(150);
        }
        fail("Private password did not open its area");
    }
}
