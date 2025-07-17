package my.LJExport.maint;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.maint.handlers.CheckLinkCaseConflicts;
import my.LJExport.maint.handlers.CountFiles;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.parallel.twostage.parser.ParserParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.parser.ParserWorkContext;
import my.LJExport.runtime.synch.ThreadsControl;
import my.LJExport.runtime.ui.ConsoleProgress;

public class Maintenance
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

    public static int TotalFileCount = 0;
    private static ConsoleProgress consoleProgress = null;
    private static int stageProcessedFileCount = 0;

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
            // HttpWireTracing.enable();

            do_users(Users, new CountFiles());
            do_users(Users, CheckLinkCaseConflicts.class);

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

    private static void do_users(String users, Maintenance handler) throws Exception
    {
        do_users(users, handler, null);
    }

    private static void do_users(String users, Class<? extends Maintenance> clz) throws Exception
    {
        do_users(users, null, clz);
    }

    private static void do_users(String users, Maintenance handler, Class<? extends Maintenance> clz) throws Exception
    {
        /* can be set in debugger */
        boolean forceExitNow = false;

        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.init("");

        if (Config.False)
        {
            Web.init();
            Main.do_login();
            RateLimiter.LJ_IMAGES.setRateLimit(100);
        }

        Maintenance exec = null;

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
            Util.unused(forceExitNow);
            if (forceExitNow)
                break;

            try
            {
                if (handler != null)
                    exec = handler;
                else
                    exec = clz.newInstance();

                if (nuser == 0)
                    exec.beginUsers();

                if (nuser != 0)
                    exec.printDivider();

                exec.do_user(user);
            }
            finally
            {
                ThreadsControl.shutdownAfterUser();
            }

            nuser++;
        }

        if (exec != null)
            exec.endUsers();

        if (Config.False)
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

            beginUser();

            processDir("pages", ParallelismDefault);
            processDir("reposts", ParallelismDefault);
            processDir("monthly-pages", ParallelismMonthly);
            processDir("monthly-reposts", ParallelismMonthly);
            processDir("profile", ParallelismDefault);

            endUser();
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    /* ================================================= */

    protected void beginUsers()
    {
        beginUsers("Processing HTML files");
    }

    protected void beginUsers(String title)
    {
        if (consoleProgress == null)
        {
            consoleProgress = new ConsoleProgress(title);
            consoleProgress.begin();
        }
        
        stageProcessedFileCount = 0;
    }

    protected void endUsers()
    {
        if (consoleProgress != null)
        {
            consoleProgress.end();
            consoleProgress = null;
        }
    }

    protected void beginUser()
    {
        Util.out(">>>     Processing user " + Config.User);
    }

    protected void endUser()
    {
    }

    protected boolean onEnumFiles(String which, List<String> enumeratedFiles)
    {
        Util.out(String.format(">>>         Processing [%s] directory %s", Config.User, which));
        return true;
    }

    protected boolean isParallel()
    {
        return true;
    }

    protected void printDivider()
    {
        Util.out("");
        Util.out("=====================================================================================");
        Util.out("");
    }

    protected void processHtmlFile(String fullFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        // Util.out("Processing for " + fullFilePath);

        if (consoleProgress != null)
        {
            double pct = (100.0 * stageProcessedFileCount) / TotalFileCount;
            consoleProgress.update("Processing for user " + Config.User, pct);
        }

        stageProcessedFileCount++;
    }

    /* ================================================= */

    private void processDir(String which, int parallelism) throws Exception
    {
        final String userRoot = Config.DownloadRoot + File.separator + Config.User;
        final String htmlPagesRootDir = userRoot + File.separator + which;

        File fpRootDir = new File(htmlPagesRootDir).getCanonicalFile();
        if (!fpRootDir.exists() || !fpRootDir.isDirectory())
        {
            if (which.equals("pages"))
                Util.err("Missing directory " + fpRootDir.getCanonicalPath());
            return;
        }

        List<String> enumeratedFiles = Util.enumerateAnyHtmlFiles(htmlPagesRootDir);

        if (!onEnumFiles(which, enumeratedFiles))
            return;

        if (isParallel())
        {

            ParserParallelWorkContext ppwc = new ParserParallelWorkContext(enumeratedFiles, htmlPagesRootDir, parallelism);

            try
            {
                for (ParserWorkContext wcx : ppwc)
                {
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
        else
        {
            for (String relativeFilePath : enumeratedFiles)
            {
                File fp = new File(fpRootDir, relativeFilePath).getCanonicalFile();
                String fullFilePath = fp.getCanonicalPath();
                PageParserDirectBasePassive parser = new PageParserDirectBasePassive();
                parser.rid = parser.rurl = null;
                parser.pageSource = Util.readFileAsString(fullFilePath);
                parser.parseHtml(parser.pageSource);
                processHtmlFile(fullFilePath, relativeFilePath, parser, parser.getCachedPageFlat());
            }
        }
    }
}