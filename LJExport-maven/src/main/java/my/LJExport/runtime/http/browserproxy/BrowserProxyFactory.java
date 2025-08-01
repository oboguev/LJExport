package my.LJExport.runtime.http.browserproxy;

import my.LJExport.runtime.http.HttpAccessMode;

public class BrowserProxyFactory
{
    public static BrowserProxy getBrowserProxy(HttpAccessMode httpAccessMode, String url)
    {
        if (httpAccessMode == null || !httpAccessMode.isBrowserProxy())
            return null;
        
        // ###
        return null;
    }
}
