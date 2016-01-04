package my.LJExport;

/*
 * This program downloads user journal records into Config.DownloadRoot/pages.
 */

import java.net.HttpCookie;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpStatus;
import org.apache.http.cookie.*;

import java.io.File;

import my.LJExport.test.*;

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

    static private boolean aborting;
    static private boolean logoutFailed = false;
    public static Object Lock = new String();
    private static String pagesDir;
    private static String repostsDir;
    private static String manualDir;
    private static Set<String> manualPages;
    private static Set<String> madeDirs;
    public static ProxyServer proxyServer;
    private static Set<String> failedPages;
    private static int nTotal;
    private static AtomicInteger nCurrent;
    public static AtomicInteger unloadablePages;

    static public void setAborting()
    {
        aborting = true;
    }

    static public boolean isAborting()
    {
        return aborting;
    }

    static public void setLogoutFailed()
    {
        logoutFailed = true;
    }

    static public boolean isLogoutFailed()
    {
        return logoutFailed;
    }

    static public void checkAborting() throws Exception
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
            out("*** Warning: LJExport may not have closed all the created login sessions.");
            out("");
            out("             Please check http://www." + Config.Site + "/manage/logins.bml");
            out("             for any leftover sessions and close them manually.");
            out("");
            out("             Alternatively use http://www." + Config.Site + "/logout.bml.");
            out("");

        }
    }

    private void do_user(String user)
    {
        try
        {
            out(">>> Processing journal for user " + user);

            reinit();
            Config.init(user);
            Calendar.init();
            Web.init();
            do_login();
            Calendar.index();

            Util.mkdir(Config.DownloadRoot + File.separator + Config.User);
            pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "pages";
            repostsDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "reposts";
            Util.mkdir(pagesDir);

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
                do_logout();
                Web.shutdown();
                break;
            }

            switch (Config.Method)
            {
            case SELENIUM:
                PageReaderSelenium.reinit();
                if (proxyServer == null)
                    proxyServer = ProxyServer.create();
                out(">>> Launching browsers");
                break;
            }

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
            case BASIC:
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

    static public void out(String s)
    {
        System.out.println(s);
    }

    static public void err(String s)
    {
        System.err.println(s);
    }

    static public void err(Exception ex)
    {
        err("*** Exception: " + ex.getMessage());
        ex.printStackTrace();
    }

    static public void err(String s, Exception ex)
    {
        err(s + ex.getMessage());
        ex.printStackTrace();
    }

    public void do_login() throws Exception
    {
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

    static public void do_work() throws Exception
    {
        PageReaderHtmlUnit.Context htmlUnitContext = null;
        PageReaderSelenium.Context seleniumContext = null;
        String rurl = null;

        try
        {
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
                    reader = new PageReaderSelenium(rurl, pageDir, seleniumContext);
                    break;

                case HTML_UNIT:
                    reader = new PageReaderHtmlUnit(rurl, pageDir, htmlUnitContext);
                    break;

                case BASIC:
                    reader = new PageReaderBasic(rurl, pageDir);
                    break;
                }

                int nc = nCurrent.incrementAndGet();
                double fpct = 100.0 * ((double) nc / nTotal);
                out(">>> " + "[" + Config.User + "] " + rurl + " (" + nc + "/" + nTotal + ", " + String.format("%.2f", fpct) + "%)");

                reader.readPage();
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
        }
    }

    static private String makePageDir(String yyyy_mm) throws Exception
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

    static public void saveDebugPage(String name, String content) throws Exception
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

    static private void enumManualPages() throws Exception
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

    static public String manualPageLoad(String rurl, int npage) throws Exception
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
     * what appears to be an LJ bug.
     * 
     * Alternatively, they still can be loaded manually into directory
     * Config.DownloadRoot/Config.User/manual-load
     * as XXXXX.html (for the first page)
     * and XXXXX-page-N.html (for subsequent pages)
     * and will be picked by LJExport from there.  
     */
    private static boolean skipPage(String rurl) throws Exception
    {
        if (Config.User.equals("colonelcassad"))
        {
            if (rurl.equals("1109403.html") ||
                    rurl.equals("767534.html") ||
                    rurl.equals("1801636.html") ||
                    rurl.equals("1376472.html") ||
                    rurl.equals("1621888.html") ||
                    rurl.equals("1519545.html") ||
                    rurl.equals("2169285.html") ||
                    rurl.equals("776611.html") ||
                    rurl.equals("1749117.html") ||
                    rurl.equals("2307881.html") ||
                    rurl.equals("2003951.html") ||
                    rurl.equals("2254859.html") ||
                    rurl.equals("1687162.html") ||
                    rurl.equals("1424522.html") ||
                    rurl.equals("1685641.html") ||
                    rurl.equals("1762051.html") ||
                    rurl.equals("865056.html") ||
                    rurl.equals("1733639.html") ||
                    rurl.equals("1570374.html") ||
                    rurl.equals("829258.html") ||
                    rurl.equals("1753474.html") ||
                    rurl.equals("670182.html") ||
                    rurl.equals("1921560.html") ||
                    rurl.equals("807691.html") ||
                    rurl.equals("2017670.html") ||
                    rurl.equals("2341094.html") ||
                    rurl.equals("1044892.html") ||
                    rurl.equals("976413.html") ||
                    rurl.equals("1036375.html") ||
                    rurl.equals("2412676.html"))
            {
                return true;
            }
        }
        else if (Config.User.equals("miguel_kud"))
        {
            if (rurl.equals("32069.html"))
            {
                return true;
            }
        }
        else if (Config.User.equals("dmitrij_sergeev"))
        {
            // import just this one record
            if (!rurl.equals("407503.html"))
                return true;
        }

        return false;
    }
}
