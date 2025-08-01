package my.LJExport.runtime.http;

import my.LJExport.Config;
import my.LJExport.runtime.lj.Sites;

public enum HttpAccessMode
{
    /* disable access */
    NO_ACCESS, 

    /* normal direct access via Apache client */
    DIRECT, 

    /* direct access via Apache client, but downgrade HTTPS to HTTP */
    DIRECT_VIA_HTTP, 

    /* route via BrowserProxyNodeJS */
    PROXY_NODEJS, 

    /* route via BrowserProxyCDP */
    PROXY_CDP;

    public static HttpAccessMode forUrl(String url) throws Exception
    {
        String site = Sites.which(url);
        return forSite(site);
    }

    public static HttpAccessMode forSite(String site) throws Exception
    {
        HttpAccessMode mode = Config.HttpAccessModes.get(site);
        if (mode == null)
            mode = HttpAccessMode.DIRECT;
        return mode;
    }

    public boolean isBrowserProxy()
    {
        switch (this)
        {
        case PROXY_NODEJS:
        case PROXY_CDP:
            return true;
            
        default:
            return false;
        }
    }
}
