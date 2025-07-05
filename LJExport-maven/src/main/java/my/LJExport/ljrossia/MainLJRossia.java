package my.LJExport.ljrossia;

import my.LJExport.Config;
import my.LJExport.Main;

public class MainLJRossia
{
    private static final String User = "udod99";
    
    public static void main(String[] args)
    {
        Config.Site = Config.DefaultSite= "lj.rossia.org";
        Config.UseLogin = false;
        Config.DownloadRoot += ".ljrossia";
        
        Main main = new Main();
        main.do_main(User);
    }
}
