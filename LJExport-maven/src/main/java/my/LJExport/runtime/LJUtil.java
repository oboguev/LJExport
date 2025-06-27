package my.LJExport.runtime;

import my.LJExport.Config;

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
        if (Config.StandaloneSite)
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
}
