package my.LJExport.runtime.lj.login;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpStatus;
import org.apache.http.cookie.Cookie;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.Web;

public class LoginLivejournal
{
    public static void login() throws Exception
    {
        Config.acquireLoginPassword();

        Web.Response r = null;
        // ### MUST reproduce headers EXACTLY or will lock out
        r = Web.get("https://www." + Config.LoginSite + "/");
        if (r.code != HttpStatus.SC_OK)
            throw new Exception("Unable to log into the server: " + Web.describe(r.code));
        
        String auth_token = extractAuthToken(r.binaryBody);

        Util.noop();
        
        
        
        
        StringBuilder sb = new StringBuilder();

        sb.append(Web.escape("ret") + "=" + "1" + "&");
        sb.append(Web.escape("user") + "=" + Web.escape(Config.LoginUser) + "&");
        sb.append(Web.escape("password") + "=" + Web.escape(Config.LoginPassword) + "&");
        sb.append("action:login");
        r = Web.post("https://www." + Config.LoginSite + "/login.bml?ret=1", sb.toString());

        if (r.code != HttpStatus.SC_OK)
            throw new Exception("Unable to log into the server: " + Web.describe(r.code));

        for (Cookie cookie : Web.getCookieStore().getCookies())
        {
            if (!Util.is_in_domain(Config.LoginSite, cookie.getDomain()))
                continue;

            if (cookie.getName().equals("ljmastersession")
                    || cookie.getName().equals("ljloggedin")
                    || cookie.getName().equals("ljsession"))
            {
                return;
            }
        }

        throw new Exception("Unable to log into the server: most probably incorrect username or password");
    }

    /**
     * Extracts LiveJournal sessionless auth_token from HTML byte content.
     *
     * @param htmlBytes raw HTML byte content from https://www.livejournal.com/
     * @return the token string (e.g. "sessionless:...") if exactly one match is found;
     *         null otherwise (none or multiple matches).
     */
    public static String extractAuthToken(byte[] htmlBytes)
    {
        String html = new String(htmlBytes, StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile("\"auth_token\"\\s*:\\s*\"(sessionless:[^\"]+)\"");
        Matcher matcher = pattern.matcher(html);

        String match = null;
        while (matcher.find())
        {
            if (match != null)
            {
                // Multiple matches found, return null
                return null;
            }
            match = matcher.group(1);
        }

        return match; // Either the single match or null if none
    }

    /* ============================================================================================ */

    public static boolean logout(String loginSite) throws Exception
    {
        return LoginLivejournalLegacy.logout(loginSite);
    }

    /* ============================================================================================ */
    
    public static void main(String[] args)
    {
        try
        {
            Config.init("");
            Web.init();
            login();
        }
        catch (Exception ex)
        {
            Util.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }
}
