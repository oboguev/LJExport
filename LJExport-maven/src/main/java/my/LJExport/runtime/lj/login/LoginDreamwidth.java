package my.LJExport.runtime.lj.login;

import org.apache.http.HttpStatus;
import org.apache.http.cookie.Cookie;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.FormPost;
import my.LJExport.runtime.http.Web;
import static my.LJExport.runtime.Util.out;

import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class LoginDreamwidth
{
    public static void login() throws Exception
    {
        Config.acquireLoginPassword();

        Web.Response r = null;

        out(String.format(">>> Using %s login captcha challenge code %s", Config.LoginSite, Config.DreamwidthCaptchaChallenge));

        Map<String,String> form = new LinkedHashMap<>(); 
        form.put("returnto", "https://www.dreamwidth.org/");
        form.put("chal", Config.DreamwidthCaptchaChallenge);
        form.put("response", "");
        form.put("user", Config.LoginUser);
        form.put("password", Config.LoginPassword);
        form.put("remember_me", "1");
        form.put("login", "Log in");
        r = Web.post("https://www." + Config.LoginSite + "/login.bml?ret=1", FormPost.body(form));

        if (r.code != 302)
            throw new Exception("Unable to log into the server: " + Web.describe(r.code));

        if (r.redirectLocation == null || !r.redirectLocation.equals("https://www.dreamwidth.org/"))
            throw new Exception("Unable to log into the server, unexpected post-login rediect URL");

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

                if (sessid == null)
                {
                    /*
                     * DreamWidth specific
                     */
                    String urlDecodedCookie = URLDecoder.decode(cookie.getValue(), "UTF-8");
                    st = new StringTokenizer(urlDecodedCookie, ":");

                    while (st.hasMoreTokens())
                    {
                        String tok = st.nextToken();
                        if (tok.length() >= 2 && tok.charAt(0) == 's')
                        {
                            sessid = tok.substring(1);
                            break;
                        }
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
        Web.Response r = null;

        // form.put("lj_form_auth", "c0:1751749200:98:86400:0vrUQLNDDF-3528051-7:81a4e8d76d221ba067aa15f83b26a0e7");
        Map<String,String> form = new LinkedHashMap<>(); 
        form.put("returnto", "https://www.dreamwidth.org/");
        form.put("ret", "1");
        form.put("logout_one", "Log out");
        form.put("user", Config.LoginUser);
        form.put("sessid", sessid);

        try
        {
            r = Web.post("http://www." + loginSite + "/logout", FormPost.body(form));
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
