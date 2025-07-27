package my.LJExport.maintenance;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.parallel.twostage.parser.ParserParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.parser.ParserWorkContext;
import my.LJExport.runtime.synch.ThreadsControl;
import my.LJExport.runtime.url.AwayLinks;
import my.LJExport.runtime.url.UrlUtil;

public class MainForAllHtmlFiles
{
    private static final String ALL_USERS = "<all>";
    // private static final String AllUsersFromUser = "kot_begemott";
    private static final String AllUsersFromUser = null;

    private static final String Users = ALL_USERS;
    // private static final String Users = "oboguev";
    // private static final String Users = "nationalism.org";
    // private static final String Users = "harmfulgrumpy.dreamwidth-org,udod99.lj-rossia-org";

    private static int ParallelismDefault = 20;
    private static int ParallelismMonthly = 5;
    private static final boolean ShowProgress = false;

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
            // HttpWireTracing.enable();

            do_users(Users);

            Util.out("");
            Util.out(">>> Completed for all requested users and their files");
        }
        catch (Exception ex)
        {
            Util.err("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
            Main.emergency_logout();
        }

        Main.playCompletionSound();
    }

    private static void do_users(String users) throws Exception
    {
        /* can be set in debugger */
        boolean forceExitNow = false;

        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.init("");

        if (Util.False)
        {
            Web.init();
            Main.do_login();
            RateLimiter.LJ_IMAGES.setRateLimit(100);
        }

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

            /* forced exit through debugger */
            Util.unused(forceExitNow);
            if (forceExitNow)
                break;

            if (nuser++ != 0)
            {
                Util.out("");
                Util.out("=====================================================================================");
                Util.out("");
            }

            try
            {
                MainForAllHtmlFiles self = new MainForAllHtmlFiles();
                self.do_user(user);
            }
            finally
            {
                ThreadsControl.shutdownAfterUser();
            }
        }

        if (Util.False)
        {
            Main.do_logout();
            Web.shutdown();
        }
    }

    private void do_user(String user) throws Exception
    {
        try
        {
            Config.User = user;
            Config.mangleUser();
            Config.autoconfigureSite();

            Util.out(">>> Processing for user " + Config.User);

            processDir("pages", ParallelismDefault);
            processDir("reposts", ParallelismDefault);
            processDir("monthly-pages", ParallelismMonthly);
            processDir("monthly-reposts", ParallelismMonthly);
            processDir("profile", ParallelismDefault);
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    private void processDir(String which, int parallelism) throws Exception
    {
        final String userRoot = Config.DownloadRoot + File.separator + Config.User;
        final String htmlPagesRootDir = userRoot + File.separator + which;

        File fp = new File(htmlPagesRootDir).getCanonicalFile();
        if (!fp.exists() || !fp.isDirectory())
        {
            if (which.equals("pages"))
                Util.err("Missing directory " + fp.getCanonicalPath());
            return;
        }

        Util.out(String.format(">>> Processing [%s] directory %s", Config.User, which));

        ParserParallelWorkContext ppwc = new ParserParallelWorkContext(Util.enumerateAnyHtmlFiles(htmlPagesRootDir),
                htmlPagesRootDir,
                parallelism);

        try
        {
            for (ParserWorkContext wcx : ppwc)
            {
                if (ShowProgress)
                    Util.out("Processing styles for " + wcx.fullFilePath);

                Exception ex = wcx.getException();
                if (ex != null)
                    throw new Exception("While processing " + wcx.fullFilePath, ex);

                Objects.requireNonNull(wcx.parser, "parser is null");
                processHtmlFile(wcx.fullFilePath, wcx.relativeFilePath, wcx.parser, wcx.parser.getCachedPageFlat());
            }
        }
        finally
        {
            ppwc.close();
        }
    }

    private void processHtmlFile(String fullFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        // listAwayLinks(fullFilePath, relativeFilePath, parser, pageFlat);
        unwrapAwayLinks(fullFilePath, relativeFilePath, parser, pageFlat);
    }

    @SuppressWarnings("unused")
    private void listAwayLinks(String fullFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        final String prefix = "https://www.livejournal.com/away?to=";

        for (Node n : JSOUP.findElements(pageFlat, "a"))
        {
            String href = JSOUP.getAttribute(n, "href");
            if (href != null && href.startsWith(prefix))
            {
                href = href.substring(prefix.length());
                String decoded_href = UrlUtil.decodeUrl(href);
                Util.out("AWAY: " + decoded_href);
            }
        }
    }

    @SuppressWarnings("unused")
    private void unwrapAwayLinks(String fullFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        boolean updated = false;

        for (Node n : JSOUP.findElements(pageFlat, "a"))
            updated |= AwayLinks.unwrapAwayLink(n, "href");

        if (updated)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullFilePath, html);
        }
    }
}