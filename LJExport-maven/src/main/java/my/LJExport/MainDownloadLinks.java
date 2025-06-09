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
import my.LJExport.runtime.RateLimiter;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.xml.JSOUP;

/*
 * Загрузить отсутствующие локальные копии сссылок в страницах пользователя.
 * Они могут быть пропущены, если в момент загрузки страниц севрер, содержаший ссылки,
 * не работал.
 */
public class MainDownloadLinks extends PageParserDirectBase  
{
    private String pagesDir;
    private String linksDir;
    private List<String> pageFiles;
    private int pageFilesTotalCount;
    private int countFetched = 0;

    private static String User = "sergeytsvetkov";

    private static final int NWorkThreads = 8;

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

    }
    
    private MainDownloadLinks()
    {
        super(new NoPageSource());
    }

    private void do_user(String user) throws Exception
    {
        linksDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "links";
        // offline = true;

        Config.User = user;
        out(">>> Processing download links for user " + Config.User);

        pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "pages";

        pageFiles = Util.enumerateFiles(pagesDir);
        pageFilesTotalCount = pageFiles.size();

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
            pageSource = Util.readFileAsString(pageFileFullPath);
            parseHtml(pageSource);

            if (downloadExternalLinks(pageRoot, linksDir))
            {
                String newpageSource = JSOUP.emitHtml(pageRoot);
                Util.writeToFileSafe(pageFileFullPath, newpageSource);
            }
        }
    }

    /* =============================================================== */

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
    
    public static class NoPageSource implements PageContentSource
    {
        @Override
        public String getPageSource() throws Exception
        {
            throw new Exception("Not implemented");
        }
    }
}
