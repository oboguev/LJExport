package my.LJExport.runtime.http.webaction;

import java.util.Arrays;
import java.util.List;

import org.apache.http.client.CookieStore;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.browsers.FirefoxCookies;
import my.LJExport.runtime.browsers.FirefoxUserAgent;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.http.cookies.CookieUtil;

public class WebActions
{
    public static void execute(String[][] actions) throws Exception
    {
        if (actions != null)
        {
            for (String[] action : actions)
                execute(action);
        }
    }

    public static void execute(String[] action) throws Exception
    {
        if (action == null)
            return;
        
        // ### ONCE/REPEAT
        // ### record
        
        List<String> args = Arrays.asList(action);
        if (args.size() == 0)
            throw new Exception("Missing action verb");

        String verb = args.remove(0);

        switch (verb)
        {
        case WebAction.LoadFirefoxUserAgent:
            loadFirefoxUserAgent(verb, args);
            break;

        case WebAction.LoadFirefoxCookies:
            loadFirefoxCookies(verb, args);
            break;

        default:
            throw new Exception("Unknown action verb: " + verb);
        }
    }
    
    public static void clearHistory()
    {
        // ####
    }
    
    /* =========================================================================================== */

    private static void loadFirefoxUserAgent(String verb, List<String> args) throws Exception
    {
        if (args.size() != 0)
            throw new Exception("Invalid arguments for action " + verb);

        Config.UserAgent = FirefoxUserAgent.getUserAgent();
        Util.noop();
    }

    /* =========================================================================================== */

    private static void loadFirefoxCookies(String verb, List<String> args) throws Exception
    {
        CookieStore from = FirefoxCookies.loadCookiesFromFirefox();
        CookieStore to = Web.getCookieStore();

        if (args.size() == 0 || args.contains(WebAction.ALL_DOMAINS))
        {
            CookieUtil.copyAllCookies(from, to);
        }
        else
        {
            for (String domain : args)
            {
                CookieUtil.copySelectCookies(from, to, domain);
            }
        }
    }
}
