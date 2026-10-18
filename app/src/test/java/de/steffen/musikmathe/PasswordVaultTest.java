package de.steffen.musikmathe;
import org.junit.Test;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class PasswordVaultTest {
    @Test public void originsAreExactAndHttpsOnly(){
        assertEquals("https://example.com",PasswordVault.origin("https://EXAMPLE.com:443/login?x=1#test"));
        assertEquals("https://example.com:8443",PasswordVault.origin("https://example.com:8443/login"));
        for(String url:new String[]{"http://example.com","https://user:password@example.com","javascript:alert(1)","file:///login","https://example.com:70000","not a url"})assertEquals("",PasswordVault.origin(url));
        assertNotEquals(PasswordVault.origin("https://example.com"),PasswordVault.origin("https://example.com.evil.test"));
        assertNotEquals(PasswordVault.origin("https://example.com"),PasswordVault.origin("https://sub.example.com"));
        assertNotEquals(PasswordVault.origin("https://example.com"),PasswordVault.origin("https://example.com:8443"));
    }
    private SecretKey key()throws Exception{KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);return generator.generateKey();}
    @Test public void ciphertextUsesFreshNonceAndAuthenticatedEncryption()throws Exception{
        SecretKey key=key();byte[] plain="username@example.test / Dummy password".getBytes(StandardCharsets.UTF_8);
        byte[] first=PasswordVault.encrypt(key,plain),second=PasswordVault.encrypt(key,plain);
        assertFalse(java.util.Arrays.equals(first,second));assertArrayEquals(plain,PasswordVault.decrypt(key,first));
        assertFalse(new String(first,StandardCharsets.ISO_8859_1).contains("Dummy password"));
        first[first.length-1]^=1;try{PasswordVault.decrypt(key,first);fail("Modified ciphertext must fail");}catch(java.security.GeneralSecurityException expected){}
    }
    @Test public void wrongKeyAndTruncatedFilesFailClosed()throws Exception{
        byte[] encrypted=PasswordVault.encrypt(key(),new byte[]{1,2,3});
        try{PasswordVault.decrypt(key(),encrypted);fail("Wrong key must fail");}catch(java.security.GeneralSecurityException expected){}
        for(int size:new int[]{0,1,12,28})try{PasswordVault.decrypt(key(),new byte[size]);fail("Truncated vault must fail");}catch(java.security.GeneralSecurityException expected){}
    }
}
