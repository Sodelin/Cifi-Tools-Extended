package com.cifi.assistant;

/** Strict HTTPS authority parsing shared by navigation and site operations. */
final class CifiOrigin {
    private CifiOrigin() {}
    static java.net.URI httpsUri(String value) {
        if(value==null)return null;
        try {
            java.net.URI parsed=new java.net.URI(value);String authority=parsed.getRawAuthority();
            if(!"https".equalsIgnoreCase(parsed.getScheme())||parsed.isOpaque()||parsed.getHost()==null||
               parsed.getRawUserInfo()!=null||authority==null||authority.endsWith(":")||parsed.getPort()>65535)return null;
            return parsed;
        } catch(java.net.URISyntaxException error){return null;}
    }
    static boolean trusted(java.net.URI url) {
        return url!=null && "cifi-tools.com".equalsIgnoreCase(url.getHost()) && (url.getPort()==-1||url.getPort()==443);
    }
}
