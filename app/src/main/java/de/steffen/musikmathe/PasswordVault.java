package de.steffen.musikmathe;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** The complete vault (including usernames and origins) is encrypted, never backed up. */
final class PasswordVault {
    static final String KEY="musikmathe.passwords.v1",FILE="password-vault.bin";
    private static final byte[] AAD="MusikMathe password vault v1".getBytes(StandardCharsets.UTF_8);
    private final AtomicFile file;
    static final class Login {
        final String id,origin,username,password;
        Login(String id,String origin,String username,String password){this.id=id;this.origin=origin;this.username=username;this.password=password;}
    }
    PasswordVault(Context context,boolean privateMode){
        if(privateMode||(Build.VERSION.SDK_INT>=28&&(context.getPackageName()+":private").equals(Application.getProcessName())))throw new SecurityException("Kein Passwortspeicher im privaten Bereich");
        file=new AtomicFile(new File(context.getNoBackupFilesDir(),FILE));
    }
    static String origin(String url){
        try{URI u=new URI(url);String host=u.getHost();int port=u.getPort();
            if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getRawUserInfo()!=null||port==0||port>65535)return "";
            return "https://"+host.toLowerCase(Locale.ROOT)+(port<0||port==443?"":":"+port);
        }catch(Exception e){return "";}
    }
    private SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY))return (SecretKey)store.getKey(KEY,null);
        if(!create)throw new GeneralSecurityException("Passwortschlüssel nicht verfügbar");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    static byte[] encrypt(SecretKey key,byte[] plain)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(AAD);byte[] iv=cipher.getIV(),data=cipher.doFinal(plain);
        if(iv.length!=12)throw new GeneralSecurityException("Ungültiger Initialisierungsvektor");
        return ByteBuffer.allocate(1+12+data.length).put((byte)1).put(iv).put(data).array();
    }
    static byte[] decrypt(SecretKey key,byte[] encrypted)throws Exception{
        if(encrypted.length<29||encrypted.length>2*1024*1024||encrypted[0]!=1)throw new GeneralSecurityException("Ungültiger Passwortspeicher");
        byte[] iv=java.util.Arrays.copyOfRange(encrypted,1,13);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));cipher.updateAAD(AAD);
        return cipher.doFinal(encrypted,13,encrypted.length-13);
    }
    List<Login> all()throws Exception{synchronized(PasswordVault.class){
        List<Login> result=new ArrayList<>();File backup=new File(file.getBaseFile().getPath()+".bak");if(!file.getBaseFile().exists()&&!backup.exists())return result;
        if(Math.max(file.getBaseFile().length(),backup.length())>2*1024*1024)throw new GeneralSecurityException("Passwortspeicher zu groß");
        JSONArray array=new JSONArray(new String(decrypt(key(false),file.readFully()),StandardCharsets.UTF_8));
        if(array.length()>200)throw new GeneralSecurityException("Zu viele Einträge");
        for(int i=0;i<array.length();i++){JSONObject row=array.getJSONObject(i);String o=row.getString("origin");if(!o.equals(origin(o)))throw new GeneralSecurityException("Ungültige Website-Zuordnung");result.add(new Login(row.getString("id"),o,row.getString("username"),row.getString("password")));}return result;
    }}
    List<Login> forSite(String url)throws Exception{String o=origin(url);List<Login> found=new ArrayList<>();if(o.isEmpty())return found;for(Login login:all())if(o.equals(login.origin))found.add(login);return found;}
    void save(String url,String username,String password)throws Exception{synchronized(PasswordVault.class){
        String o=origin(url);if(o.isEmpty()||username==null||password==null||username.length()>320||password.isEmpty()||password.length()>4096)throw new IllegalArgumentException("Ungültige Zugangsdaten");
        List<Login> rows=all();String id=UUID.randomUUID().toString();for(java.util.Iterator<Login> it=rows.iterator();it.hasNext();){Login row=it.next();if(row.origin.equals(o)&&row.username.equals(username)){id=row.id;it.remove();}}
        if(rows.size()>=200)throw new IllegalStateException("Maximal 200 Logins");rows.add(new Login(id,o,username,password));write(rows);
    }}
    void remove(String id)throws Exception{synchronized(PasswordVault.class){List<Login> rows=all();java.util.Iterator<Login> it=rows.iterator();while(it.hasNext())if(it.next().id.equals(id))it.remove();if(rows.isEmpty())clear();else write(rows);}}
    void clear()throws Exception{synchronized(PasswordVault.class){file.delete();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(KEY))store.deleteEntry(KEY);}}
    private void write(List<Login> rows)throws Exception{
        JSONArray array=new JSONArray();for(Login row:rows)array.put(new JSONObject().put("id",row.id).put("origin",row.origin).put("username",row.username).put("password",row.password));
        byte[] encrypted=encrypt(key(!file.getBaseFile().exists()),array.toString().getBytes(StandardCharsets.UTF_8));FileOutputStream out=null;
        try{out=file.startWrite();out.write(encrypted);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
}
