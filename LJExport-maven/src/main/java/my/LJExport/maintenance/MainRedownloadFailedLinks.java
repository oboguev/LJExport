package my.LJExport.maintenance;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.maintenance.handlers.MaintenanceHandler;
import my.LJExport.maintenance.handlers.MaintenanceHandlerPassive;
import my.LJExport.maintenance.handlers.MaintenanceHandler.LinkInfo;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.IdentityWrapper;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.ActivityCounters;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.ShouldDownload;
import my.LJExport.runtime.links.SmartLinkDownloader;
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
    private static final String ALL_USERS = "<all>";
    private static final String AllUsersFromUser = null;
    // private static final String AllUsersFromUser = "schloenski";

    // private static final String Users = ALL_USERS;
    // private static final String Users = "funt";
    // private static final String Users = "krylov_arhiv,krylov";
    private static final String Users = "oboguev";
    // private static final String Users = "udod99.lj-rossia-org,harmfulgrumpy.dreamwidth-org,nationalism.org";

    private static boolean DryRun = false;

    private static boolean UseArchiveOrg = true;
    private static boolean UseLivejournal = false;

    /* =============================================================================== */

    /* we can use large number of threads because they usually are network IO bound */
    private static final int NWorkThreadsDownload = 1 /*300*/;
    private static final int NMaxWorkThreadsHtmlFiles = 70;
    private static final int MaxConnectionsPerRoute = 10;

    /* =============================================================================== */

    private String userRoot;
    private String linksDir;

    private KVFile kvfile;
    private List<KVEntry> kvlist;

    Set<IdentityWrapper<KVEntry>> kvset_remaining = new LinkedHashSet<>();

    private List<KVEntry> kvlist_good = new ArrayList<>();
    private Map<String, KVEntry> kvmap_good = new HashMap<>();

    private List<KVEntry> kvlist_failed = new ArrayList<>();
    private Map<String, KVEntry> kvmap_failed = new HashMap<>();

    private Map<String, String> file_lc2ac = new HashMap<>();

    private Set<String> failedUrls = new HashSet<>();

    /* =============================================================================== */

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
            if (UseLivejournal)
                Main.do_login();

            userRoot = Config.DownloadRoot + File.separator + Config.User;
            linksDir = userRoot + File.separator + "links";
            kvfile = new KVFile(linksDir + File.separator + "failed-link-downloads.txt");

            Util.out(">>> Redownloading of failed link files for user " + Config.User);

            build_lc2ac();

            if (!redownloadLinkFiles())
                return;

            if (Main.isAborting())
            {
                Util.err(">>> Aborted redownloading of failed link files for user " + Config.User);
                saveControlFile(true);
                return;
            }

            if (Config.False)
            {
                updateUserHtmlFiles();

                if (Main.isAborting())
                {
                    Util.err(">>> Aborted redownloading of failed link files for user " + Config.User);
                    saveControlFile(true);
                    return;
                }
            }

            saveControlFile(true);
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    private void saveControlFile(boolean finalSave) throws Exception
    {
        if (kvset_remaining == null)
        {
            Util.err("kvset_remaining is null");
            return;
        }

        synchronized (kvset_remaining)
        {
            List<KVEntry> list = new ArrayList<>();

            for (IdentityWrapper<KVEntry> wrap : kvset_remaining)
                list.add(wrap.get());

            int nremaining = list.size();

            if (finalSave)
            {
                int nloaded = this.kvlist_good.size();
                Util.out("");
                Util.out(String.format("Files redownloaded: %d, remaining %s", nloaded, nremaining));
            }

            if (DryRun)
            {
                if (finalSave)
                {
                    Util.out("DRY RUN: failed-link-downloads.txt will not be updated");
                }
            }
            else if (nremaining == 0)
            {
                kvfile.delete();
                if (finalSave)
                {
                    Util.out("All scheduled link files have been downloaded, deleted failed-link-downloads.txt for user "
                            + Config.User);
                }
            }
            else
            {
                KVEntry.sortByValueIgnoreCase(list);
                kvfile.save(list);
                if (finalSave)
                {
                    Util.out("Updated failed-link-downloads.txt for user " + Config.User + " with remaining files");
                }
            }
        }
    }

    private String numfiles(int n)
    {
        if (n == 1)
            return "1 file";
        else
            return "" + n + " files";
    }

    /* =================================================================================================== */

    private void build_lc2ac() throws Exception
    {
        file_lc2ac = new HashMap<>();

        MaintenanceHandler mh = new MaintenanceHandlerPassive();

        for (String fp : Util.enumerateFiles(linksDir, null))
        {
            if (mh.isLinksRootFileRelativePathSyntax(fp))
                continue;

            fp = linksDir + File.separator + fp;
            file_lc2ac.put(fp.toLowerCase(), fp);
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
            Util.out(String.format("User %s has %s scheduled to redownload",
                    Config.User, numfiles(kvlist.size())));

            for (KVEntry entry : kvlist)
                kvset_remaining.add(new IdentityWrapper<>(entry));

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

            if (redownload(image, url, relpath, referer))
            {
                int ncompleted;

                synchronized (kvlist)
                {
                    kvlist_good.add(entry);
                    ncompleted = kvlist_good.size();
                }

                synchronized (kvset_remaining)
                {
                    kvset_remaining.remove(new IdentityWrapper<>(entry));
                    if ((ncompleted % 20) == 0)
                        saveControlFile(false);
                }
            }
            else
            {
                synchronized (kvlist)
                {
                    kvlist_failed.add(entry);
                }
            }
        }
    }

    /* ========================================================================================== */

    /*
     * This code section is disabled.
     * Update of original-xxx is no longer is done here, but rather in DetectFailedDownloads.
     */

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
        parser.rid = parser.rurl = Util.extractFileName(fullHtmlFilePath);
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

            if (href == null || !mh.isLinksRepositoryReference(fullHtmlFilePath, href))
                continue;

            if (mh.isArchiveOrg())
            {
                /* ignore bad links due to former bug in archive loader */
                if (href.startsWith("../") && href.endsWith("../links/null"))
                    continue;
            }

            LinkInfo linkInfo = mh.linkInfo(fullHtmlFilePath, href);
            if (linkInfo == null)
                continue;

            String ac = file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
            if (ac == null)
            {
                String msg = String.format("Link file/dir [%s] is not present in the repository map, href=[%s], filepath=[%s]",
                        Config.User, href, linkInfo.linkFullFilePath);

                boolean allow = Config.User.equals("d_olshansky") && href.contains("../links/imgprx.livejournal.net/");

                if (DryRun || allow)
                {
                    Util.err(msg);
                    continue;
                }
                else
                {
                    throwException(msg);
                }
            }

            if (!ac.equals(linkInfo.linkFullFilePath))
                throwException("Mismatching link case");

            String relpath = mh.abs2rel(linkInfo.linkFullFilePath);

            KVEntry e_good = kvmap_good.get(relpath.toLowerCase());
            KVEntry e_failed = kvmap_failed.get(relpath.toLowerCase());

            if (e_good != null && e_failed != null)
                throwException("Entry is both on good or failed lists");

            if (e_good != null)
            {
                if (null == JSOUP.getAttribute(n, "original-" + attr))
                {
                    // e.key is already normalized in file and does not need to be URL-encoded
                    JSOUP.setAttribute(n, "original-" + attr, e_good.key);
                    updated = true;
                }
            }

            if (e_failed != null)
            {
                if (null == JSOUP.getAttribute(n, "original-" + attr))
                {
                    // e.key is already normalized in file and does not need to be URL-encoded
                    JSOUP.setAttribute(n, "original-" + attr, e_failed.key);
                    updated = true;
                }
            }
        }

        return updated;
    }

    /* ========================================================================================== */

    public boolean redownload(boolean image, String url, String relativeLinkFilePath, String referer) throws Exception
    {
        SmartLinkDownloader smartLinkRedownloader = new SmartLinkDownloader(linksDir);
        smartLinkRedownloader.useArchiveOrg(UseArchiveOrg);
        
        if (!ShouldDownload.shouldDownload(image, url))
        {
            Util.out("Skipping " + url);
            return false;
        }

        if (!UseLivejournal && LJUtil.isServerUrl(url))
        {
            Util.out("Skipping " + url);
            return false;
        }

        if (failedUrls.contains(url))
        {
            Util.err(
                    String.format("Quitting [%s] link file %s, previosuly failed url: %s", Config.User, relativeLinkFilePath, url));
            return false;
        }

        MutableObject<String> fromWhere = new MutableObject<>();
        boolean result = smartLinkRedownloader.redownloadToFile(image, url, relativeLinkFilePath, referer, fromWhere);

        if (result)
        {
            String from = "";
            if (fromWhere.get() != null)
                from = " === from " + fromWhere.get();
            Util.out(String.format("Downloaded [%s] link file %s%s", Config.User, relativeLinkFilePath, from));
        }
        else
        {
            Util.err(String.format("Unable to download [%s] link file %s, url: %s", Config.User, relativeLinkFilePath, url));
            failedUrls.add(url);
        }

        return result;
    }

    /* ========================================================================================== */

    private static void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }
}