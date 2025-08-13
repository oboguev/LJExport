package my.LJExport.runtime.lj;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import my.LJExport.Config;
import my.LJExport.runtime.Util;

public class LJUtil
{
    /*
     * Check if URL belongs to the site's server
     */
    public static boolean isServerUrl(String url) throws Exception
    {
        if (isServerUrl(url, Config.Site))
            return true;

        for (String site : Config.AllowedUrlSites)
        {
            if (!site.equalsIgnoreCase(Config.Site) && isServerUrl(url, site))
                return true;
        }

        return false;
    }

    private static boolean isServerUrl(String url, String site) throws Exception
    {
        int k;

        url = Util.stripProtocol(url);

        // strip port number and everything afterwards
        k = url.indexOf(':');
        if (k != -1)
            url = url.substring(0, k);

        // strip local URI and everything afterwards
        k = url.indexOf('/');
        if (k != -1)
            url = url.substring(0, k);

        // strip parameters
        k = url.indexOf('?');
        if (k != -1)
            url = url.substring(0, k);

        // strip anchor
        k = url.indexOf('#');
        if (k != -1)
            url = url.substring(0, k);

        int lu = url.length();
        int ls = site.length();
        if (lu < ls)
        {
            return false;
        }
        else if (lu == ls)
        {
            return url.equalsIgnoreCase(site);
        }
        else
        {
            return url.substring(lu - ls).equalsIgnoreCase(site) &&
                    url.charAt(lu - ls - 1) == '.' &&
                    url.charAt(0) != '.';
        }
    }

    public static boolean isLivejournal(String url) throws Exception
    {
        String host = new URL(url).getHost();
        if (host == null)
            return false;
        host = host.trim().toLowerCase();

        return host.equals("livejournal.com") || host.endsWith(".livejournal.com") ||
                host.equals("livejournal.net") || host.endsWith(".livejournal.net");
    }

    /*
     * Check if @url is what should be after a login.
     * Used only by Selenium.
     */
    public static boolean isValidPostLoginUrl(String url) throws Exception
    {
        if (Config.StandaloneSite)
            throw new Exception("Not implemented");

        if (isJournalUrl(url))
            return true;
        url = Util.stripProtocol(url);
        url = Util.stripLastChar(url, '/');
        if (url.equalsIgnoreCase(Config.Site) || url.equalsIgnoreCase("www." + Config.Site))
            return true;
        return false;
    }

    /*
     * Check if @url belongs to the user's journal. 
     * If yes, strip the journal prefix, store the result in @sb and return @true. 
     * If no, return @false and the value of @sb is unpredictable.
     * 
     *     aleksei.livejournal.com/profile/ => profile/
     *     aleksei.livejournal.com/calendar => calendar
     *     aleksei.livejournal.com/ => (empty)
     *     https://olegmakarenko.ru/calendar/ => calendar
     *     https://olegmakarenko.ru/profile/ => profile/
     */
    static boolean isJournalUrl(String url) throws Exception
    {
        return isJournalUrl(url, new StringBuilder());
    }

    public static boolean isJournalUrl(String url, StringBuilder sb) throws Exception
    {
        if (url.startsWith("/"))
        {
            // e.g. /2532366.html?thread=474097422&format=light
            sb.setLength(0);
            sb.append(url.substring(1));
            return true;
        }

        url = Util.stripProtocol(url);

        if (Config.StandaloneSite)
        {
            String pattern = Config.Site;
            if (Util.beginsWithPath(url, pattern, sb))
                return true;
        }
        else
        {
            String pattern = Config.MangledUser + "." + Config.Site;
            if (Util.beginsWithPath(url, pattern, sb))
                return true;

            pattern = Config.Site + "/users/" + Config.User;
            if (Util.beginsWithPath(url, pattern, sb))
                return true;
            if (Util.beginsWithPath(url, "www." + pattern, sb))
                return true;

            pattern = "users." + Config.Site + "/" + Config.User;
            if (Util.beginsWithPath(url, pattern, sb))
                return true;
        }

        return false;
    }

    /*
     * Check if @url belongs is a record in the user's journal. 
     * If yes, strip the journal prefix, store the result in @sb and return @true.
     * If no, return @false and the value of @sb is unpredictable.
     * 
     *     https://aleksei.livejournal.com/123.html => 123.html
     *     https://olegmakarenko.ru/123.html => 123.html
     */
    public static boolean isJournalRecordUrl(String url, StringBuilder sb) throws Exception
    {
        if (!isJournalUrl(url, sb))
            return false;
        url = Util.stripParameters(sb.toString());
        int len = url.length();
        if (len == 0)
            return false;

        int k = 0;
        while (k < len && Character.isDigit(url.charAt(k)))
            k++;
        if (k == 0)
            return false;

        url = url.toLowerCase();
        if (url.substring(k).equalsIgnoreCase(".html"))
        {
            sb.setLength(0);
            sb.append(url.toLowerCase());
            return true;
        }

        return false;
    }

    /*
     * Used only by Selenium
     */
    public static boolean isLoginPageURL(String url) throws Exception
    {
        if (Config.StandaloneSite)
            throw new Exception("Not implemented");

        url = Util.stripProtocol(url);
        StringBuilder sb = new StringBuilder();
        if (Util.beginsWith(url, Config.Site, sb) ||
                Util.beginsWith(url, "www." + Config.Site, sb))
        {
            url = sb.toString();
            return url.equals("/login.bml");
        }
        else
        {
            return false;
        }
    }

    /*
     * Used only by Selenium
     */
    public static boolean isLogoutURL(String url, StringBuilder sb) throws Exception
    {
        if (Config.StandaloneSite)
            throw new Exception("Not implemented");

        url = Util.stripProtocol(url);

        if (url.startsWith("/logout.bml?"))
        {
            // got it
        }
        else if (Util.beginsWith(url, Config.Site, sb) ||
                Util.beginsWith(url, "www." + Config.Site, sb))
        {
            url = sb.toString();
            if (!url.startsWith("/logout.bml?"))
                return false;
        }
        else
        {
            return false;
        }

        sb.setLength(0);
        sb.append("http://" + Config.Site + url);
        return true;
    }

    /*
     * https://aleksei.livejournal.com/123.html
     * https://olegmakarenko.ru/123.html
     */
    public static String recordPageURL(String rurl)
    {
        if (Config.isRossiaOrg())
        {
            return userBase() + "/" + rurl;
        }
        else if (Config.StandaloneSite)
        {
            return "https://" + Config.Site + "/" + rurl;
        }
        else
        {
            return "https://" + Config.MangledUser + "." + Config.Site + "/" + rurl;
        }
    }

    public static String rpcCommentsForPage(String rid, int npage)
    {
        String url = null;

        if (Config.StandaloneSite)
        {
            url = String.format("http://%s/%s/__rpc_get_thread?journal=%s&itemid=%s&skip=&media=&page=%d&expand_all=1",
                    Config.Site,
                    Config.MangledUser,
                    Config.User,
                    rid,
                    npage);
        }
        else
        {
            url = String.format("http://%s.%s/%s/__rpc_get_thread?journal=%s&itemid=%s&skip=&media=&page=%d&expand_all=1",
                    Config.MangledUser,
                    Config.Site,
                    Config.MangledUser,
                    Config.User,
                    rid,
                    npage);
        }

        return url;
    }

    public static String rpcCommentsForThread(String rid, String thread)
    {
        String url = null;

        if (Config.StandaloneSite)
        {
            url = String.format("http://%s/%s/__rpc_get_thread?journal=%s&itemid=%s&skip=&media=&thread=%s&expand_all=1",
                    Config.Site,
                    Config.MangledUser,
                    Config.User,
                    rid,
                    thread);
        }
        else
        {
            url = String.format("http://%s.%s/%s/__rpc_get_thread?journal=%s&itemid=%s&skip=&media=&thread=%s&expand_all=1",
                    Config.MangledUser,
                    Config.Site,
                    Config.MangledUser,
                    Config.User,
                    rid,
                    thread);
        }

        return url;
    }

    /*
     * User base HTTP
     * 
     *     http://pioneer-lj.livejorunal.com
     *     http://users.livejorunal.com/_devol_
     *     http://olegmakarenko.ru
     */
    public static String userBase()
    {
        if (Config.isRossiaOrg())
        {
            return "https://lj.rossia.org/users/" + Config.User;
        }
        else if (Config.StandaloneSite)
        {
            return "https://" + Config.Site;
        }
        else if (Config.User.charAt(0) == '_' && Util.lastChar(Config.User) == '_')
        {
            return "https://users." + Config.Site + "/" + Config.User;
        }
        else
        {
            return "https://" + Config.MangledUser + "." + Config.Site;
        }
    }

    /* ======================================================================================== */

    /**
     * Attempts to extract the original image URL from a LiveJournal proxy URL.
     * If the proxy URL contains "?" or "#" characters, throws an exception to alert for manual handling.
     *
     * @param url the possibly proxied image URL
     * @return the original image URL if detected, or the input URL otherwise
     * @throws IllegalArgumentException if the URL contains '?' or '#' and appears to be an imgprx proxy URL
     * 
     * Decodes https://imgprx.livejournal.net/st/a5qxcIzYm6irxnS1DFddEl8-2t1CYWAJYj-i7bfFaKo/img1.labirint.ru/books/168777/big.jpg
     * into    https://img1.labirint.ru/books/168777/big.jpg
     */
    public static String decodeImgPrxStLink(String url) throws Exception
    {
        if (url == null)
            return null;

        try
        {
            URL parsedUrl = new URL(url);
            String host = parsedUrl.getHost();
            if (host == null || !host.equalsIgnoreCase("imgprx.livejournal.net"))
                return url;

            String path = parsedUrl.getPath(); // starts with /
            if (path == null || path.isEmpty())
                return url;

            String[] components = path.split("/");

            if (components.length < 3)
                return url; // need at least /st/key/...

            // Locate original host and path
            for (int i = 2; i < components.length; i++)
            {
                if (looksLikeHost(components[i]))
                {
                    List<String> originalParts = new ArrayList<>();
                    for (int j = i; j < components.length; j++)
                    {
                        originalParts.add(components[j]);
                    }

                    String reconstructed = String.join("/", originalParts);
                    // reconstructed = decodePercent(reconstructed);
                    StringBuilder finalUrl = new StringBuilder("https://").append(reconstructed);

                    String query = parsedUrl.getQuery();
                    if (query != null && !query.isEmpty())
                        finalUrl.append('?').append(query);

                    String fragment = parsedUrl.getRef();
                    if (fragment != null && !fragment.isEmpty())
                        finalUrl.append('#').append(fragment);

                    return finalUrl.toString();
                }
            }
        }
        catch (MalformedURLException ex)
        {
            return url;
        }
        catch (Exception ex)
        {
            throw new Exception("Unable to decode LiveJournal image proxy URL " + url, ex);
        }

        return url;
    }

    private static boolean looksLikeHost(String s)
    {
        // Must contain at least one dot and only valid hostname characters
        if (s == null || !s.contains("."))
            return false;
        return s.matches("(?i)[a-z0-9.-]+");
    }
}
