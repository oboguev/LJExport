package my.LJExport.runtime.lj;

import java.net.URL;

import my.LJExport.runtime.Util;

public class Sites
{
    public static final String Livejournal = "livejournal.com";
    public static final String RossiaOrg = "lj.rossia.org";
    public static final String DreamwidthOrg = "dreamwidth.org";
    public static final String OlegMakarenko = "olegmakarenko.ru";

    public static final String ArchiveOrg = "archive.org";
    public static final String Other = "--other--";

    private static final String[] all = { Livejournal, DreamwidthOrg, RossiaOrg, ArchiveOrg, OlegMakarenko };

    public static String which(String url) throws Exception
    {
        if (!Util.startsWithIgnoreCase(url, null, "http://", "https://"))
            url = "https://" + url;

        URL xurl = new URL(url);
        String host = xurl.getHost();
        if (host != null)
            host = host.trim().toLowerCase();
        if (host == null || host.length() == 0)
            throw new Exception("Missing host name in URL " + url);

        if (host.equals("livejournal.net") || host.endsWith(".livejournal.net"))
            return Livejournal;

        for (String site : all)
        {
            if (host.equals(site) || host.endsWith("." + site))
                return site;
        }

        return Other;
    }
}
