package de.steffen.musikmathe;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrivateCryptoDeviceTest {
    @Test public void existingPlatformKeysAndUnicodePasswordsRemainCompatible()throws Exception{
        byte[] salt=new byte[32];new java.util.Random(42).nextBytes(salt);
        char[] longest=new char[256];Arrays.fill(longest,'ü');
        for(String text:new String[]{"1234","äÖß中🔒","nul\0password",new String(longest)}){
            char[] password=text.toCharArray();PBEKeySpec spec=new PBEKeySpec(password,salt,210000,768);byte[] reference=null,parallel=null;
            try{
                reference=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();parallel=PrivateCrypto.derive(password,salt);
                assertArrayEquals("Exact 96-byte compatibility, all 210000 rounds",reference,parallel);
                String id=PrivateCrypto.id(reference);byte[] data=PrivateCrypto.encrypt(PrivateCrypto.dataKey(reference),PrivateCrypto.aad("data",id),new byte[]{1,2,3});
                assertEquals(id,PrivateCrypto.id(parallel));assertArrayEquals(new byte[]{1,2,3},PrivateCrypto.decrypt(PrivateCrypto.dataKey(parallel),PrivateCrypto.aad("data",id),data));
            }finally{spec.clearPassword();Arrays.fill(password,'\0');if(reference!=null)Arrays.fill(reference,(byte)0);if(parallel!=null)Arrays.fill(parallel,(byte)0);}
        }
    }
}
