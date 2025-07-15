package my.LJExport.runtime.parallel.twostage.parser;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
// import my.LJExport.runtime.http.RateLimiter;
// import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.synch.ThreadsControl;

public class DemoParserParallelWorkContext
{
    private static final String ALL_USERS = "<all>";
    // private static final String AllUsersFromUser = "fritzmorgen";
    private static final String AllUsersFromUser = null;

    private static final String Users = ALL_USERS;
    // private static final String Users = "oboguev";
    // private static final String Users = "nationalism.org";

    /* can be set in debugger */
    public static volatile boolean forceExitNow = false;

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
            // HttpWireTracing.enable();

            Util.out("Time: " + Util.timeNow());
            long ts = System.currentTimeMillis();

            do_users(Users);

            Util.out("Time: " + Util.timeNow());
            ts = System.currentTimeMillis() - ts;
            Util.out(String.format("Duration: %.1f sec", ts / 1000.0));
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
        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.init("");
        // Web.init();
        // Main.do_login();
        // RateLimiter.LJ_IMAGES.setRateLimit(100);

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

            if (Config.False)
            {
                Main.out(user);
                continue;
            }

            /* forced exit through debugger */
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
                DemoParserParallelWorkContext self = new DemoParserParallelWorkContext();
                self.do_user(user);
            }
            finally
            {
                ThreadsControl.shutdownAfterUser();
            }
        }

        // Main.do_logout();
        // Web.shutdown();
    }

    private void do_user(String user) throws Exception
    {
        try
        {
            Config.User = user;
            Config.mangleUser();
            Config.autoconfigureSite();

            Util.out(">>> Demo HTML file parssing for " + Config.User);

            processDir("pages", 40);
            processDir("reposts", 40);
            processDir("monthly-pages", 4);
            processDir("monthly-reposts", 4);
            processDir("profile", 40);

            Main.out(">>> Completed demo parsing HTML files for user " + Config.User);
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

        if (!which.equals("pages"))
        {
            File fp = new File(htmlPagesRootDir).getCanonicalFile();
            if (!fp.exists() || !fp.isDirectory())
                return;
        }

        Util.out(String.format(">>> Scanning [%s] directory %s", Config.User, which));

        if (Config.False)
        {
            for (String relPath : Util.enumerateAnyHtmlFiles(htmlPagesRootDir))
            {
                String path = htmlPagesRootDir + File.separator + relPath;
                Util.out("Processing for " + path);

                PageParserDirectBasePassive parser = new PageParserDirectBasePassive();
                parser.rid = parser.rurl = null;
                parser.pageSource = Util.readFileAsString(path);
                parser.parseHtml(parser.pageSource);
            }

            // cold = 241 warm = 206
        }
        else
        {
            ParserParallelWorkContext ppwc = new ParserParallelWorkContext(Util.enumerateAnyHtmlFiles(htmlPagesRootDir), 
                    new ParserStage1Processor(htmlPagesRootDir),
                    parallelism);
            
            try
            {
                for (ParserWorkContext wcx : ppwc)
                {
                    Util.out("Processing for " + wcx.fullFilePath);
                    Exception ex = wcx.getException();
                    if (ex != null)
                        throw new Exception("While processing " + wcx.fullFilePath, ex);
                    if (wcx.parser == null)
                        throw new Exception("No parser while processing " + wcx.fullFilePath);
                }

                // cold = 70 for parallelism 20/3
                // cold = 40 for parallelism 40/4
            }
            finally
            {
                ppwc.close();
            }
        }
    }
}
