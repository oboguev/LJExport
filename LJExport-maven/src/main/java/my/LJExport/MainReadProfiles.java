package my.LJExport;

import java.io.File;
import java.util.StringTokenizer;

import my.LJExport.profile.ReadProfile;
import my.LJExport.runtime.HttpWireTracing;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.RateLimiter;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.synch.ThreadsControl;

public class MainReadProfiles
{
    // private static final String Users = "oboguev";
    private static final String Users = "fritzmorgen,oboguev";

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
            // HttpWireTracing.enable();
            do_users(Users);
        }
        catch (Exception ex)
        {
            Util.err("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
            Main.emergency_logout();
        }

        Main.playCompletionSound();
    }

    private static void do_users(String users) throws Exception
    {
        Config.init("");
        Web.init();

        /* login may be required for pictures marked 18+ */
        Main.do_login();

        RateLimiter.LJ_IMAGES.setRateLimit(100);

        StringTokenizer st = new StringTokenizer(users, ", \t\r\n");
        int nuser = 0;

        while (st.hasMoreTokens() && !Main.isAborting())
        {
            String user = st.nextToken();
            user = user.trim().replace("\t", "").replace(" ", "");
            if (user.equals(""))
                continue;

            if (nuser++ != 0)
            {
                Util.out("");
                Util.out("=====================================================================================");
                Util.out("");
            }

            MainReadProfiles self = new MainReadProfiles();
            self.do_user(user);
        }

        Main.do_logout();
        Web.shutdown();
    }

    private void do_user(String user) throws Exception
    {
        try
        {
            Config.User = user;
            Config.mangleUser();

            final String userRoot = Config.DownloadRoot + File.separator + Config.User;
            // final String pagesDir = userRoot + File.separator + "pages";
            final String linksDir = userRoot + File.separator + "links";

            Util.out(">>> Downloading profile for user " + Config.User);

            Util.mkdir(linksDir);
            LinkDownloader.init(linksDir);
            
            new ReadProfile().readAll();

            if (Main.isAborting())
                Main.err(">>> Aborted loading profile for user " + Config.User);
            else
                Main.out(">>> Completed loading profile for user " + Config.User);
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }
}
