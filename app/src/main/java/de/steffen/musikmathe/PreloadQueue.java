package de.steffen.musikmathe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** UI-thread queue; late callbacks cannot resume a cancelled page or a disabled feature. */
final class PreloadQueue {
    interface Cancel { void cancel(); default void adopt(){} }
    interface Fetcher { Cancel fetch(String url,Consumer<Boolean> done); }
    private static final class Task { Cancel cancel; }
    private final Fetcher fetcher;
    private final ArrayDeque<String> pending=new ArrayDeque<>();
    private final LinkedHashMap<String,Task> running=new LinkedHashMap<>();
    private final Set<String> seen=new HashSet<>();
    private boolean enabled,pumping;
    private int generation;
    int completed,failed;
    PreloadQueue(Fetcher fetcher){this.fetcher=fetcher;}
    void enable(){enabled=true;pump();}
    void add(List<String> urls){if(!enabled)return;for(String url:urls)if(seen.size()<PreloadLinks.MAX_LINKS&&seen.add(url))pending.add(url);pump();}
    int waiting(){return pending.size();}
    int active(){return running.size();}
    void pause(){cancel(null,false);}
    void reset(String adopted){cancel(adopted,true);}
    private void cancel(String adopted,boolean forget){
        enabled=false;generation++;
        ArrayList<Cancel> cancellations=new ArrayList<>();
        for(java.util.Map.Entry<String,Task> row:running.entrySet()){
            seen.remove(row.getKey());if(row.getValue().cancel!=null){if(row.getKey().equals(adopted))row.getValue().cancel.adopt();else cancellations.add(row.getValue().cancel);}
        }
        seen.removeAll(pending);pending.clear();running.clear();
        if(forget){seen.clear();completed=0;failed=0;}
        for(Cancel cancel:cancellations)cancel.cancel();
    }
    private void pump(){
        if(pumping)return;pumping=true;
        try{while(enabled&&running.size()<2&&!pending.isEmpty()){
            String url=pending.remove();Task task=new Task();running.put(url,task);int epoch=generation;
            Consumer<Boolean> done=success->{if(epoch!=generation||running.get(url)!=task)return;running.remove(url);if(success)completed++;else failed++;pump();};
            try{task.cancel=fetcher.fetch(url,done);}catch(RuntimeException failure){done.accept(false);}
        }}finally{pumping=false;}
    }
}
