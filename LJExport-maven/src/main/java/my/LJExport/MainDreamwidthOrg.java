package my.LJExport;

import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.http.webaction.WebAction;
import my.LJExport.runtime.lj.Sites;

public class MainDreamwidthOrg
{
    private static final String User = "harmfulgrumpy";

    public static void main(String[] args)
    {
        Config.LoginSite = Config.Site = Config.DefaultSite = Sites.DreamwidthOrg;
        Config.UseLogin = false;
        Config.DownloadRoot += ".dreamwidth-org";
        Web.InitialActions = getWebActions();
        Config.AutoconfigureSite = false;

        Main main = new Main();
        main.do_main(User);
    }
    
    public static  String[][] getWebActions()
    {
        return new String[][] {
            /* login via pre-existing cookies */
            { WebAction.ONCE, WebAction.LoadFirefoxCookies, "dreamwidth.org" },
            { WebAction.REPEAT, WebAction.LoadFirefoxUserAgent }
        };
    }
}
