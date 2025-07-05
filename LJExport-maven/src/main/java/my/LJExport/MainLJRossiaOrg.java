package my.LJExport;

public class MainLJRossiaOrg
{
    private static final String User = "udod99";
    
    public static void main(String[] args)
    {
        Config.Site = Config.DefaultSite= "lj.rossia.org";
        Config.UseLogin = false;
        Config.DownloadRoot += ".lj-rossia-org";
        
        Main main = new Main();
        main.do_main(User);
    }
}
