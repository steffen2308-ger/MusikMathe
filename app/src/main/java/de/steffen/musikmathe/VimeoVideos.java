package de.steffen.musikmathe;

import android.net.Uri;

/** Only video pages and the actual HTTPS Vimeo player can be opened from a marker. */
final class VimeoVideos {
    private VimeoVideos(){}
    static String playerUrl(String value){
        try{
            if(value==null||value.length()>8192)return "";
            Uri url=Uri.parse(value);String host=url.getHost(),path=url.getPath();
            if(!"https".equalsIgnoreCase(url.getScheme())||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443)||host==null||path==null)return "";
            host=host.toLowerCase(java.util.Locale.ROOT);
            String id="",hash=url.getQueryParameter("h");
            if("player.vimeo.com".equals(host)){
                if(!path.matches("/video/[0-9]{1,15}/?"))return "";
                if(hash!=null&&!hash.isEmpty()&&!hash.matches("[A-Za-z0-9_-]{1,128}"))return "";
                // The site may supply signed context, privacy and playback parameters.
                // Preserve their encoded bytes; never reconstruct or invent a player signature.
                return url.buildUpon().fragment(null).build().toString();
            }else if("vimeo.com".equals(host)||"www.vimeo.com".equals(host)){
                java.util.regex.Matcher match=java.util.regex.Pattern.compile("^/(?:channels/[^/]+/|groups/[^/]+/videos/|(?:showcase|album)/[0-9]+/video/)?([0-9]{1,15})(?:/([A-Fa-f0-9]{6,64}))?/?$").matcher(path);
                if(!match.matches())return "";id=match.group(1);if(match.group(2)!=null)hash=match.group(2);
            }else return "";
            Uri.Builder player=new Uri.Builder().scheme("https").authority("player.vimeo.com").appendPath("video").appendPath(id);
            if(hash!=null&&!hash.isEmpty()){
                if(!hash.matches("[A-Za-z0-9_-]{1,128}"))return "";
                player.appendQueryParameter("h",hash);
            }
            return player.build().toString();
        }catch(RuntimeException ignored){return "";}
    }
}
