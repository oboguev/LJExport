package my.LJExport.runtime.links.util;

import java.util.Set;

import my.LJExport.runtime.Util;

public class DontDownload
{
    private static Set<String> dontDownload;
    
    public static boolean dontDownload(String href) throws Exception
    {
        synchronized (DontDownload.class)
        {
            if (dontDownload == null)
                dontDownload = Util.read_set("dont-download.txt");
        }
        
        String np = Util.stripProtocol(href);

        if (dontDownload.contains(np))
            return true;

        for (String dont : dontDownload)
        {
            if (dont.endsWith("/*") || dont.endsWith("?*"))
            {
                dont = Util.stripTail(dont, "*");
                if (np.startsWith(dont))
                    return true;
            }
        }
        
        return false;
    }
}
