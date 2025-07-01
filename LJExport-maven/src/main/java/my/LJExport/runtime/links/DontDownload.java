package my.LJExport.runtime.links;

import java.util.Set;

import my.LJExport.runtime.Util;

public class DontDownload
{
    private static Set<String> dontDownload;
    
    public static boolean dontDownload(String href) throws Exception
    {
        synchronized (LinkDownloader.class)
        {
            if (dontDownload == null)
                dontDownload = Util.read_set("dont-download.txt");
        }

        String flip = Util.flipProtocol(href);
        if (dontDownload.contains(href) || dontDownload.contains(flip))
            return true;

        for (String dont : dontDownload)
        {
            if (dont.endsWith("/*") || dont.endsWith("?*"))
            {
                dont = Util.stripTail(dont, "*");
                if (href.startsWith(dont) || flip.startsWith(dont))
                    return true;
            }
        }
        
        return false;
    }
}
