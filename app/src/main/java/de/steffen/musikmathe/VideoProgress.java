package de.steffen.musikmathe;

import java.util.Locale;

/** One export phase, sampled across loader/worker threads; no URLs or persisted history. */
final class VideoProgress {
    final String phase;
    private final long started;
    private long bytes,total=-1,lastMovement;
    private int percent=-1;
    VideoProgress(String phase,long now){this.phase=phase;started=lastMovement=now;}
    synchronized void bytes(long value,long length,long now){
        if(value>bytes)lastMovement=now;bytes=Math.max(bytes,value);total=length>0?length:-1;
        if(total>0)percent=(int)Math.min(99,100.0*bytes/total);
    }
    synchronized void received(int count,long now){if(count>0){bytes+=count;lastMovement=now;}}
    synchronized void percent(int value,long now){
        int next=value<0?-1:Math.min(99,value);if(next>percent)lastMovement=now;percent=next;
    }
    synchronized Reading read(long now){
        long elapsed=Math.max(0,now-started);boolean stalled=now-lastMovement>8000;
        long rate=elapsed>=1000&&!stalled?(long)(bytes*1000.0/elapsed):0;
        long eta=-1;
        if(elapsed>=2000&&!stalled){
            if(total>bytes&&bytes>0)eta=(long)Math.ceil((total-bytes)*(elapsed/1000.0)/bytes);
            else if(total<0&&percent>0)eta=(long)Math.ceil((100-percent)*(elapsed/1000.0)/percent);
        }
        return new Reading(phase,percent,bytes,total,rate,eta,elapsed/1000,stalled);
    }
    static final class Reading {
        final String phase;
        final int percent;
        final long bytes,total,rate,eta,elapsed;
        final boolean stalled;
        Reading(String phase,int percent,long bytes,long total,long rate,long eta,long elapsed,boolean stalled){
            this.phase=phase;this.percent=percent;this.bytes=bytes;this.total=total;this.rate=rate;this.eta=eta;this.elapsed=elapsed;this.stalled=stalled;
        }
        String detail(){
            String size=amount(bytes)+(total>0?" von "+amount(total):" übertragen · Gesamtgröße unbekannt");
            return size+"\n"+(rate>0?amount(rate)+"/s · ":"")+"Laufzeit: "+duration(elapsed)+"\n"+
                (stalled?"Warte auf weiteren Fortschritt …":eta>=0?"Restzeit für diesen Schritt: ca. "+duration(eta):"Restzeit für diesen Schritt: wird ermittelt …");
        }
    }
    static String amount(long bytes){
        if(bytes<1000)return bytes+" B";if(bytes<1000000)return String.format(Locale.GERMANY,"%.1f kB",bytes/1000.0);
        if(bytes<1000000000)return String.format(Locale.GERMANY,"%.1f MB",bytes/1000000.0);return String.format(Locale.GERMANY,"%.2f GB",bytes/1000000000.0);
    }
    static String duration(long seconds){
        if(seconds<60)return seconds+" s";if(seconds<3600)return (seconds/60)+" min "+(seconds%60)+" s";return (seconds/3600)+" h "+((seconds%3600)/60)+" min";
    }
}
