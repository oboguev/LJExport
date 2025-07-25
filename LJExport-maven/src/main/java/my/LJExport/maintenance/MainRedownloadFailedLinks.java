package my.LJExport.maintenance;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.maintenance.handlers.MaintenanceHandler;
import my.LJExport.maintenance.handlers.MaintenanceHandlerPassive;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.ActivityCounters;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.LinkRedownloader;
import my.LJExport.runtime.links.util.LinkFilepath;
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

    private List<KVEntry> kvlist_good = new ArrayList<>();
    private Map<String, KVEntry> kvmap_good = new HashMap<>();

    private List<KVEntry> kvlist_failed = new ArrayList<>();
    private Map<String, KVEntry> kvmap_failed = new HashMap<>();

    // private List<KVEntry> kvlist_all = new ArrayList<>();

    private static final String ALL_USERS = "<all>";
    private static final String AllUsersFromUser = null;
    // private static final String AllUsersFromUser = "tanya_mass";

    private static final String Users = ALL_USERS;
    // private static final String Users = "funt";
    // private static final String Users = "krylov_arhiv,krylov";
    // private static final String Users = "zhu_s";

    /* we can use large number of threads because they usually are network IO bound */
    private static final int NWorkThreadsDownload = 300;
    private static final int NMaxWorkThreadsHtmlFiles = 70;
    private static final int MaxConnectionsPerRoute = 10;

    private static boolean DryRun = true;

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

        Config.NWorkThreads = NWorkThreadsDownload;
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

            Util.out(">>> Redownloading of failed links for user " + Config.User);

            if (!redownloadLinkFiles())
                return;

            if (Main.isAborting())
            {
                Util.err(">>> Aborted redownloading of failed links for user " + Config.User);
                return;
            }

            updateUserHtmlFiles();
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

        if (kvlist == null || kvlist.size() == 0)
        {
            Util.out("User " + Config.User + " has no files scheduled to redownload");
            return false;
        }
        else
        {
            // verify no duplicate entries for file paths
            KVFile.reverseMap(kvlist, true);

            int parallelism = Math.min(NWorkThreadsDownload, kvlist.size());
            runWorkers(parallelism, WorkType.RedownloadLinkFiles);
            return true;
        }
    }

    private void runWorkers(int parallelism, WorkType workType) throws Exception
    {
        // start worker threads
        ThreadsControl.workerThreadGoEventFlag.clear();
        ThreadsControl.activeWorkerThreadCount.set(0);

        List<Thread> vt = new ArrayList<Thread>();
        for (int nt = 0; nt < parallelism; nt++)
        {
            Thread t = new Thread(new RedownloadRunnable(this, workType));
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
    }

    public static enum WorkType
    {
        RedownloadLinkFiles, UpdateHtmlFiles
    }

    public static class RedownloadRunnable implements Runnable
    {
        private final MainRedownloadFailedLinks main;
        private final WorkType workType;

        public RedownloadRunnable(MainRedownloadFailedLinks main, WorkType workType)
        {
            this.main = main;
            this.workType = workType;
        }

        public void run()
        {
            try
            {
                ThreadsControl.backgroundStarting();
                Thread.currentThread().setName("worker");
                switch (workType)
                {
                case RedownloadLinkFiles:
                    main.doRedownloadLinkFiles();
                    break;

                case UpdateHtmlFiles:
                    main.doUpdateHtmlFiles();
                    break;
                }
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

    private void doRedownloadLinkFiles() throws Exception
    {
        ThreadsControl.workerThreadGoEventFlag.waitFlag();

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
            else
            {
                throw new Exception("Invalid control file format");
            }

            String referer = LJUtil.userBase();
            if (Config.isLiveJournal() || Config.isDreamwidthOrg() || Config.isRossiaOrg())
            {
                int random = (int) (Math.random() * (7000000 - 1000000 + 1)) + 1000000;
                referer += String.format("/%07d.html", random);
            }

            if (redownload(url, relpath, referer, image))
            {
                synchronized (kvlist)
                {
                    kvlist_good.add(entry);
                    // kvlist_all.add(entry);
                }
            }
            else
            {
                synchronized (kvlist)
                {
                    kvlist_failed.add(entry);
                    // kvlist_all.add(entry);
                }
            }
        }
    }

    /* ========================================================================================== */

    private List<String> htmlFilesList;

    private void updateUserHtmlFiles() throws Exception
    {
        kvmap_good = KVFile.reverseMap(kvlist_good, true);
        kvmap_failed = KVFile.reverseMap(kvlist_failed, true);

        List<String> list = new ArrayList<>();

        addDirFiles(list, "pages");
        addDirFiles(list, "reposts");
        addDirFiles(list, "profile");

        if (Util.False)
        {
            addDirFiles(list, "monthly-pages");
            addDirFiles(list, "monthly-reposts");
        }

        htmlFilesList = list;

        int parallelism = Runtime.getRuntime().availableProcessors() * Config.ThreadsPerCPU;
        parallelism = Math.min(parallelism, NMaxWorkThreadsHtmlFiles);
        Config.prepareThreading(parallelism);

        runWorkers(parallelism, WorkType.UpdateHtmlFiles);
    }

    private void addDirFiles(List<String> list, String which) throws Exception
    {
        final String htmlPagesRootDir = userRoot + File.separator + which;

        File fpRootDir = new File(htmlPagesRootDir).getCanonicalFile();
        if (!fpRootDir.exists() || !fpRootDir.isDirectory())
        {
            if (which.equals("pages"))
                Util.err("Missing directory " + fpRootDir.getCanonicalPath());
            return;
        }

        List<String> enumeratedFiles = Util.enumerateAnyHtmlFiles(htmlPagesRootDir);

        for (String fpath : enumeratedFiles)
            list.add(this.userRoot + File.separator + which + File.separator + fpath);
    }

    private void doUpdateHtmlFiles() throws Exception
    {
        ThreadsControl.workerThreadGoEventFlag.waitFlag();

        for (;;)
        {
            String fullHtmlFilePath;

            if (Main.isAborting())
                return;

            synchronized (htmlFilesList)
            {
                if (htmlFilesList.size() == 0)
                    return;
                fullHtmlFilePath = htmlFilesList.remove(0);
            }

            processHtmlFile(fullHtmlFilePath);
        }
    }

    private void processHtmlFile(String fullHtmlFilePath) throws Exception
    {
        PageParserDirectBasePassive parser = new PageParserDirectBasePassive();
        parser.rid = parser.rurl = null;
        parser.pageSource = Util.readFileAsString(fullHtmlFilePath);
        parser.parseHtml(parser.pageSource);
        processHtmlFile(fullHtmlFilePath, parser, JSOUP.flatten(parser.pageRoot));
    }

    private void processHtmlFile(String fullHtmlFilePath, PageParserDirectBasePassive parser, List<Node> pageFlat) throws Exception
    {
        boolean updated = false;

        updated |= process(fullHtmlFilePath, parser, pageFlat, "a", "href");
        updated |= process(fullHtmlFilePath, parser, pageFlat, "img", "src");

        if (updated && !DryRun)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullHtmlFilePath, html);
        }
    }

    private boolean process(String fullHtmlFilePath, PageParserDirectBasePassive parser, List<Node> pageFlat,
            String tag, String attr) throws Exception
    {
        boolean updated = false;
        
        MaintenanceHandler mh = new MaintenanceHandlerPassive();

        for (Node n : JSOUP.findElements(pageFlat, tag))
        {
            String href = mh.getLinkAttribute(n, attr);
            String href_original = mh.getLinkOriginalAttribute(n, "original-" + attr);

            // ### in kvlist_good
            // ### OK -> remove from kvlist file
            // ### add original-attr if missing

            // ### in kvlist_failed
            // ### cannot reload -> restore original URL in HTML links
        }

        return updated;
    }

    /* ========================================================================================== */

    public boolean redownload(String url, String relativeLinkFilePath, String referer, boolean image) throws Exception
    {
        LinkRedownloader linkRedownloader = new LinkRedownloader(linksDir);

        if (!LinkDownloader.shouldDownload(url, false))
            return false;

        // ### use smart link redownloader

        return linkRedownloader.redownload(url, relativeLinkFilePath, referer, image);
    }
}