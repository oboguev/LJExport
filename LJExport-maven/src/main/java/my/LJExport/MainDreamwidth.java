package my.LJExport;

public class MainDreamwidth
{
    private static final String User = "harmfulgrumpy";
    
    public static void main(String[] args)
    {
        Config.Site = Config.DefaultSite= "dreamwidth.org";
        Config.UseLogin = false;
        Config.DownloadRoot += ".dreamwidth";
        
        Main main = new Main();
        main.do_main(User);
    }

}
