package my.LJExport;

public class MainDreamwidthOrg
{
    private static final String User = "harmfulgrumpy";
    
    public static void main(String[] args)
    {
        Config.LoginSite = Config.Site = Config.DefaultSite= "dreamwidth.org";
        Config.UseLogin = true;
        Config.DownloadRoot += ".dreamwidth-org";
        Config.AutoconfigureSite = false;
        
        Main main = new Main();
        main.do_main(User);
    }
}
