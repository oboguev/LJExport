package my.LJExport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import my.LJExport.calendar.YYYY_MM;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBase.AbsoluteLinkBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.ActivityCounters;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.SmartLinkDownloader.LoadFrom;
import my.LJExport.runtime.links.util.RelativeLink;
import my.LJExport.runtime.synch.MonthlyGate;
import my.LJExport.runtime.synch.ThreadsControl;

/*
 * Загрузить отсутствующие локальные копии сссылок в страницах пользователя.
 * Они могут быть пропущены, если в момент загрузки страниц сервер, содержащий ссылки, не работал.
 * 
 * Use: -Xss4m -Xmx16g
 */
public class MainDownloadLinks
{
    private String userRoot;
    private String linksDir;
    private List<String> pageFiles;
    private int pageFilesTotalCount;
    private int countFetched = 0;

    private static final String ALL_USERS = "<all>";
    // private static final String AllUsersFromUser = null;
    private static final String AllUsersFromUser = "avmalgin";
    // private static final YYYY_MM AllUsersFromUserFromYyyyMm = null;
    private static final YYYY_MM AllUsersFromUserFromYyyyMm = new YYYY_MM(2015, 2);

    private static final String Users = ALL_USERS;

    // private static final String Users = "krylov";
    // private static final String Users = "_devol_,1981dn,1981dn_dn,64vlad,a_bugaev,a_kaminsky,a_samovarov,a_sevastianov,abcdefgh,afanarizm,afrika_sl,aleksei";
    // private static final String Users = "amfora,andrewmed,anya_anya_anya";
    // private static final String Users = "archives_ru,arkhip,artemov_igor,asriyan,atorin";
    // private static final String Users = "corporatelie,d_olshansky";
    // private static final String Users = "dmitrij_sergeev,doppel_herz,ego,eremei,f_f,fat_yankey,fluffyduck2,funt";
    // private static final String Users = "harmfulgrumpy,hokma,holmogor,istoriograf,ivkonstant,jlm_taurus";
    // private static final String Users = "karaulov,knigipoistcccp,kordonsky,kornev,kot_begemott,kouzdra,krylov,krylov_arhiv,kushnirenko,laert,langobard";
    // private static final String Users = "lesolub,limonov_eduard,lomonosov,lxe";
    // private static final String Users = "m_yu_sokolov,man_with_dogs,maxim_sokolov2,michail,miguel_kud,morky,nep2,nikital2014,nilsky_nikolay,novy_chitatel";
    // private static final String Users = "oboguev,olegnemen,olshansky,otrubon,pavell,philtrius,pioneer_lj,polit_ec,pyc_ivan";
    // private static final String Users = "rf2,rigort,rms1,rn_manifesto,rod_ru,ru_bezch,ru_history,ruspartia";
    // private static final String Users = "tutchev,tverdyi_znak,um_plus,unilevel";
    // private static final String Users = "vladimir_tor,von_hoffmann,wiradhe,wyradhe,ystrek,zhenziyou";
    // private static final String Users = "pikitan,schloenski,pravoe_org,piligrim,trufanov";
    // private static final String Users = "krylov_arhiv,krylov";
    // private static final String Users = "zhu_s";
    // private static final String Users = "udod99.lj-rossia-org,harmfulgrumpy.dreamwidth-org";
    // private static final String Users = "udod99.lj-rossia-org,colonelcassad.my_comments,harmfulgrumpy.dreamwidth-org";
    // private static final String Users = "1981dn.pre-2025,1981dn_dn.pre-2025,a_kaminsky.pre-2025,a_samovarov.pre-2025,bantaputu.pre-2025,hokma.pre-2025,krylov.pre-2025,oboguev.pre-2025,pioneer_lj.pre-2025,polit_ec.pre-2025,zhenziyou.pre-2025";
    // private static final String Users = "novy_chitatel";

    /* download images from archive.org in addition to online */
    private static boolean UseArchiveOrg = true;

    /* 
     * additionally reload missing images and links for monthly pages too,
     * this is usually not needed since monthly pages can be regenerated from posts pages
     */
    private static boolean ReloadForMonthlyPages = false;

    /* we can use large number of threads because they usually are network IO bound */
    private static final int NWorkThreads = 300;
    private static final int MaxConnectionsPerRoute = 10;

    /* limit the number of threads working on large  monthly files, to prevent OutOfMemory */
    private static final int NMonthlyWorkThreads = 4;

    private MonthlyGate monthlyGate = new MonthlyGate(NMonthlyWorkThreads, 20);

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
            err("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
            Main.emergency_logout();
        }

        Main.playCompletionSound();
    }

    private MainDownloadLinks()
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

        if (UseArchiveOrg)
            Config.LinkDownloaderLoadFrom = LoadFrom.OnlineAndArchive;

        Config.PrintLinkDownloads = true;

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
                out("");
                out("=====================================================================================");
                out("");
            }

            try
            {
                MainDownloadLinks self = new MainDownloadLinks();
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
            /* login may be required for pictures marked 18+ */
            Config.autoconfigureSite(true);

            userRoot = Config.DownloadRoot + File.separator + Config.User;

            linksDir = userRoot + File.separator + "links";
            // offline = true;

            out(">>> Processing download links for user " + Config.User);

            Util.mkdir(linksDir);
            Main.linkDownloader.init(linksDir);
            Main.linkDownloader.setUseSmartDownloader(Config.LinkDownloaderLoadFrom);

            pageFiles = enumerateHtmlFiles("pages", true);

            if (Util.True)
            {
                pageFiles.addAll(enumerateHtmlFiles("profile", false));
                pageFiles.addAll(enumerateHtmlFiles("reposts", false));
            }

            if (ReloadForMonthlyPages)
            {
                pageFiles.addAll(enumerateHtmlFiles("monthly-pages", false));
                pageFiles.addAll(enumerateHtmlFiles("monthly-reposts", false));
            }

            pageFilesTotalCount = pageFiles.size();

            // start worker threads
            ThreadsControl.workerThreadGoEventFlag.clear();
            ThreadsControl.activeWorkerThreadCount.set(0);

            List<Thread> vt = new ArrayList<Thread>();
            for (int nt = 0; nt < Math.min(NWorkThreads, pageFilesTotalCount); nt++)
            {
                Thread t = new Thread(new MainDownloadLinksRunnable(this));
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
                        out(">>> Waiting for active worker threads to complete ...");
                }
            }

            if (Main.isAborting())
                err(">>> Aborted scanning the journal for user " + Config.User);
            else
                out(">>> Completed scanning the journal for user " + Config.User);
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    private List<String> enumerateHtmlFiles(String which, boolean required) throws Exception
    {
        String dir = userRoot + File.separator + which;

        File fp = new File(dir);

        if (!fp.exists() || !fp.isDirectory())
        {
            if (required)
                throw new Exception("Directory does not exist: " + dir);
            else
                return new ArrayList<>();
        }

        List<String> list = Util.enumerateOnlyHtmlFiles(dir);

        List<String> rlist = new ArrayList<>();
        for (String fn : list)
            rlist.add(which + File.separator + fn);
        return rlist;
    }

    void do_work() throws Exception
    {
        ThreadsControl.workerThreadGoEventFlag.waitFlag();

        for (;;)
        {
            String pageFile = null;

            if (Main.isAborting())
                return;

            synchronized (pageFiles)
            {
                if (pageFiles.size() == 0)
                    return;

                pageFile = pageFiles.remove(0);
                out(String.format(">>> [%s] %s (%d/%d)", Config.User, pageFile, ++this.countFetched, pageFilesTotalCount));
            }

            String pageFileUnix = pageFile.replace(File.separator, "/");

            if (Users.equals(ALL_USERS) && AllUsersFromUser != null && Config.User.equals(AllUsersFromUser) &&
                AllUsersFromUserFromYyyyMm != null && pageFileUnix.startsWith("pages/"))
            {
                String[] sa = Util.stripStart(pageFileUnix, "pages/").split("/");
                int yyyy = Integer.parseInt(sa[0]);
                int mm = Integer.parseInt(sa[1]);
                if (new YYYY_MM(yyyy, mm).compareTo(AllUsersFromUserFromYyyyMm) < 0)
                    continue;
            }

            String pageFileFullPath = userRoot + File.separator + pageFile;
            Thread.currentThread().setName("page-scanner: scanning " + Config.User + " " + pageFile);

            /*
             * Monthly files are large.
             * Do not keep many of them in memory at the same time, to avoid OutOfMemoryException. 
             */
            boolean monthly = pageFile.startsWith("monthly-");
            monthlyGate.acquire(monthly);

            try
            {
                PageParserDirectBase parser = new PageParserDirectBasePassive();
                parser.rurl = Util.extractFileName(pageFileFullPath);

                if (Util.False)
                {
                    if (!parser.rurl.equals("253432.html"))
                        continue;
                    Util.noop();
                }

                parser.pageSource = Util.readFileAsString(pageFileFullPath);
                parser.parseHtml(parser.pageSource);

                String linkReferencePrefix = RelativeLink.fileRelativeLink(this.linksDir, pageFileFullPath, this.userRoot);
                parser.setLinkReferencePrefix(linkReferencePrefix + "/");

                if (parser.downloadExternalLinks(parser.pageRoot, AbsoluteLinkBase.User))
                {
                    String newPageSource = JSOUP.emitHtml(parser.pageRoot);
                    Util.writeToFileSafe(pageFileFullPath, newPageSource);
                }

                /* help GC */
                parser = null;
            }
            finally
            {
                monthlyGate.release(monthly);
            }
        }
    }

    private static void out(String s)
    {
        Main.out(s);
    }

    private static void err(String s)
    {
        Main.err(s);
    }

    /* =============================================================== */

    public static class MainDownloadLinksRunnable implements Runnable
    {
        private final MainDownloadLinks main;

        public MainDownloadLinksRunnable(MainDownloadLinks main)
        {
            this.main = main;
        }

        public void run()
        {
            try
            {
                ThreadsControl.backgroundStarting();
                main.do_work();
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
}
