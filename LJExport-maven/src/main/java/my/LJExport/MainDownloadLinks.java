package my.LJExport;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.runtime.ActivityCounters;
import my.LJExport.runtime.LinkDownloader;
import my.LJExport.runtime.RateLimiter;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
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

    private static String User = "harmfulgrumpy";
    // private static String User = "alex_vergin";
    // private static String User = "genby";
    // private static String User = "blog_10101";
    // private static String User = "nikital2014";
    // private static String User = "von_hoffmann";

    private static final int NWorkThreads = 10;

    public static void main(String[] args)
    {
        try
        {
            MainDownloadLinks self = new MainDownloadLinks();
            self.do_user(User);
        }
        catch (Exception ex)
        {
            err("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        Main.playCompletionSound();
    }

    private MainDownloadLinks()
    {
    }

    private void do_user(String user) throws Exception
    {
        Config.User = user;
        Config.mangleUser();

        pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "pages";
        linksDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "links";
        // offline = true;

        out(">>> Processing download links for user " + Config.User);

        Util.mkdir(linksDir);
        LinkDownloader.init(linksDir);

        pageFiles = Util.enumerateFiles(pagesDir);
        pageFilesTotalCount = pageFiles.size();

        Config.MaxConnectionsPerRoute = NWorkThreads;
        Web.init();
        ActivityCounters.reset();
        RateLimiter.setRateLimit(100);

        // start worker threads
        List<Thread> vt = new ArrayList<Thread>();
        for (int nt = 0; nt < Math.min(NWorkThreads, pageFilesTotalCount); nt++)
        {
            Thread t = new Thread(new MainDownloadLinksRunnable(this));
            vt.add(t);
            t.start();
        }

        // wait for worker threads to complete
        for (int nt = 0; nt < vt.size(); nt++)
            vt.get(nt).join();

        if (Main.isAborting())
            err(">>> Aborted scanning the journal for user " + Config.User);
        else
            out(">>> Completed scanning the journal for user " + Config.User);
    }

    void do_work() throws Exception
    {
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
                out(String.format(">>> %s (%d/%d)", pageFile, ++this.countFetched, pageFilesTotalCount));
            }

            String pageFileFullPath = pagesDir + File.separator + pageFile;
            Thread.currentThread().setName("page-scanner: scanning " + Config.User + " " + pageFile);

            PageParserDirectBase parser = new PageParserDirectBasePassive();
            parser.rurl = Util.extractFileName(pageFileFullPath);

            if (Config.False)
            {
                if (!parser.rurl.equals("592466.html"))
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

    public static class PageParserDirectBasePassive extends PageParserDirectBase
    {
        public PageParserDirectBasePassive()
        {
            super(new NoPageSource());
        }

        @Override
        public void removeJunk(int flags) throws Exception
        {
            throw new Exception("Not implemented");
        }

        @Override
        public Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception
        {
            throw new Exception("Not implemented");
        }

        @Override
        public void injectComments(Element commentsSection, CommentsTree commentTree) throws Exception
        {
            throw new Exception("Not implemented");
        }

        @Override
        public Element findMainArticle() throws Exception
        {
            throw new Exception("Not implemented");
        }
    }

    public static class NoPageSource implements PageContentSource
    {
        @Override
        public String getPageSource() throws Exception
        {
            throw new Exception("Not implemented");
        }
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
                int priority = (Thread.NORM_PRIORITY + Thread.MIN_PRIORITY) / 2;
                if (priority == Thread.NORM_PRIORITY)
                    priority = Thread.MIN_PRIORITY;
                Thread.currentThread().setPriority(priority);

                main.do_work();
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
}
