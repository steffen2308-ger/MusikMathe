package de.steffen.musikmathe;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative page-link policy. GET is not a guarantee that a server has no side effects. */
final class PreloadLinks {
    static final int MAX_LINKS=512;
    private static final Pattern ACTION=Pattern.compile("(?i)(^|[^a-z])(logout|logoff|signout|signoff|abmelden|ausloggen|delete|remove|loeschen|löschen|unsubscribe|unfollow|checkout|purchase|buy|kaufen|cart|warenkorb|basket|submit|confirm|revoke|reset|activate|deactivate|download|export|token|csrf|nonce|auth|oauth|login|signin|anmelden|payment|pay|vote|like|unlike)([^a-z]|$)");
    static String eligible(String current,String href,String label,boolean download,String rel){
        if(download||href==null||href.length()>4096||rel!=null&&rel.matches("(?is).*(?:\\bsponsored\\b|\\bnofollow\\b).*"))return "";
        try{
            URI u=new URI(href);if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getPort()>65535)return "";
            String host=u.getHost().toLowerCase(Locale.ROOT);if(host.equals("musikmathe.invalid"))return "";
            String path=u.getPath()==null?"":u.getPath();String query=u.getRawQuery()==null?"":URLDecoder.decode(u.getRawQuery(),"UTF-8");
            if(ACTION.matcher(path+" "+query+" "+(label==null?"":label)).find()||path.toLowerCase(Locale.ROOT).matches(".*(?:^|/)api(?:/|$).*"))return "";
            String last=path.substring(path.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
            if(last.contains(".")&&!last.matches(".*\\.(?:html?|xhtml|php|aspx?|jsp)$"))return "";
            // Preserve percent escapes verbatim while removing only the fragment.
            String result;
            int fragment=href.indexOf('#');result=fragment<0?href:href.substring(0,fragment);
            int currentFragment=current==null?-1:current.indexOf('#');String source=currentFragment<0?current:current.substring(0,currentFragment);
            return result.equals(source)?"":result;
        }catch(Exception invalid){return "";}
    }
}
