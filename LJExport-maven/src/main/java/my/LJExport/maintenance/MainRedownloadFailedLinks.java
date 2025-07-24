package my.LJExport.maintenance;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.http.ActivityCounters;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.LinkRedownloader;
import my.LJExport.runtime.lj.LJUtil;
import my.LJExport.runtime.synch.ThreadsControl;

/*
 * Redownload linked files listed in failed-link-downloads.txt.
 * The list is built by maintenance tool DetectFailedDownloads.

 * These are linked files pointed by IMG.SRC and A.HREF that contain HTML/XHTML/PHP/TXT content -- 
 * error pages saying that files was unavailable.
 * 
 * Use: -Xss4m -Xmx16g
 */
public class MainRedownloadFailedLinks
{
    private String userRoot;
    private String linksDir;
    private KVFile kvfile;
    private List<KVEntry> kvlist;

    private static final String ALL_USERS = "<all>";
    private static final String AllUsersFromUser = null;
    // private static final String AllUsersFromUser = "tanya_mass";

    private static final String Users = ALL_USERS;
    // private static final String Users = "funt";
    // private static final String Users = "krylov_arhiv,krylov";
    // private static final String Users = "zhu_s";

    /* we can use large number of threads because they usually are network IO bound */
    private static final int NWorkThreads = 300;
    private static final int MaxConnectionsPerRoute = 10;

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
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

    private MainRedownloadFailedLinks()
    {
    }

    private static void do_users(String users) throws Exception
    {
        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.NWorkThreads = NWorkThreads;
        Config.MaxConnectionsPerRoute = MaxConnectionsPerRoute;
        Config.init("");
        Web.init();

        ActivityCounters.reset();
        RateLimiter.LJ_IMAGES.setRateLimit(100);

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

            if (nuser++ != 0)
            {
                Util.out("");
                Util.out("=====================================================================================");
                Util.out("");
            }

            try
            {
                MainRedownloadFailedLinks self = new MainRedownloadFailedLinks();
                self.do_user(user);
            }
            finally
            {
                ThreadsControl.shutdownAfterUser();
            }
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
            Config.autoconfigureSite();

            /* login may be required for pictures marked 18+ */
            Main.do_login();

            userRoot = Config.DownloadRoot + File.separator + Config.User;
            linksDir = userRoot + File.separator + "links";
            kvfile = new KVFile(linksDir + File.separator + "failed-link-downloads.txt");

            Util.out(">>> Redownloading failed links for user " + Config.User);

            if (!redownloadLinkFiles())
                return;
            
            if (Main.isAborting())
            {
                Util.err(">>> Aborted redownloading of failed links for user " + Config.User);
                return;
            }
            
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }
    
    /* =================================================================================================== */
    
    private boolean redownloadLinkFiles() throws Exception
    {
        if (kvfile.exists())
            kvlist = kvfile.load(true);

        if (kvlist == null ||  kvlist.size() == 0)
        {
            Util.out("User " + Config.User + " has no files scheduled to redownload");
            return false;
        }
        
        // start worker threads
        ThreadsControl.workerThreadGoEventFlag.clear();
        ThreadsControl.activeWorkerThreadCount.set(0);

        List<Thread> vt = new ArrayList<Thread>();
        for (int nt = 0; nt < Math.min(NWorkThreads, kvlist.size()); nt++)
        {
            Thread t = new Thread(new RedownloadRunnable(this));
            vt.add(t);
            t.start();
            ThreadsControl.activeWorkerThreadCount.incrementAndGet();
        }

        ThreadsControl.workerThreadGoEventFlag.set();

        // wait for worker threads to complete
        boolean firstHasCompleted = false;
        for (int nt = 0; nt < vt.size(); nt++)
        {
            vt.get(nt).join();
            if (!firstHasCompleted)
            {
                firstHasCompleted = true;
                if (!Main.isAborting())
                    Util.out(">>> Waiting for active worker threads to complete ...");
            }
        }
        
        return true;
    }

    public static class RedownloadRunnable implements Runnable
    {
        private final MainRedownloadFailedLinks main;

        public RedownloadRunnable(MainRedownloadFailedLinks main)
        {
            this.main = main;
        }

        public void run()
        {
            try
            {
                ThreadsControl.backgroundStarting();
                Thread.currentThread().setName("worker");
                main.do_redownload();
            }
            catch (Exception ex)
            {
                ThreadsControl.backgroundException(ex);
            }
            finally
            {
                ThreadsControl.backgroundFinally();
            }
        }
    }

    private void do_redownload() throws Exception
    {
        ThreadsControl.workerThreadGoEventFlag.waitFlag();
        
        LinkRedownloader linkRedownloader = new LinkRedownloader(linksDir);

        for (;;)
        {
            KVEntry entry = null;

            if (Main.isAborting())
                return;

            synchronized (kvlist)
            {
                if (kvlist.size() == 0)
                    return;
                entry = kvlist.remove(0);
            }
            
            String url = entry.key;
            String relpath = entry.value;
            boolean image = false;
            
            if (url.startsWith("image:"))
            {
                image = true;
                url = Util.stripStart(url, "image:");
            }
            else if (url.startsWith("document:"))
            {
                image = false;
                url = Util.stripStart(url, "document:");
            }
            
            String referer = LJUtil.userBase();
            
            if (LinkDownloader.shouldDownload(url, false) && linkRedownloader.redownload(url, relpath, referer, image))
            {
                // ### OK -> remove from list
                // ### add original-attr if missing
            }
            else
            {
                // ### cannot reload -> restore original URL in links
            }
        }
    }
}