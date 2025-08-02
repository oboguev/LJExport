package my.LJExport.runtime.lj.login;

import static my.LJExport.runtime.Util.out;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.http.HttpStatus;
import org.apache.http.cookie.Cookie;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.FormPost;
import my.LJExport.runtime.http.Web;

public class LoginLivejournalLegacy
{
    /*
     * This login method used to work till July 2025
     * and now causes IP address to be blocked for 10-15 minutes
     */
    public static void login() throws Exception
    {
        Config.acquireLoginPassword();

        Web.Response r = null;
        
        Map<String,String> form = new LinkedHashMap<>(); 
        form.put("ret", "1");
        form.put("user", Config.LoginUser);
        form.put("password", Config.LoginPassword);
        r = Web.post("https://www." + Config.LoginSite + "/login.bml?ret=1", 
                FormPost.body(form) + "&" + "action:login",
                Map.of("Content-Type", "application/x-www-form-urlencoded"));

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

    /* ============================================================================================ */

    public static boolean logout(String loginSite) throws Exception
    {
        String sessid = null;

        for (Cookie cookie : Web.getCookieStore().getCookies())
        {
            if (!Util.is_in_domain(loginSite, cookie.getDomain()))
                continue;

            if (cookie.getName().equals("ljmastersession") || cookie.getName().equals("ljloggedin")
                    || cookie.getName().equals("ljsession"))
            {
                StringTokenizer st = new StringTokenizer(cookie.getValue(), ":");

                while (st.hasMoreTokens())
                {
                    String tok = st.nextToken();
                    if (tok.length() >= 2 && tok.charAt(0) == 's')
                    {
                        sessid = tok.substring(1);
                        break;
                    }
                }

                if (sessid != null)
                    break;
            }
        }

        if (sessid == null)
        {
            out(">>> Unable to log off the server (unknown sessid)");
            return false;
        }

        out(">>> Logging off " + loginSite);
        StringBuilder sb = new StringBuilder();
        Web.Response r = null;

        sb.append("http://www." + loginSite + "/logout.bml?ret=1&user=" + Config.LoginUser + "&sessid=" + sessid);
        try
        {
            r = Web.get(sb.toString());
        }
        catch (Exception ex)
        {
            Util.noop();
        }

        if (r != null && r.code == HttpStatus.SC_OK)
        {
            out(">>> Logged off " + loginSite);
            return true;
        }
        else
        {
            out(">>> Loggoff unsuccessful from " + loginSite);
            return false;
        }
    }
}