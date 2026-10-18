package de.steffen.musikmathe;
import android.net.Uri;
import android.webkit.CookieManager;
import androidx.media3.datasource.DataSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
@android.annotation.SuppressLint("UnsafeOptInUsageError")
public class VideoHttpSourceTest {
    @Test public void redirectsRecomputeCookiesAndRemoveCrossOriginAuthorization()throws Exception{
        try(ServerSocket server=new ServerSocket(0)){
            List<String> received=Collections.synchronizedList(new ArrayList<>());int port=server.getLocalPort();
            Thread responder=new Thread(()->{try{for(int i=0;i<2;i++)try(Socket socket=server.accept()){
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));StringBuilder request=new StringBuilder();String line;while((line=reader.readLine())!=null&&!line.isEmpty())request.append(line).append("\n");received.add(request.toString());
                String response=i==0?"HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:"+port+"/final\r\nContent-Length: 0\r\nConnection: close\r\n\r\n":"HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\nabcdef";
                socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            }}catch(Exception error){throw new RuntimeException(error);}});responder.setDaemon(true);responder.start();
            CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().setCookie("http://localhost:"+port+"/","source=secret");CookieManager.getInstance().setCookie("http://127.0.0.1:"+port+"/","target=good");
            String url="http://localhost:"+port+"/redirect";Map<String,String> headers=new LinkedHashMap<>();headers.put("Authorization","Bearer secret");VideoHttpSource source=new VideoHttpSource(url,headers);
            try{source.open(new DataSpec.Builder().setUri(Uri.parse(url)).setPosition(2).setLength(3).build());byte[] buffer=new byte[10];assertEquals(3,source.read(buffer,0,10));assertEquals("cde",new String(buffer,0,3,StandardCharsets.US_ASCII));assertEquals(-1,source.read(buffer,0,10));}finally{source.close();}
            responder.join(2000);assertEquals(2,received.size());assertTrue(received.get(0).contains("Bearer secret"));assertFalse(received.get(1).contains("Bearer secret"));assertFalse(received.get(1).contains("source=secret"));assertTrue(received.get(1).contains("target=good"));
        }
    }
}
