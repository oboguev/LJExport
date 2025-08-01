package my.LJExport;

import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.http.webaction.WebAction;
import my.LJExport.runtime.lj.Sites;

public class MainDreamwidthOrg
{
    private static final String User = "harmfulgrumpy";

    private static boolean useFirefoxCookiesLogin = true;

    public static void main(String[] args)
    {
        if (useFirefoxCookiesLogin)
        {
            Config.LoginSite = Config.Site = Config.DefaultSite = Sites.DreamwidthOrg;
            Config.DownloadRoot += ".dreamwidth-org";
            Web.InitialActions = getWebActions();
            Config.AutoconfigureSite = true;
        }
        else
        {
            Config.LoginSite = Config.Site = Config.DefaultSite = Sites.DreamwidthOrg;
            Config.DownloadRoot += ".dreamwidth-org";
            Config.UseLogin = true;
            Config.AutoconfigureSite = false;
        }

        Main main = new Main();
        main.do_main(User);
    }

    public static String[][] getWebActions()
    {
        return new String[][] {
                /* login via pre-existing cookies */
                { WebAction.REPEAT, WebAction.UseLogin, "false" },
                { WebAction.ONCE, WebAction.LoadFirefoxCookies, "dreamwidth.org" },
                { WebAction.REPEAT, WebAction.LoadFirefoxUserAgent }
        };
    }
}
