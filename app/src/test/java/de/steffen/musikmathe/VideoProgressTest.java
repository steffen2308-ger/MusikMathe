package de.steffen.musikmathe;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoProgressTest {
    @Test public void estimatesKnownSizeAndHidesStaleEstimate(){
        VideoProgress progress=new VideoProgress("Download",1000);progress.bytes(250000,1000000,6000);
        VideoProgress.Reading reading=progress.read(6000);
        assertEquals(25,reading.percent);assertEquals(50000,reading.rate);assertEquals(15,reading.eta);
        assertTrue(reading.detail().contains("250,0 kB von 1,0 MB"));
        reading=progress.read(15000);assertTrue(reading.stalled);assertEquals(-1,reading.eta);assertEquals(0,reading.rate);
        progress.bytes(500000,1000000,16000);assertFalse(progress.read(16000).stalled);assertEquals(15,progress.read(16000).eta);
    }
    @Test public void unknownLengthAndStartupNeverInventAnEndTime(){
        VideoProgress progress=new VideoProgress("Stream",0);progress.received(100000,500);
        assertEquals(-1,progress.read(500).percent);assertEquals(-1,progress.read(500).eta);
        assertEquals(-1,progress.read(3000).eta);assertTrue(progress.read(3000).detail().contains("Gesamtgröße unbekannt"));
        progress.percent(25,4000);assertEquals(12,progress.read(4000).eta);
        progress.percent(-1,4500);assertEquals(-1,progress.read(4500).eta);
    }
    @Test public void finalCopyStartsSeparateEstimateAndDoesNotClaimSuccessBeforeClose(){
        VideoProgress copying=new VideoProgress("Zielordner",20000);copying.bytes(500000,1000000,22000);
        assertEquals(2,copying.read(22000).eta);copying.bytes(1000000,1000000,23000);
        assertEquals(99,copying.read(23000).percent);assertEquals(-1,copying.read(23000).eta);
    }
}
