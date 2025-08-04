package my.LJExport.maintenance.styles;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.synch.ThreadsControl;
import my.LJExport.styles.StyleManager;

public class MainMergeStyleRepositories
{
    private static final String ALL_USERS = "<all>";
    // private static final String AllUsersFromUser = "kot_begemott";
    private static final String AllUsersFromUser = null;

    private static final String Users = ALL_USERS;
    // private static final String Users = "oboguev";
    // private static final String Users = "sergeytsvetkov";
    // private static final String Users = "nationalism.org";
    // private static final String Users = "udod99.lj-rossia-org";
    
    @SuppressWarnings("unused")
    private boolean conflict = false;

    public static void main(String[] args)
    {
        try
        {
            do_users(Users);
        }
        catch (Exception ex)
        {
            Util.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }

    private static void do_users(String users) throws Exception
    {
        boolean forceExitNow = false;

        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.init("");

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

            if (Util.False)
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
                MainMergeStyleRepositories self = new MainMergeStyleRepositories();
                self.do_user(user);
            }
            finally
            {
                ThreadsControl.shutdownAfterUser();
            }

        }
    }

    private void do_user(String user) throws Exception
    {
        try
        {
            Config.User = user;
            Config.mangleUser();
            Util.out("User: " + user);

            String userStyleCatalogDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "styles";
            StyleManager sm = new StyleManager(userStyleCatalogDir, null, true);
            sm.init();
            String userStyleDir = sm.getStyleDir();
            sm.close();

            String systemStyleCatalogDir = Config.DownloadRoot + File.separator + Config.User + File.separator
                    + "@livejournal-styles";
            sm = new StyleManager(systemStyleCatalogDir, null, true);
            sm.init();
            String systemStyleDir = sm.getStyleDir();
            sm.close();

            Util.out(userStyleDir);
            Util.out(systemStyleDir);

            MergeStyleRepositories mss = new MergeStyleRepositories(userStyleDir, systemStyleDir);
            if (mss.noConflicts())
            {
                
            }
            else
            {
                Util.err("Conflicts");
                conflict = true;
            }
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }
}
