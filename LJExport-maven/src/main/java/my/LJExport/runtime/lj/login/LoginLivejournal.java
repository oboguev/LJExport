package my.LJExport.runtime.lj.login;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpStatus;
import org.apache.http.cookie.Cookie;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.HttpWireTracing;
import my.LJExport.runtime.http.Web;

public class LoginLivejournal
{
    public static void login() throws Exception
    {
        Config.acquireLoginPassword();

        Web.Response r = null;
        r = Web.get("https://www." + Config.LoginSite + "/");
        if (r.code != HttpStatus.SC_OK)
            throw new Exception("Unable to log into the server: " + Web.describe(r.code));

        // sessionless:1753822800:/__api/::321e17dd8605d07193060b73e367f1a80daa8277
        String auth_token = extractAuthToken(r.binaryBody);
        String postBody = String.format(
                "[{\"jsonrpc\":\"2.0\",\"method\":\"user.login\",\"params\":{\"user\":\"%s\",\"password\":\"%s\",\"expire\":\"never\",\"auth_token\":\"%s\"},\"id\":%s}]",
                Config.LoginUser,
                Config.LoginPassword,
                auth_token,
                "20"); // request id

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("Content-Type", "text/plain");
        headers.put("Sec-Fetch-Dest", "empty");
        headers.put("Sec-Fetch-Mode", "cors");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("Referer", String.format("https://www.%s/", Config.LoginSite));
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Origin", String.format("https://www.%s", Config.LoginSite));
        
        r = Web.post("https://www." + Config.LoginSite + "/_api/", 
                postBody,
                headers);
        
        // responds with JSON and cookies
        // [{"jsonrpc":"2.0","id":20,"result":{"status":"OK"}}]
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
     * @param htmlBytes
     *            raw HTML byte content from https://www.livejournal.com/
     * @return the token string (e.g. "sessionless:...") if exactly one match is found; null otherwise (none or multiple matches).
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
            if (Config.False)
            {
                Config.LoginPassword = "xxxzzz";
                Config.LoginSite = "google.com";
                Config.AutoconfigureSite = false;
                Config.UseLogin = true;
            }

            HttpWireTracing.enable();
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
