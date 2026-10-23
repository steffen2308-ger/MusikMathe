package de.steffen.musikmathe;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 8018 PBKDF2-HMAC-SHA256, 96 bytes: independent blocks run on at most three threads.
 * Uses the platform HMAC primitive; rounds, block numbers and output match SecretKeyFactory.
 * All workers finish before shared password bytes can be erased or the result is returned. */
final class ParallelPbkdf2 {
    static byte[] derive(char[] password,byte[] salt,int rounds)throws GeneralSecurityException{
        if(rounds<1)throw new GeneralSecurityException("Invalid PBKDF2 rounds");
        if(Thread.currentThread().isInterrupted())throw new GeneralSecurityException("Password derivation interrupted");
        byte[] encoded;
        try{
            ByteBuffer buffer=StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            encoded=new byte[buffer.remaining()];buffer.get(encoded);
            if(buffer.hasArray())Arrays.fill(buffer.array(),(byte)0);
        }catch(CharacterCodingException invalid){throw new GeneralSecurityException(invalid);}
        byte[] result=new byte[96];Thread[] workers=new Thread[3];Throwable[] errors=new Throwable[3];
        boolean complete=false,interrupted=false;
        try{
            for(int i=0;i<3;i++){
                final int slot=i;
                workers[i]=new Thread(()->{try{block(encoded,salt,rounds,slot+1,result,slot*32);}catch(Throwable error){errors[slot]=error;}},"private-password-"+(i+1));
                workers[i].setDaemon(true);workers[i].start();
            }
            interrupted=join(workers);
            if(interrupted||Thread.currentThread().isInterrupted())throw new GeneralSecurityException("Password derivation interrupted");
            for(Throwable error:errors)if(error!=null){if(error instanceof Error)throw (Error)error;throw new GeneralSecurityException(error);}
            complete=true;return result;
        }finally{
            if(!complete)for(Thread worker:workers)if(worker!=null)worker.interrupt();
            interrupted|=join(workers);
            Arrays.fill(encoded,(byte)0);if(!complete)Arrays.fill(result,(byte)0);
            if(interrupted)Thread.currentThread().interrupt();
        }
    }
    private static boolean join(Thread[] workers){
        boolean interrupted=false;
        for(Thread worker:workers)if(worker!=null){boolean waiting=true;while(waiting)try{worker.join();waiting=false;}catch(InterruptedException stop){interrupted=true;for(Thread other:workers)if(other!=null)other.interrupt();}}
        return interrupted;
    }
    private static void block(byte[] password,byte[] salt,int rounds,int number,byte[] result,int offset)throws GeneralSecurityException{
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(password,"HmacSHA256"));
        byte[] value=null,sum=null;
        try{
            mac.update(salt);value=mac.doFinal(new byte[]{0,0,0,(byte)number});sum=value.clone();
            for(int round=1;round<rounds;round++){
                if((round&1023)==0&&Thread.currentThread().isInterrupted())throw new GeneralSecurityException("Password derivation interrupted");
                mac.update(value);mac.doFinal(value,0);
                for(int i=0;i<32;i++)sum[i]^=value[i];
            }
            System.arraycopy(sum,0,result,offset,32);
        }finally{if(value!=null)Arrays.fill(value,(byte)0);if(sum!=null)Arrays.fill(sum,(byte)0);}
    }
}
