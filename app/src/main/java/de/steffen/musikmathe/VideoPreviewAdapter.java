package de.steffen.musikmathe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/** Dialog-scoped previews: bounded image requests, no disk cache and no media downloads. */
final class VideoPreviewAdapter extends BaseAdapter implements AutoCloseable {
    private static final int LIMIT=2*1024*1024;
    private final Context context;
    private final List<VideoCatalog.Item> items;
    private final String page;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newFixedThreadPool(2);
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    private final Map<String,Bitmap> cache=new HashMap<>();
    private final Map<String,List<ImageView>> waiting=new HashMap<>();
    private final Map<String,List<Runnable>> metadataRows=new HashMap<>();
    private final java.util.Set<String> probes=new java.util.HashSet<>();
    java.util.function.Consumer<VideoCatalog.Item> information;
    Runnable metadataChanged;
    private volatile boolean closed;
    VideoPreviewAdapter(Context context,List<VideoCatalog.Item> items,String page){this.context=context;this.items=items;this.page=page;}
    @Override public int getCount(){return items.size();}
    @Override public VideoCatalog.Item getItem(int position){return items.get(position);}
    @Override public long getItemId(int position){return position;}
    private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    @Override public View getView(int position,View convertView,ViewGroup parent){return row(getItem(position));}
    View row(VideoCatalog.Item item){
        return row(item,false);
    }
    View row(VideoCatalog.Item item,boolean compact){
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.TOP);row.setPadding(dp(20),dp(12),dp(20),dp(12));row.setMinimumHeight(dp(80));
        ImageView image=new ImageView(context);image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);image.setImageResource(android.R.drawable.ic_media_play);image.setBackgroundColor(context.getColor(R.color.browser_field));image.setContentDescription("Kein Vorschaubild verfügbar");row.addView(image,new LinearLayout.LayoutParams(dp(80),dp(48)));
        LinearLayout text=new LinearLayout(context);text.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(0,-2,1);label.leftMargin=dp(12);row.addView(text,label);
        TextView title=new TextView(context);title.setText(item.label());title.setTextColor(context.getColor(R.color.browser_ink));title.setTextSize(15);title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(title);
        TextView facts=new TextView(context);facts.setTextColor(context.getColor(R.color.browser_ink));facts.setTextSize(13);facts.setPadding(0,dp(6),0,0);text.addView(facts);
        Runnable update=()->facts.setText(item.metadata.summary(compact));update.run();metadataRows.computeIfAbsent(item.url,key->new ArrayList<>()).add(update);
        if(!compact&&information!=null){TextView more=new TextView(context);more.setText("Alle Informationen");more.setTextColor(context.getColor(R.color.browser_accent));more.setTextSize(14);more.setGravity(Gravity.CENTER_VERTICAL);more.setMinHeight(dp(48));more.setContentDescription("Videoinformationen: "+item.label());more.setFocusable(false);more.setOnClickListener(v->information.accept(item));text.addView(more);}
        probe(item);
        String url=item.thumbnail;if(closed||url.isEmpty())return row;
        if(cache.containsKey(url)){display(image,cache.get(url));return row;}
        List<ImageView> views=waiting.get(url);if(views!=null){views.add(image);return row;}
        views=new ArrayList<>();views.add(image);waiting.put(url,views);
        worker.execute(()->{Bitmap bitmap=null;try{bitmap=decode(bytes(url));}catch(IOException|RuntimeException ignored){}Bitmap result=bitmap;main.post(()->{if(closed)return;cache.put(url,result);List<ImageView> targets=waiting.remove(url);if(targets!=null)for(ImageView target:targets)display(target,result);});});
        return row;
    }
    private void probe(VideoCatalog.Item item){
        if(closed||probes.size()>=12||probes.contains(item.url)||(item.metadata.probedAt>0&&android.os.SystemClock.elapsedRealtime()-item.metadata.probedAt<30000))return;probes.add(item.url);
        worker.execute(()->{VideoMetadata result=null;try{result=VideoMetadataProbe.read(item,http);}catch(IOException|RuntimeException ignored){}VideoMetadata found=result;main.post(()->{if(closed)return;if(found!=null){item.metadata.merge(found);item.metadata.probedAt=android.os.SystemClock.elapsedRealtime();List<Runnable> updates=metadataRows.get(item.url);if(updates!=null)for(Runnable update:updates)update.run();if(metadataChanged!=null)metadataChanged.run();}});});
    }
    private void display(ImageView image,Bitmap bitmap){if(bitmap==null)return;image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Video-Vorschaubild");}
    private byte[] bytes(String source)throws IOException{
        if(source.startsWith("data:image/"))return android.util.Base64.decode(source.substring(source.indexOf(',')+1),android.util.Base64.NO_WRAP);
        String url=source;
        for(int i=0;i<6&&!closed;i++){
            if(!Address.isWeb(url))throw new IOException("Invalid preview");
            okhttp3.Request.Builder request=new okhttp3.Request.Builder().url(url).header("User-Agent",YouTubeDownloadResolver.AGENT).header("Accept","image/*");
            // Cookies are selected for each image/redirect target; stream authorization is never forwarded.
            String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)request.header("Cookie",cookie);
            if(Address.isWeb(page)&&!(page.startsWith("https://")&&url.startsWith("http://")))request.header("Referer",android.net.Uri.parse(page).buildUpon().path("/").clearQuery().fragment(null).build().toString());
            try(okhttp3.Response response=http.newCall(request.build()).execute()){
                if(response.isRedirect()){
                    String location=response.header("Location");okhttp3.HttpUrl next=location==null?null:response.request().url().resolve(location);
                    if(next==null||(url.startsWith("https://")&&!next.isHttps()))throw new IOException("Invalid redirect");url=next.toString();continue;
                }
                if(!response.isSuccessful()||response.body()==null||!response.header("Content-Type","").startsWith("image/")||response.body().contentLength()>LIMIT)throw new IOException("Invalid image");
                try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(closed||out.size()+n>LIMIT)throw new IOException("Image too large");out.write(buffer,0,n);}return out.toByteArray();
                }
            }
        }
        throw new IOException("Preview unavailable");
    }
    static Bitmap decode(byte[] bytes){
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<=0||options.outHeight<=0||(long)options.outWidth*options.outHeight>40000000)return null;
        options.inJustDecodeBounds=false;options.inSampleSize=1;while(options.outWidth/options.inSampleSize>320||options.outHeight/options.inSampleSize>180)options.inSampleSize*=2;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    }
    @Override public void close(){closed=true;http.dispatcher().cancelAll();worker.shutdownNow();waiting.clear();cache.clear();metadataRows.clear();}
}
