package de.steffen.musikmathe;

import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Passwords are never stored. Separate derived keys protect metadata, streams and lookup IDs. */
@android.annotation.TargetApi(28)
final class PrivateCrypto {
    static final int ITERATIONS=210000;
    static byte[] derive(char[] password,byte[] salt)throws GeneralSecurityException{
        if(password.length<4||password.length>256||salt.length!=32)throw new GeneralSecurityException("Bitte ein Passwort mit 4 bis 256 Zeichen verwenden.");
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,768);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    static String id(byte[] material)throws GeneralSecurityException{
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Arrays.copyOfRange(material,64,96),"HmacSHA256"));
        StringBuilder result=new StringBuilder();for(byte b:mac.doFinal(aad("lookup","v1")))result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();
    }
    static byte[] aad(String type,String id){return ("BestBrowser private v1 "+type+" "+id).getBytes(StandardCharsets.UTF_8);}
    static SecretKey dataKey(byte[] material){return new SecretKeySpec(Arrays.copyOf(material,32),"AES");}
    static StreamingAead stream(byte[] material)throws GeneralSecurityException{return new AesGcmHkdfStreaming(Arrays.copyOfRange(material,32,64),"HmacSha256",32,65536,0);}
    static byte[] encrypt(SecretKey key,byte[] aad,byte[] plain)throws GeneralSecurityException{
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);c.updateAAD(aad);byte[] iv=c.getIV();if(iv.length!=12)throw new GeneralSecurityException("Ungültiger Initialisierungsvektor");
        byte[] encrypted=c.doFinal(plain);return ByteBuffer.allocate(1+12+encrypted.length).put((byte)1).put(iv).put(encrypted).array();
    }
    static byte[] decrypt(SecretKey key,byte[] aad,byte[] encrypted)throws GeneralSecurityException{
        if(encrypted.length<29||encrypted.length>8*1024*1024||encrypted[0]!=1)throw new GeneralSecurityException("Ungültiger privater Speicher");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(encrypted,1,13)));c.updateAAD(aad);return c.doFinal(encrypted,13,encrypted.length-13);
    }
}
