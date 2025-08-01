package my.LJExport.runtime.http;

import my.LJExport.Config;
import my.LJExport.runtime.lj.Sites;

public enum HttpAccessMode
{
    DIRECT, NO_ACCESS, PROXY_NODEJS, PROXY_CDP;

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
