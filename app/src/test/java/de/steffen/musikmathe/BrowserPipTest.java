package de.steffen.musikmathe;

import android.util.Rational;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class BrowserPipTest {
    @Test public void videoAspectRatiosStayWithinAndroidPipLimits(){
        assertEquals(new Rational(16,9),BrowserPip.ratio(1920,1080));
        assertEquals(new Rational(9,16),BrowserPip.ratio(1080,1920));
        assertEquals(new Rational(239,100),BrowserPip.ratio(4000,100));
        assertEquals(new Rational(100,239),BrowserPip.ratio(100,4000));
        assertEquals(new Rational(16,9),BrowserPip.ratio(0,0));
    }
}
