package my.LJExport.runtime.links.util;

import my.LJExport.runtime.url.UrlPatternMatcher;

public class DontDownload
{
    private static UrlPatternMatcher dontDownload;

    public static boolean dontDownload(String href) throws Exception
    {
        synchronized (DontDownload.class)
        {
            if (dontDownload == null)
                dontDownload = UrlPatternMatcher.fromResource("dont-download.txt");
        }

        return dontDownload.contains(href);
    }
}
