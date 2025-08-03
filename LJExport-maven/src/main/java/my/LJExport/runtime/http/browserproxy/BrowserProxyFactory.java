package my.LJExport.runtime.http.browserproxy;

import my.LJExport.runtime.http.HttpAccessMode;

public class BrowserProxyFactory
{
    public static BrowserProxy getBrowserProxy(HttpAccessMode httpAccessMode, String url)
    {
        if (httpAccessMode == null || !httpAccessMode.isBrowserProxy())
            return null;
        
        throw new RuntimeException("Proxy is not implemente yet"); //###
    }
    
    public static boolean requiresProgrammaticLogin(String site) throws Exception
    {
        HttpAccessMode httpAccessMode = HttpAccessMode.forUrl(site);
        BrowserProxy browserProxy = BrowserProxyFactory.getBrowserProxy(httpAccessMode, site);
        return browserProxy != null && browserProxy.requiresProgrammaticLogin();
    }
}
