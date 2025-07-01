package my.LJExport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.ActivityCounters;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.RateLimiter;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.synch.ThreadsControl;
import my.LJExport.xml.JSOUP;

/*
 * Загрузить отсутствующие локальные копии сссылок в страницах пользователя.
 * Они могут быть пропущены, если в момент загрузки страниц сервер, содержащий ссылки, не работал.
 */
public class MainDownloadLinks
{
    private String pagesDir;
    private String linksDir;
    private List<String> pageFiles;
    private int pageFilesTotalCount;
    private int countFetched = 0;

    // private static final String Users = "funt";
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
    private static final String Users = "oldadmiral";
    // private static final String Users = "zhu_s";

    private static final int NWorkThreads = 100;
    private static final int MaxConnectionsPerRoute = 10;

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
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
        Config.MaxConnectionsPerRoute = MaxConnectionsPerRoute;
        Web.init();

        /* login may be required for pictures marked 18+ */
        Main.do_login();

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

            if (nuser++ != 0)
            {
                out("");
                out("=====================================================================================");
                out("");
            }

            MainDownloadLinks self = new MainDownloadLinks();
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

            pagesDir = userRoot + File.separator + "pages";
            // pagesDir = userRoot + File.separator + "reposts";
            linksDir = userRoot + File.separator + "links";
            // offline = true;

            out(">>> Processing download links for user " + Config.User);

            Util.mkdir(linksDir);
            LinkDownloader.init(linksDir);

            pageFiles = Util.enumerateFiles(pagesDir);
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

            String pageFileFullPath = pagesDir + File.separator + pageFile;
            Thread.currentThread().setName("page-scanner: scanning " + Config.User + " " + pageFile);

            PageParserDirectBase parser = new PageParserDirectBasePassive();
            parser.rurl = Util.extractFileName(pageFileFullPath);

            if (Config.False)
            {
                if (!parser.rurl.equals("253432.html"))
                    continue;
                Util.noop();
            }

            parser.pageSource = Util.readFileAsString(pageFileFullPath);
            parser.parseHtml(parser.pageSource);

            if (parser.downloadExternalLinks(parser.pageRoot, linksDir))
            {
                String newPageSource = JSOUP.emitHtml(parser.pageRoot);
                Util.writeToFileSafe(pageFileFullPath, newPageSource);
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
