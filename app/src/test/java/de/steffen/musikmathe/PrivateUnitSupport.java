package de.steffen.musikmathe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import org.json.JSONObject;

/** A previously authenticated session for UI unit tests; device tests exercise the real gate. */
final class PrivateUnitSupport {
    static PrivateProfiles.Session session(android.content.Context context)throws Exception{
        PrivateProfiles.salt(context);byte[] key=new byte[96];new SecureRandom().nextBytes(key);
        return new PrivateProfiles.Session(context,PrivateCrypto.id(key),key,new JSONObject().put("schema",1),true);
    }
    static void authenticated(MainActivity activity)throws Exception{
        PrivateProfiles.active=session(activity);
        Field access=MainActivity.class.getDeclaredField("privateAccess");access.setAccessible(true);PrivateAccess gate=(PrivateAccess)access.get(activity);gate.destroy();Field flag=PrivateAccess.class.getDeclaredField("locked");flag.setAccessible(true);flag.setBoolean(gate,false);
        Method initialize=MainActivity.class.getDeclaredMethod("initializeBrowser",android.os.Bundle.class);initialize.setAccessible(true);initialize.invoke(activity,new Object[]{null});
    }
}
