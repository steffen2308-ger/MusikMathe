package de.steffen.musikmathe;

import java.util.Random;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParallelPbkdf2Test {
    static byte[] reference(char[] password,byte[] salt,int rounds)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password,salt,rounds,768);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    @Test public void allThreeBlocksMatchPlatformForBoundariesAndUnicode()throws Exception{
        byte[] salt=new byte[32];new Random(42).nextBytes(salt);
        for(String password:new String[]{"1234","äÖß中🔒","nul\0password","x".repeat(64),"ü".repeat(256)})for(int rounds:new int[]{1,2,37,1000}){
            char[] chars=password.toCharArray();assertArrayEquals(reference(chars,salt,rounds),ParallelPbkdf2.derive(chars,salt,rounds));assertEquals(password,new String(chars));
        }
        Random random=new Random(7);for(int i=0;i<40;i++){random.nextBytes(salt);char[] password=new char[4+random.nextInt(253)];for(int j=0;j<password.length;j++)password[j]=(char)(32+random.nextInt(90));assertArrayEquals(reference(password,salt,13+i),ParallelPbkdf2.derive(password,salt,13+i));}
    }
    @Test public void cancellationReturnsNoPartialKeyAndRetainsInterrupt()throws Exception{
        Thread.currentThread().interrupt();
        try{assertThrows(java.security.GeneralSecurityException.class,()->ParallelPbkdf2.derive("1234".toCharArray(),new byte[32],210000));assertTrue(Thread.currentThread().isInterrupted());}
        finally{Thread.interrupted();}
    }
}
