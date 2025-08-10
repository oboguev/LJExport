package my.LJExport.runtime.url;

import my.LJExport.runtime.Util;

public class FixUrl
{
    public static String fix(String url) throws Exception
    {
        if (url.startsWith("http:///"))
        {
            url = "http://" + Util.stripStart(url, "http:///");
        }

        return url;
    }
}
