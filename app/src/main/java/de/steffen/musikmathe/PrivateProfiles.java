package de.steffen.musikmathe;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Callable;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.JSONObject;

/** Password-bound encrypted areas. A short-lived Keystore lease permits recovery after process death. */
@android.annotation.TargetApi(28)
final class PrivateProfiles {
    private static final Object LOCK=new Object();
    private static final String LEASE_KEY="bestbrowser.private.active.v1";
    private static final java.util.concurrent.ScheduledExecutorService RECOVERY=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private static final java.util.concurrent.atomic.AtomicBoolean RECOVERY_PENDING=new java.util.concurrent.atomic.AtomicBoolean();
    static volatile Session active;
    static volatile String recoveryError="";
    static File directory(Context c)throws IOException{File dir=new File(c.getNoBackupFilesDir(),"private-profiles");if(!dir.mkdirs()&&!dir.isDirectory())throw new IOException("Der private Speicher ist nicht verfügbar.");return dir;}
    private static <T>T locked(Context c,Callable<T> action)throws Exception{synchronized(LOCK){
        try(RandomAccessFile f=new RandomAccessFile(new File(directory(c),"coord.lock"),"rw");FileLock ignored=f.getChannel().lock()){return action.call();}
    }}
    static AtomicFile dataFile(Context c,String id)throws IOException{return new AtomicFile(new File(directory(c),valid(id)+".data"));}
    static AtomicFile webFile(Context c,String id)throws IOException{return new AtomicFile(new File(directory(c),valid(id)+".web"));}
    private static String valid(String id)throws IOException{if(id==null||!id.matches("[a-f0-9]{64}"))throw new IOException("Ungültiger privater Bereich.");return id;}
    private static boolean exists(AtomicFile file){return file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists();}
    static void write(AtomicFile file,byte[] bytes)throws IOException{FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}}
    static byte[] salt(Context c)throws Exception{return locked(c,()->{
        AtomicFile stored=new AtomicFile(new File(directory(c),"salt.v1"));
        if(exists(stored)){byte[] value=stored.readFully();if(value.length!=32)throw new IOException("Privater Passwortspeicher ist beschädigt.");return value;}
        File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".data")||f.getName().endsWith(".web"))throw new IOException("Der private Passwortschlüssel fehlt.");
        byte[] value=new byte[32];new SecureRandom().nextBytes(value);write(stored,value);return value;
    });}
    static final class Session implements AutoCloseable {
        final Context context;final String id;final byte[] material;final boolean fresh;
        private JSONObject data;
        String failure="";
        private boolean closed;
        Session(Context c,String id,byte[] material,JSONObject data,boolean fresh){this.context=c.getApplicationContext();this.id=id;this.material=material;this.data=data;this.fresh=fresh;}
        synchronized String string(String key,String fallback){check();return data.optString(key,fallback);}
        synchronized int integer(String key,int fallback){check();return data.optInt(key,fallback);}
        synchronized boolean bool(String key,boolean fallback){check();return data.optBoolean(key,fallback);}
        synchronized JSONObject object(String key){check();JSONObject value=data.optJSONObject(key);return value==null?new JSONObject():value;}
        synchronized void put(String key,Object value){check();try{data.put(key,value);flush();}catch(Exception e){failure="Private Daten konnten nicht gespeichert werden. Bitte den freien Speicher prüfen.";}}
        synchronized boolean flush(){
            check();try{byte[] plain=data.toString().getBytes(StandardCharsets.UTF_8);if(plain.length>8*1024*1024-29)throw new IOException("Privater Speicher zu groß");write(dataFile(context,id),PrivateCrypto.encrypt(PrivateCrypto.dataKey(material),PrivateCrypto.aad("data",id),plain));failure="";return true;}
            catch(Exception e){failure="Private Daten konnten nicht gespeichert werden. Bitte den freien Speicher prüfen.";return false;}
        }
        boolean matches(char[] password)throws Exception{byte[] next=PrivateCrypto.derive(password,salt(context));try{return MessageDigest.isEqual(material,next);}finally{Arrays.fill(next,(byte)0);}}
        private void check(){if(closed)throw new IllegalStateException("Der private Bereich ist gesperrt.");}
        @Override public synchronized void close(){if(closed)return;closed=true;Arrays.fill(material,(byte)0);data=new JSONObject();if(active==this)active=null;}
    }
    static Session probe(Context c,char[] password)throws Exception{
        byte[] material=PrivateCrypto.derive(password,salt(c));boolean retained=false;
        try{
            String id=PrivateCrypto.id(material);AtomicFile file=dataFile(c,id);boolean fresh=!exists(file);JSONObject data;
            if(fresh){data=new JSONObject().put("schema",1).put("engine",c.getSharedPreferences("browser",0).getString("engine","google")).put("night",c.getSharedPreferences("appearance",0).getBoolean("night",false));}
            else{if(Math.max(file.getBaseFile().length(),new File(file.getBaseFile()+".bak").length())>8*1024*1024)throw new IOException("Privater Speicher ist zu groß.");data=new JSONObject(new String(PrivateCrypto.decrypt(PrivateCrypto.dataKey(material),PrivateCrypto.aad("data",id),file.readFully()),StandardCharsets.UTF_8));if(data.optInt("schema")!=1)throw new IOException("Unbekanntes Format des privaten Bereichs.");}
            Session session=new Session(c,id,material,data,fresh);retained=true;return session;
        }finally{if(!retained)Arrays.fill(material,(byte)0);}
    }
    static void activate(Session session)throws Exception{locked(session.context,()->{
        if(active!=null)throw new IOException("Ein privater Bereich ist bereits geöffnet.");
        recoverLocked(session.context);
        if(!session.flush())throw new IOException(session.failure);
        File destination=new File(session.context.getDataDir(),"app_webview_"+PrivateSession.SUFFIX);
        PrivateArchive.restore(webFile(session.context,session.id),new File(directory(session.context),"restore-"+UUID.randomUUID()),destination,session.material,session.id);
        JSONObject lease=new JSONObject().put("id",session.id).put("material",Base64.encodeToString(session.material,Base64.NO_WRAP));
        try{write(leaseFile(session.context),PrivateCrypto.encrypt(leaseKey(true),PrivateCrypto.aad("lease","v1"),lease.toString().getBytes(StandardCharsets.UTF_8)));active=session;}
        catch(Exception e){PrivateSession.wipe(session.context);throw e;}
        return null;
    });}
    private static AtomicFile leaseFile(Context c)throws IOException{return new AtomicFile(new File(directory(c),"active.lease"));}
    private static SecretKey leaseKey(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(LEASE_KEY))return (SecretKey)store.getKey(LEASE_KEY,null);
        if(!create)throw new IOException("Der Wiederherstellungsschlüssel fehlt.");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(LEASE_KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return generator.generateKey();
    }
    private static void removeLeaseKey()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(LEASE_KEY))store.deleteEntry(LEASE_KEY);}
    /** The caller guarantees that there is no live WebView using the private working directory. */
    private static void recoverLocked(Context c)throws Exception{
        if(active!=null)throw new IOException("Der private Bereich wird noch verwendet.");
        AtomicFile lease=leaseFile(c);
        if(exists(lease)){
            JSONObject record=new JSONObject(new String(PrivateCrypto.decrypt(leaseKey(false),PrivateCrypto.aad("lease","v1"),lease.readFully()),StandardCharsets.UTF_8));
            String id=valid(record.getString("id"));byte[] material=Base64.decode(record.getString("material"),Base64.NO_WRAP);if(material.length!=96)throw new IOException("Ungültiger Wiederherstellungsschlüssel.");
            try{PrivateArchive.save(new File(c.getDataDir(),"app_webview_"+PrivateSession.SUFFIX),webFile(c,id),material,id);PrivateSession.wipe(c);}finally{Arrays.fill(material,(byte)0);}
            lease.delete();removeLeaseKey();
        }else{PrivateSession.wipe(c);removeLeaseKey();}
        File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(f.getName().startsWith("restore-"))PrivateSession.remove(f);
        recoveryError="";
    }
    static void recoverStopped(Context context){
        if(Build.VERSION.SDK_INT<28||!RECOVERY_PENDING.compareAndSet(false,true))return;
        RECOVERY.execute(()->recoverAttempt(context.getApplicationContext(),0));
    }
    private static void recoverAttempt(Context c,int attempt){
        if(PrivateSession.running(c)){
            if(attempt<30)RECOVERY.schedule(()->recoverAttempt(c,attempt+1),500,java.util.concurrent.TimeUnit.MILLISECONDS);
            else RECOVERY_PENDING.set(false);
            return;
        }
        try{locked(c,()->{if(!PrivateSession.running(c))recoverLocked(c);return null;});}
        catch(Exception error){recoveryError="Der letzte private Bereich konnte noch nicht sicher gesichert werden. Bitte freien Speicher prüfen und erneut öffnen.";}
        finally{RECOVERY_PENDING.set(false);}
    }
    static Session forContext(Context context){return active!=null&&(BrowserApplication.privateProcess||context instanceof PrivateActivity)?active:null;}
}
