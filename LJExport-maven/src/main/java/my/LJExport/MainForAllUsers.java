package my.LJExport;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

import my.LJExport.monthly.BuildNavigationIndex;
import my.LJExport.monthly.InsertNavigationControls;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.synch.ThreadsControl;

public class MainForAllUsers
{
    private static final String ALL_USERS = "<all>";
    // private static final String AllUsersFromUser = "kot_begemott";
    private static final String AllUsersFromUser = null;

    private static final String Users = ALL_USERS;
    // private static final String Users = "oboguev";
    // private static final String Users = "harmfulgrumpy.dreamwidth-org,udod99.lj-rossia-org";
    // private static final String Users = "d_olshansky.ljsearch";

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
            // HttpWireTracing.enable();

            do_users(Users);

            Util.out("");
            Util.out(">>> Completed for all requested users");
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
        /* can be set in debugger */
        boolean forceExitNow = false;

        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.init("");

        if (Config.False)
        {
            Web.init();
            Main.do_login();
            RateLimiter.LJ_IMAGES.setRateLimit(100);
        }

        StringTokenizer st = new StringTokenizer(users, ", \t\r\n");
        int nuser = 0;

        while (st.hasMoreTokens() && !Main.isAborting())
        {
            String user = st.nextToken();
            user = user.trim().replace("\t", "").replace(" ", "");
            if (user.equals(""))
                continue;

            if (Main.isAborting())
                break;

            if (Config.False)
            {
                Main.out(user);
                continue;
            }

            /* forced exit through debugger */
            Util.unused(forceExitNow);
            if (forceExitNow)
                break;

            if (nuser++ != 0)
            {
                Util.out("");
                Util.out("=====================================================================================");
                Util.out("");
            }

            try
            {
                MainForAllUsers self = new MainForAllUsers();
                self.do_user(user);
            }
            finally
            {
                ThreadsControl.shutdownAfterUser();
            }
        }

        if (Config.False)
        {
            Main.do_logout();
            Web.shutdown();
        }
    }

    private void do_user(String user) throws Exception
    {
        try
        {
            Config.User = user;
            Config.mangleUser();
            Config.autoconfigureSite();

            // do_user_actual(user);
            // do_user_monthly_index_html(user);
            do_user_monthly_insert_nav_controls(user);
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    @SuppressWarnings("unused")
    private void do_user_actual(String user) throws Exception
    {
        Util.out(">>> Making task for user " + Config.User);
    }

    /* ======================================================================= */

    @SuppressWarnings("unused")
    private void do_user_monthly_index_html(String user) throws Exception
    {
        Util.out(">>> Making monthly index.html files for user " + Config.User);
        do_user_monthly_index_html(user, "monthly-pages");
        do_user_monthly_index_html(user, "monthly-reposts");
    }

    private void do_user_monthly_index_html(String user, String which) throws Exception
    {
        String monthlyRootDir = Config.DownloadRoot + File.separator + Config.User + File.separator + which;
        File fp = new File(monthlyRootDir).getCanonicalFile();

        if (!fp.exists())
        {
            if (which.equals("monthly-pages"))
                Util.err("User " + user + "has no monthly-pages");
            return;
        }

        new BuildNavigationIndex(monthlyRootDir, BuildNavigationIndex.DIVIDER).buildNavigation();
    }

    /* ======================================================================= */

    @SuppressWarnings("unused")
    private void do_user_monthly_insert_nav_controls(String user) throws Exception
    {
        Util.out(">>> Inserting monthly navigation controls for user " + Config.User);
        do_user_monthly_insert_nav_controls(user, "monthly-pages");
        do_user_monthly_insert_nav_controls(user, "monthly-reposts");
    }
    
    private void do_user_monthly_insert_nav_controls(String user, String which) throws Exception
    {
        String monthlyRootDir = Config.DownloadRoot + File.separator + Config.User + File.separator + which;
        File fp = new File(monthlyRootDir).getCanonicalFile();

        if (!fp.exists())
        {
            if (which.equals("monthly-pages"))
                Util.err("User " + user + "has no monthly-pages");
            return;
        }
        
        new InsertNavigationControls(monthlyRootDir, InsertNavigationControls.DIVIDER).insertContols();
   }
}