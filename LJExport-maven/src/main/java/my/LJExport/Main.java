package my.LJExport;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

/*
 * This program downloads user journal records into Config.DownloadRoot/pages.
 */

// import java.net.HttpCookie;
import java.util.concurrent.atomic.AtomicInteger;

import my.LJExport.calendar.Calendar;
import my.LJExport.profile.ReadProfile;
import my.LJExport.readers.PageReader;
import my.LJExport.readers.direct.PageReaderDirect;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.audio.PlaySound;
import my.LJExport.runtime.http.ActivityCounters;
import my.LJExport.runtime.http.ProxyServer;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.http.browserproxy.BrowserProxyFactory;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.lj.login.LoginDreamwidth;
import my.LJExport.runtime.lj.login.LoginLivejournal;
import my.LJExport.runtime.synch.ThreadsControl;

import java.io.File;

// import my.LJExport.test.*;

// http://www.livejournal.com/manage/logins.bml
// http://www.livejournal.com/logout.bml

/*
 * Загрузить страницы дневника.
 * 
 * Use stack size: -Xss2m
 */
public class Main
{
    public class MainRunnable implements Runnable
    {
        public void run()
        {
            try
            {
                ThreadsControl.backgroundStarting();
                Main.do_work();
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

    private static volatile boolean aborting;
    private static boolean logoutFailed = false;
    public static Object Lock = new String();
    private static String pagesDir;
    private static String repostsDir;
    private static String linksDir;
    private static String manualDir;
    private static Set<String> manualPages;
    private static Set<String> madeDirs;
    public static ProxyServer proxyServer;
    private static Set<String> failedPages;
    private static int nTotal;
    private static AtomicInteger nCurrent;
    public static AtomicInteger unloadablePages;
    private static Set<String> deadLinks;
    private static Set<String> logged_in = new HashSet<>();
    public static LinkDownloader linkDownloader = new LinkDownloader();

    public static void setAborting()
    {
        aborting = true;
        Web.aborting();
    }

    public static boolean isAborting()
    {
        return aborting;
    }

    public static void setLogoutFailed()
    {
        logoutFailed = true;
    }

    public static boolean isLogoutFailed()
    {
        return logoutFailed;
    }

    public static void checkAborting() throws Exception
    {
        if (isAborting())
            throw new InterruptedException("Aborting");
    }

    public static void main(String[] args)
    {
        Main main = new Main();
        main.do_main(null);
    }

    private void reinit() throws Exception
    {
        aborting = false;
        madeDirs = new HashSet<String>();
        failedPages = new HashSet<String>();
        unloadablePages = new AtomicInteger(0);
    }

    public static synchronized void markFailedPage(String rurl) throws Exception
    {
        err(">>> " + "[" + Config.User + "] " + rurl + " failed to load");
        failedPages.add(rurl);
    }

    public void do_main(String user)
    {
        try
        {
            LimitProcessorUsage.limit();
            deadLinks = Util.read_set("deadlinks.txt");

            if (user != null)
            {
                do_user(user);
            }
            else
            {
                // TODO: parse args or display user interface
                StringTokenizer st = new StringTokenizer(Config.Users, ", \t\r\n");
                int nuser = 0;

                while (st.hasMoreTokens() && !isAborting())
                {
                    user = st.nextToken();
                    user = user.trim().replace("\t", "").replace(" ", "");
                    if (user.equals(""))
                        continue;
                    
                    if (nuser++ != 0)
                    {
                        out("");
                        Main.linkDownloader.close();
                        Web.shutdown();
                    }

                    do_user(user);
                }
            }

            do_logout();
            Web.shutdown();

            if (proxyServer != null)
                proxyServer.stop();
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
            emergency_logout();
        }

        try
        {
            Main.linkDownloader.close();
        }
        catch (Exception ex)
        {
            Util.noop();
        }

        out(">>> Time: " + Util.timeNow());

        if (isAborting() || isLogoutFailed())
        {
            out("");
            out("*** Warning: LJExport may not have closed all login sessions it created.");
            out("");
            out("             Please check http://www." + Config.LoginSite + "/manage/logins.bml");
            out("             for any leftover sessions and close them manually.");
            out("");
            out("             Alternatively use http://www." + Config.LoginSite + "/logout.bml.");
            out("");
        }

        playCompletionSound();
    }

    private void do_user(String user)
    {
        try
        {
            out(">>> Processing journal for user " + user);
            out(">>> Time: " + Util.timeNow());

            reinit();
            Config.init(user);
            RateLimiter.LJ_PAGES.setRateLimit(100);
            Web.init();
            linkDownloader.setUseSmartDownloader(Config.LinkDownloaderLoadFrom);
            do_login();
            Calendar.init();

            RateLimiter.LJ_PAGES.setRateLimit(Config.RateLimit_LiveJournal_Calendar);
            Calendar.index();
            RateLimiter.LJ_PAGES.setRateLimit(Config.RateLimit_LiveJournal_PageLoad);

            Util.mkdir(Config.DownloadRoot + File.separator + Config.User);
            pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "pages";
            repostsDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "reposts";
            linksDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "links";
            Util.mkdir(pagesDir);
            Util.mkdir(linksDir);
            linkDownloader.init(linksDir);

            manualDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "manual-load";
            enumManualPages();

            if (!Config.ReloadExistingFiles)
            {
                Calendar.Records = removeExistingRecords(Calendar.Records);
            }

            if (Util.False)
            {
                // for testing
                Calendar.Records.clear();
                Calendar.Records.add("827312.html");
            }

            out(">>> Located " + Calendar.Records.size() + " records to load");

            if (Calendar.Records.size() == 0)
            {
                out(">>> Nothing (no pages) to load for user " + user);
                new ReadProfile().readAll();
                return;
            }

            nTotal = Calendar.Records.size();
            nCurrent = new AtomicInteger(0);

            Config.UnloadablePagesAllowed = nTotal / 100;
            Config.UnloadablePagesAllowed = Math.min(Config.UnloadablePagesAllowed, Config.MaxUnloadablePagesAllowed);
            Config.UnloadablePagesAllowed = Math.max(Config.UnloadablePagesAllowed, Config.MinUnloadablePagesAllowed);

            ActivityCounters.reset();

            // start worker threads
            ThreadsControl.workerThreadGoEventFlag.clear();
            ThreadsControl.activeWorkerThreadCount.set(0);

            Vector<Thread> vt = new Vector<Thread>();
            for (int nt = 0; nt < Math.min(Config.NWorkThreads, Calendar.Records.size()); nt++)
            {
                Thread t = new Thread(new MainRunnable());
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
                    if (!isAborting())
                        out(">>> Waiting for active worker threads to complete ...");
                }
            }

            if (Calendar.Records.size() != 0)
                setAborting();

            if (isAborting())
                err(">>> Aborted exporting journal for user " + Config.User);
            else
                out(">>> Completed exporting journal posts for user " + Config.User);

            if (failedPages.size() != 0)
            {
                err("");
                err("WARNING: " + failedPages.size() + " record(s) failed to load.");
                err("         You may retry loading.");
            }

            if (!isAborting())
            {
                new ReadProfile().readAll();
                out(">>> Completed exporting journal for user " + Config.User);
            }

            ThreadsControl.shutdownAfterUser();
        }
        catch (Exception ex)
        {
            setAborting();
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    public static void out(String s)
    {
        System.out.println(s);
        System.out.flush();
    }

    public static void err(String s)
    {
        System.out.flush();
        System.err.println(s);
    }

    public static void err(Exception ex)
    {
        err("*** Exception: " + ex.getMessage());
        ex.printStackTrace();
    }

    public static void err(String s, Exception ex)
    {
        err(s + ex.getMessage());
        ex.printStackTrace();
    }

    public static void do_login() throws Exception
    {
        if (!Config.UseLogin || Config.User == null || Config.User.length() == 0)
            return;
        
        if (!BrowserProxyFactory.requiresProgrammaticLogin(Config.LoginSite))
        {
            out(">>> Login to " + Config.LoginSite + " is manual via proxy browser");
            return;
        }

        if (logged_in.contains(Config.LoginSite))
        {
            out(">>> Already logged in to " + Config.LoginSite);
            return;
        }

        long originalRateLimit = RateLimiter.LJ_PAGES.getRateLimit();
        RateLimiter.LJ_PAGES.setRateLimit(100);

        out(">>> Logging into " + Config.LoginSite + " as user " + Config.LoginUser);

        if (Config.isDreamwidthOrg())
        {
            LoginDreamwidth.login();
        }
        else
        {
            LoginLivejournal.login();
        }

        logged_in.add(Config.LoginSite);
        out(">>> Logged in to " + Config.LoginSite);
        RateLimiter.LJ_PAGES.setRateLimit(originalRateLimit);
    }

    public static void do_logout() throws Exception
    {
        Exception caught = null;

        for (String loginSite : logged_in)
        {
            try
            {
                do_logout(loginSite);
            }
            catch (Exception ex)
            {
                if (caught == null)
                    caught = ex;
            }
        }

        if (caught != null)
            throw caught;
    }

    private static void do_logout(String loginSite) throws Exception
    {
        if (!logged_in.contains(loginSite))
            return;
        
        if (!BrowserProxyFactory.requiresProgrammaticLogin(loginSite))
            return;

        long originalRateLimit = RateLimiter.LJ_PAGES.getRateLimit();
        RateLimiter.LJ_PAGES.setRateLimit(100);

        boolean logoutSucceeded = false;

        if (Config.isDreamwidthOrg(loginSite))
            logoutSucceeded = LoginDreamwidth.logout(loginSite);
        else
            logoutSucceeded = LoginLivejournal.logout(loginSite);

        if (logoutSucceeded)
            logged_in.remove(loginSite);
        else
            setLogoutFailed();

        RateLimiter.LJ_PAGES.setRateLimit(originalRateLimit);
    }

    public static void emergency_logout()
    {
        if (!logged_in.isEmpty())
        {
            try
            {
                do_logout();
            }
            catch (Exception ex)
            {
                Util.noop();
            }
        }
    }

    public static void do_work() throws Exception
    {
        String rurl = null;

        try
        {
            Thread.currentThread().setName("page-loader: idle");
            ThreadsControl.workerThreadGoEventFlag.waitFlag();

            switch (Config.Method)
            {
            case DIRECT:
                break;
            }

            for (;;)
            {
                rurl = null;
                Main.checkAborting();

                synchronized (Lock)
                {
                    if (Calendar.Records.size() == 0)
                        break;
                    rurl = Calendar.Records.get(0);
                    Calendar.Records.remove(0);
                }

                if (skipPage(rurl))
                {
                    out(">>> Skipping [" + Config.User + "] " + rurl);
                    nCurrent.incrementAndGet();
                    continue;
                }

                String yyyy_mm = Calendar.get_record_yyyy_mm(rurl);
                String pageDir = makePageDir(yyyy_mm);

                // if (!Config.ReloadExistingFiles)
                // {
                //     File file = new File(pageDir + File.separator + rurl);
                //     if (file.exists())
                //         continue;
                // }

                PageReader reader = null;

                switch (Config.Method)
                {
                case DIRECT:
                    reader = new PageReaderDirect(rurl, pageDir);
                    break;
                }

                int nc = nCurrent.incrementAndGet();
                double fpct = 100.0 * ((double) nc / nTotal);
                out(">>> " + "[" + Config.User + "] " + rurl + " (" + nc + "/" + nTotal + ", " + String.format("%.2f", fpct)
                        + "%) " + ActivityCounters.summary(nTotal - nc));

                Thread.currentThread().setName("page-loader: loading " + rurl);

                reader.readPage();
                ActivityCounters.loadedPage();

                Thread.currentThread().setName("page-loader: idle");
            }
        }
        catch (Exception ex)
        {
            if (!Main.isAborting())
            {
                if (rurl == null)
                    throw new Exception("Unable to read [" + Config.User + "]", ex);
                else
                    throw new Exception("Unable to read [" + Config.User + "] " + rurl, ex);
            }
        }
        finally
        {
            Thread.currentThread().setName("page-loader: idle");
        }
    }

    private static String makePageDir(String yyyy_mm) throws Exception
    {
        String pageDir = pagesDir + File.separator + yyyy_mm.replace("/", File.separator);

        synchronized (Lock)
        {
            if (madeDirs.contains(yyyy_mm))
                return pageDir;
        }

        Util.mkdir(pageDir);

        synchronized (Lock)
        {
            madeDirs.add(yyyy_mm);
        }

        return pageDir;
    }

    public static void saveDebugPage(String name, String content) throws Exception
    {
        synchronized (Main.class)
        {
            String saveDir = Config.DownloadRoot + File.separator + "@debug";
            Util.mkdir(saveDir);
            Util.writeToFile(saveDir + File.separator + name, content);
        }
    }

    private Vector<String> removeExistingRecords(Vector<String> src) throws Exception
    {
        Vector<String> res = new Vector<String>();

        for (String rurl : src)
        {
            String yyyy_mm = Calendar.get_record_yyyy_mm(rurl);

            String pageDir = pagesDir + File.separator + yyyy_mm.replace("/", File.separator);
            String repostDir = repostsDir + File.separator + yyyy_mm.replace("/", File.separator);

            File pageFile = new File(pageDir + File.separator + rurl);
            File repostFile = new File(repostDir + File.separator + rurl);

            if (!pageFile.exists() && !repostFile.exists())
                res.add(rurl);
        }

        return res;
    }

    private static void enumManualPages() throws Exception
    {
        manualPages = new HashSet<String>();

        File md = new File(manualDir);
        if (md.exists() && md.isDirectory())
        {
            File[] xlist = md.listFiles();
            if (xlist == null)
                throw new Exception("Unable to enumerate files under " + manualDir);
            for (File xf : xlist)
            {
                if (xf.isFile())
                    manualPages.add(xf.getName());
            }
        }
    }

    public static String manualPageLoad(String rurl, int npage) throws Exception
    {
        String postfix = ".html";

        if (npage != 1)
        {
            if (rurl.endsWith(postfix))
            {
                rurl = rurl.substring(0, rurl.length() - postfix.length());
                rurl = rurl + "-page-" + npage + postfix;
            }
            else
            {
                throw new Exception("Unexpected page file name format");
            }
        }

        if (manualPages.contains(rurl))
            return Util.readFileAsString(manualDir + File.separator + rurl);
        else
            return null;
    }

    /*
     * This routine is intended to skip pages that are not downloadable due to
     * an LJ bug (such as Expand link does not expand and does not go away).
     * 
     * Alternatively, these pages still can be loaded manually into directory
     * Config.DownloadRoot/Config.User/manual-load
     * as XXXXX.html (for the first page)
     * and XXXXX-page-N.html (for subsequent pages)
     * and will be picked by LJExport from there.  
     */
    private static boolean skipPage(String rurl) throws Exception
    {
        if (Util.False && Config.User.equals("colonelcassad"))
        {
            if (rurl.equals("1109403.html") ||
                    rurl.equals("2412676.html"))
            {
                return true;
            }
        }
        else if (Util.False && Config.User.equals("miguel_kud"))
        {
            if (rurl.equals("32069.html"))
            {
                return true;
            }
        }
        else if (Util.False && Config.User.equals("dmitrij_sergeev"))
        {
            // import just this one record
            if (!rurl.equals("407503.html"))
                return true;
        }

        return false;
    }

    /*
     * Check if specified URL is on the list of "dead" Expand links
     * that do not expand.
     */
    public static boolean isDeadLink(String url) throws Exception
    {
        return deadLinks.contains(url);
    }

    public static void playCompletionSound()
    {
        PlaySound.play("audio/flute-alert.wav");
    }
}
