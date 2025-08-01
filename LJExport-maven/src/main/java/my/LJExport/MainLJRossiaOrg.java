package my.LJExport;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.http.webaction.WebAction;
import my.LJExport.runtime.lj.Sites;

public class MainLJRossiaOrg
{
    private static final String User = "udod99";

    public static void main(String[] args)
    {
        Config.LoginSite = Config.Site = Config.DefaultSite = Sites.RossiaOrg;
        Config.DownloadRoot += ".lj-rossia-org";
        
        if (Util.True)
        {
            Web.InitialActions = getWebActions();
            Config.AutoconfigureSite = true;
        }
        else
        {
            Config.UseLogin = false;
            Config.AutoconfigureSite = false;
        }
        

        Main main = new Main();
        main.do_main(User);
    }

    public static String[][] getWebActions()
    {
        return new String[][] {
                /* login via pre-existing cookies */
                { WebAction.REPEAT, WebAction.UseLogin, "false" }
        };
    }
}
