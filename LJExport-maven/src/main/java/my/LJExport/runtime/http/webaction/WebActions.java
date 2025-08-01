package my.LJExport.runtime.http.webaction;

import java.util.ArrayList;
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
    public static List<List<String>> history = new ArrayList<>();

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

        List<String> args = Arrays.asList(action);
        if (args.size() < 2)
            throw new Exception("Missing action repeat setting and/or verb");

        /* for removability */
        args = new ArrayList<>(args);

        String repeat = args.remove(0);

        switch (repeat)
        {
        case WebAction.ONCE:
            if (historyContains(args))
                return;
            break;

        case WebAction.REPEAT:
            break;

        default:
            throw new Exception("Incorrect repeat mode in action");
        }

        if (!historyContains(args))
            addHistory(args);

        String verb = args.remove(0);

        switch (verb)
        {
        case WebAction.LoadFirefoxUserAgent:
            loadFirefoxUserAgent(verb, args);
            break;

        case WebAction.LoadFirefoxCookies:
            loadFirefoxCookies(verb, args);
            break;

        case WebAction.UseLogin:
            useLogin(verb, args);
            break;

        default:
            throw new Exception("Unknown action verb: " + verb);
        }
    }

    private static void addHistory(List<String> action)
    {
        history.add(new ArrayList<>(action));
    }

    private static boolean historyContains(List<String> action)
    {
        return history.contains(action);
    }

    public static void clearHistory()
    {
        history.clear();
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

    /* =========================================================================================== */

    private static void useLogin(String verb, List<String> args) throws Exception
    {
        if (args.size() != 1)
            throw new Exception("Invalid arguments for action " + verb);

        boolean useLogin = Boolean.parseBoolean(args.get(0));
        Config.UseLogin = useLogin;
    }
}
