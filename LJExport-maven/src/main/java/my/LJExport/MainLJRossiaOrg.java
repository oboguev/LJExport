package my.LJExport;

import my.LJExport.runtime.lj.Sites;

public class MainLJRossiaOrg
{
    private static final String User = "udod99";
    
    public static void main(String[] args)
    {
        Config.Site = Config.DefaultSite= Sites.RossiaOrg;
        Config.UseLogin = false;
        Config.DownloadRoot += ".lj-rossia-org";
        Config.AutoconfigureSite = false;
        
        Main main = new Main();
        main.do_main(User);
    }
}
