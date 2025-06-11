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

import org.apache.http.HttpStatus;
import org.apache.http.cookie.Cookie;

import my.LJExport.Config.WebMethod;
import my.LJExport.calendar.Calendar;
import my.LJExport.readers.PageReader;
import my.LJExport.readers.PageReaderBasic;
import my.LJExport.readers.PageReaderHtmlUnit;
import my.LJExport.readers.PageReaderSelenium;
import my.LJExport.readers.direct.PageReaderDirect;
import my.LJExport.runtime.ActivityCounters;
import my.LJExport.runtime.LinkDownloader;
import my.LJExport.runtime.ProxyServer;
import my.LJExport.runtime.RateLimiter;
import my.LJExport.runtime.UrlDurationHistory;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;

import java.io.File;

// import my.LJExport.test.*;

// http://www.livejournal.com/manage/logins.bml
// http://www.livejournal.com/logout.bml

public class Main
{
    public class MainRunnable implements Runnable
    {
        public void run()
        {
            try
            {
                int priority = (Thread.NORM_PRIORITY + Thread.MIN_PRIORITY) / 2;
                if (priority == Thread.NORM_PRIORITY)
                    priority = Thread.MIN_PRIORITY;
                Thread.currentThread().setPriority(priority);

                Main.do_work();
            }
            catch (Exception ex)
            {
                boolean wasAborting = Main.isAborting();
                Main.setAborting();
                if (!wasAborting)
                {
                    System.err.println("*** Exception: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
    }

    private static boolean aborting;
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

    public static void setAborting()
    {
        aborting = true;
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
        // TestHtmlUnit.test();
        // TestSelenium.test1();
        // TestSelenium.test3();
        Main main = new Main();
        main.do_main(args);
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

    private void do_main(String[] args)
    {
        try
        {
            deadLinks = Util.read_set("deadlinks.txt");
            // TODO: parse args or display user interface
            StringTokenizer st = new StringTokenizer(Config.Users, ", \t\r\n");
            int nuser = 0;

            while (st.hasMoreTokens() && !isAborting())
            {
                String user = st.nextToken();
                user = user.trim().replace("\t", "").replace(" ", "");
                if (user.equals(""))
                    continue;
                if (nuser++ != 0)
                {
                    out("");
                    Web.shutdown();
                }
                do_user(user);
            }

            if (proxyServer != null)
                proxyServer.stop();
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        if (isAborting() || isLogoutFailed())
        {
            out("");
            out("*** Warning: LJExport may not have closed all login sessions it created.");
            out("");
            out("             Please check http://www." + Config.Site + "/manage/logins.bml");
            out("             for any leftover sessions and close them manually.");
            out("");
            out("             Alternatively use http://www." + Config.Site + "/logout.bml.");
            out("");
        }

        if (Config.Method == WebMethod.SELENIUM)
            UrlDurationHistory.display();
    }

    private void do_user(String user)
    {
        try
        {
            out(">>> Processing journal for user " + user);

            reinit();
            Config.init(user);
            Calendar.init();
            RateLimiter.setRateLimit(0);
            Web.init();
            do_login();
            RateLimiter.setRateLimit(Config.RateLimitCalendar);
            Calendar.index();
            RateLimiter.setRateLimit(Config.RateLimitPageLoad);

            Util.mkdir(Config.DownloadRoot + File.separator + Config.User);
            pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "pages";
            repostsDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "reposts";
            linksDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "links";
            Util.mkdir(pagesDir);
            Util.mkdir(linksDir);
            LinkDownloader.init(linksDir);

            manualDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "manual-load";
            enumManualPages();

            if (!Config.ReloadExistingFiles)
            {
                Calendar.Records = removeExistingRecords(Calendar.Records);
                out(">>> Located " + Calendar.Records.size() + " records to load");
            }

            if (Calendar.Records.size() == 0)
            {
                out(">>> Nothing to load for user " + user);
                do_logout();
                Web.shutdown();
                return;
            }

            nTotal = Calendar.Records.size();
            nCurrent = new AtomicInteger(0);

            Config.UnloadablePagesAllowed = nTotal / 100;
            Config.UnloadablePagesAllowed = Math.min(Config.UnloadablePagesAllowed, Config.MaxUnloadablePagesAllowed);
            Config.UnloadablePagesAllowed = Math.max(Config.UnloadablePagesAllowed, Config.MinUnloadablePagesAllowed);

            switch (Config.Method)
            {
            case HTML_UNIT:
            case SELENIUM:
                // Not yet. We'll need the connection for links downloading
                // do_logout();
                // Web.shutdown();
                break;

            case BASIC:
            case DIRECT:
                break;
            }

            switch (Config.Method)
            {
            case SELENIUM:
                PageReaderSelenium.reinit();
                if (proxyServer == null)
                    proxyServer = ProxyServer.create();
                out(">>> Launching slave browsers");
                break;

            case BASIC:
            case DIRECT:
            case HTML_UNIT:
                break;
            }
            
            ActivityCounters.reset();

            Vector<Thread> vt = new Vector<Thread>();
            // start worker threads
            for (int nt = 0; nt < Math.min(Config.NWorkThreads, Calendar.Records.size()); nt++)
            {
                Thread t = new Thread(new MainRunnable());
                vt.add(t);
                t.start();
            }
            // wait for worker threads to complete
            for (int nt = 0; nt < vt.size(); nt++)
            {
                vt.get(nt).join();
            }

            if (Calendar.Records.size() != 0)
                setAborting();

            if (isAborting())
                err(">>> Aborted exporting journal for user " + Config.User);
            else
                out(">>> Completed exporting journal for user " + Config.User);

            if (failedPages.size() != 0)
            {
                err("");
                err("WARNING: " + failedPages.size() + " record(s) failed to load.");
                err("         You may retry loading.");
            }

            switch (Config.Method)
            {
            case HTML_UNIT:
            case SELENIUM:
                do_logout();
                Web.shutdown();
                break;

            case BASIC:
            case DIRECT:
                do_logout();
                Web.shutdown();
                break;
            }
        }
        catch (Exception ex)
        {
            setAborting();
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void out(String s)
    {
        System.out.println(s);
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

    public void do_login() throws Exception
    {
        RateLimiter.setRateLimit(0);

        out(">>> Logging into " + Config.Site + " as user " + Config.LoginUser);

        StringBuilder sb = new StringBuilder();
        sb.append(Web.escape("ret") + "=" + "1" + "&");
        sb.append(Web.escape("user") + "=" + Web.escape(Config.LoginUser) + "&");
        sb.append(Web.escape("password") + "=" + Web.escape(Config.LoginPassword) + "&");
        sb.append("action:login");

        Web.Response r = Web.post("https://www." + Config.Site + "/login.bml?ret=1", sb.toString());
        if (r.code != HttpStatus.SC_OK)
            throw new Exception("Unable to log into the server: " + Web.describe(r.code));

        boolean logged_in = false;

        for (Cookie cookie : Web.cookieStore.getCookies())
        {
            if (!Util.is_in_domain(Config.Site, cookie.getDomain()))
                continue;

            if (cookie.getName().equals("ljmastersession") || cookie.getName().equals("ljloggedin")
                    || cookie.getName().equals("ljsession"))
            {
                logged_in = true;
            }
        }

        if (!logged_in)
            throw new Exception("Unable to log into the server: most probably incorrect username or password");

        out(">>> Logged in");
    }

    public void do_logout() throws Exception
    {
        RateLimiter.setRateLimit(0);

        String sessid = null;

        for (Cookie cookie : Web.cookieStore.getCookies())
        {
            if (!Util.is_in_domain(Config.Site, cookie.getDomain()))
                continue;

            if (cookie.getName().equals("ljmastersession") || cookie.getName().equals("ljloggedin")
                    || cookie.getName().equals("ljsession"))
            {
                StringTokenizer st = new StringTokenizer(cookie.getValue(), ":");

                while (st.hasMoreTokens())
                {
                    String tok = st.nextToken();
                    if (tok.length() >= 2 && tok.charAt(0) == 's')
                    {
                        sessid = tok.substring(1);
                        break;
                    }
                }

                if (sessid != null)
                    break;
            }
        }

        if (sessid == null)
        {
            out(">>> Unable to log off the server (unknown sessid)");
            setLogoutFailed();
            return;
        }

        out(">>> Logging off " + Config.Site);
        StringBuilder sb = new StringBuilder();
        sb.append("http://www." + Config.Site + "/logout.bml?ret=1&user=" + Config.LoginUser + "&sessid=" + sessid);
        Web.Response r = null;
        try
        {
            r = Web.get(sb.toString());
        }
        catch (Exception ex)
        {
        }

        if (r != null && r.code == HttpStatus.SC_OK)
        {
            out(">>> Logged off");
        }
        else
        {
            out(">>> Loggoff unsuccessful");
            setLogoutFailed();
        }
    }

    public static void do_work() throws Exception
    {
        PageReaderHtmlUnit.Context htmlUnitContext = null;
        PageReaderSelenium.Context seleniumContext = null;
        String rurl = null;

        try
        {
            Thread.currentThread().setName("page-loader: idle");

            switch (Config.Method)
            {
            case HTML_UNIT:
                // null if login soft-failed (e.g. login limit exceeded)
                htmlUnitContext = PageReaderHtmlUnit.makeContext();
                if (htmlUnitContext == null)
                    return;
                break;

            case SELENIUM:
                // null if login soft-failed (e.g. login limit exceeded)
                seleniumContext = PageReaderSelenium.makeContext();
                if (seleniumContext == null)
                    return;
                break;

            case BASIC:
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
                case SELENIUM:
                    reader = new PageReaderSelenium(rurl, pageDir, linksDir, seleniumContext);
                    break;

                case HTML_UNIT:
                    reader = new PageReaderHtmlUnit(rurl, pageDir, htmlUnitContext);
                    break;

                case BASIC:
                    reader = new PageReaderBasic(rurl, pageDir);
                    break;

                case DIRECT:
                    reader = new PageReaderDirect(rurl, pageDir, linksDir);
                    break;
                }

                int nc = nCurrent.incrementAndGet();
                double fpct = 100.0 * ((double) nc / nTotal);
                out(">>> " + "[" + Config.User + "] " + rurl + " (" + nc + "/" + nTotal + ", " + String.format("%.2f", fpct)
                        + "%) " + ActivityCounters.summary());

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
            if (htmlUnitContext != null)
                htmlUnitContext.close();

            if (seleniumContext != null)
                seleniumContext.close();

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

    Vector<String> removeExistingRecords(Vector<String> src) throws Exception
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
        if (Config.False && Config.User.equals("colonelcassad"))
        {
            if (rurl.equals("1109403.html") ||
                    rurl.equals("2412676.html"))
            {
                return true;
            }
        }
        else if (Config.False && Config.User.equals("miguel_kud"))
        {
            if (rurl.equals("32069.html"))
            {
                return true;
            }
        }
        else if (Config.False && Config.User.equals("dmitrij_sergeev"))
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
}
