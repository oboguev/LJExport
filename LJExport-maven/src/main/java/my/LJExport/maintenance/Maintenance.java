package my.LJExport.maintenance;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.maintenance.handlers.*;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.ErrorMessageLog;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.TxLog;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.parallel.twostage.parser.ParserParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.parser.ParserWorkContext;
import my.LJExport.runtime.synch.ThreadsControl;
import my.LJExport.runtime.ui.UIProgressDialog;

public class Maintenance
{
    private static final String ALL_USERS = "<all>";
    private static final String AllUsersFromUser = null;
    // private static final String AllUsersFromUser = "alzheimer";

    private static final String Users = ALL_USERS;
    // private static final String Users = "1981dn";
    // private static final String Users = "oboguev";
    // private static final String Users = "a_kaminsky";
    // private static final String Users = "1981dn,1981dn_dn,64vlad,_devol_";
    // private static final String Users = "oboguev";
    // private static final String Users = "";
    // private static final String Users = "harmfulgrumpy.dreamwidth-org,udod99.lj-rossia-org";
    // private static final String Users = "udod99.lj-rossia-org,harmfulgrumpy.dreamwidth-org,nationalism.org";

    private static int ParallelismDefault = 20;
    private static int ParallelismMonthly = 5;

    protected static int TotalFileCount = 0;
    private static UIProgressDialog consoleProgress = null;
    private static int stageProcessedFileCount = 0;

    protected static final String nl = "\n";
    protected static final ErrorMessageLog errorMessageLog = new ErrorMessageLog();
    protected static boolean printErrorMessageLog = true;
    protected static TxLog txLog;
    protected static BufferedWriter traceWriter;

    protected static final int FileTypeDetectionThreads = 100;

    private boolean repeatUser = false;
    private static boolean doLogin = false;

    public static void main(String[] args)
    {
        try
        {
            FileTypeDetector.prepareThreading(FileTypeDetectionThreads);

            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();

            String traceLogPath = Config.DownloadRoot + File.separator + "@admin" + File.separator + "trace.log";
            traceWriter = new BufferedWriter(new FileWriter(traceLogPath, true));
            traceWriter.write("" + nl);
            traceWriter.write("===========================================================================================" + nl);
            traceWriter.write("Maintenance started at " + Util.timeNow() + nl);
            traceWriter.write("" + nl);
            traceWriter.flush();

            txLog = new TxLog(Config.DownloadRoot + File.separator + "@admin" + File.separator + "transaction.log");
            txLog.open();
            txLog.writeLine("");
            txLog.writeLine("===========================================================================================");
            txLog.writeLine("Maintenance started at " + Util.timeNow());
            txLog.writeLine("");

            // HttpWireTracing.enable();

            //
            // Sequence:
            //   0. CheckLinkCaseConflicts [optional]
            //   1. ResolveLinkCaseDifferences 
            //   2. FixDirectoryLinks [test a_bugaev, ночная москва -- check links map file, review txlog]
            //   3. FixLongPaths
            //   4. FixFileExtensions
            //
            do_users(Users, new CountFiles());
            // do_users(Users, CheckLinkCaseConflicts.class);
            // do_users(Users, ResolveLinkCaseDifferences.class);
            // do_users(Users, FixUnencodedLinks.class);
            // do_users(Users, VerifyUnencodedLinks.class);
            // do_users(Users, FixNullLinks.class); // only for nationalism.org
            // do_users(Users, FixDirectoryLinks.class);
            // do_users(Users, FixLongPaths.class);
            do_users(Users, DetectFailedDownloads.class);
            // --- then run MainRedownloadFailedLinks
            // do_users(Users, FixFileExtensions.class);

            txLog.writeLineSafe("");
            txLog.writeLineSafe("Maintenance COMPLETED at " + Util.timeNow());
            txLog.writeLineSafe("");
            txLog.writeLineSafe("===========================================================================================");

            traceWriter.write("" + nl);
            traceWriter.write("Maintenance COMPLETED at " + Util.timeNow() + nl);
            traceWriter.write("" + nl);
            traceWriter.write("===========================================================================================" + nl);
            traceWriter.flush();

            Util.out("");
            Util.out(">>> Completed for all requested users and their files");

            showErrorMessageLogLog();

            Util.out("");
            Util.out(">>> Completed for all requested users and their files");
        }
        catch (Exception ex)
        {
            showErrorMessageLogLog();

            Util.err("");
            Util.err("*** Exception: " + ex.getMessage());
            ex.printStackTrace();

            Main.emergency_logout();

            if (txLog != null && txLog.isOpen())
            {
                txLog.writeLineSafe("");
                txLog.writeLineSafe("*************** ABORTED at " + Util.timeNow());
                txLog.writeLineSafe("Exception: " + ex.getMessage());
                txLog.writeLineSafe("");
                txLog.writeLineSafe(Util.getStackTraceAsString(ex));
                txLog.writeLineSafe("");
                txLog.writeLineSafe("===========================================================================================");
            }
        }
        finally
        {
            Util.safeClose(txLog);
            Util.safeFlush(traceWriter);
            Util.safeClose(traceWriter);
        }

        Main.playCompletionSound();
    }

    private static void showErrorMessageLogLog()
    {
        if (errorMessageLog.length() != 0 && printErrorMessageLog)
        {
            Util.err("");
            Util.err("************** ALL LOGGED ERRORS ************** ");
            Util.err("");
            Util.err(errorMessageLog.toString());
            Util.err("");
            Util.err("************** END OF ALL LOGGED ERRORS ************** ");
            Util.err("");
        }
        else if (errorMessageLog.length() != 0)
        {
            Util.err("");
            Util.err("************** MESSAGE LOG IS NOT PRINTED HERE DUE TO SIZE LIMITS ************** ");
            Util.err("");
        }
    }

    private static void do_users(String users, MaintenanceHandler handler) throws Exception
    {
        do_users(users, handler, null);
    }

    @SuppressWarnings("unused")
    private static void do_users(String users, Class<? extends MaintenanceHandler> clz) throws Exception
    {
        do_users(users, null, clz);
    }

    private static void do_users(String users, MaintenanceHandler handler, Class<? extends MaintenanceHandler> clz) throws Exception
    {
        /* can be set in debugger */
        boolean forceExitNow = false;

        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.init("");

        if (doLogin)
        {
            Web.init();
            Main.do_login();
            RateLimiter.LJ_IMAGES.setRateLimit(100);
        }

        MaintenanceHandler exec = null;

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

            Config.User = user;
            Config.mangleUser();
            Config.autoconfigureSite(doLogin);

            try
            {
                if (handler != null)
                    exec = handler;
                else
                    exec = clz.getDeclaredConstructor().newInstance();

                if (nuser == 0)
                    exec.beginUsers();

                if (nuser != 0)
                    exec.printDivider();

                ((Maintenance) exec).do_user(user);
            }
            finally
            {
                ThreadsControl.shutdownAfterUser();
            }

            nuser++;
        }

        if (exec != null)
            exec.endUsers();

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
            do
            {
                repeatUser = false;

                beginUser();

                processDir("pages", ParallelismDefault);
                processDir("reposts", ParallelismDefault);
                processDir("profile", ParallelismDefault);

                if (Util.False)
                {
                    processDir("monthly-pages", ParallelismMonthly);
                    processDir("monthly-reposts", ParallelismMonthly);
                }

                endUser();
            }
            while (repeatUser);
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    protected void repeatUser()
    {
        repeatUser = true;
    }

    /* ================================================= */

    protected void beginUsers() throws Exception
    {
        beginUsers("Processing HTML files");
    }

    protected void beginUsers(String title) throws Exception
    {
        if (consoleProgress == null)
        {
            consoleProgress = new UIProgressDialog(title);
            consoleProgress.begin();
        }

        stageProcessedFileCount = 0;
    }

    protected void endUsers() throws Exception
    {
        if (consoleProgress != null)
        {
            consoleProgress.end();
            consoleProgress = null;
        }
    }

    protected void beginUser() throws Exception
    {
        Util.out(">>>     Processing user " + Config.User);
    }

    protected void endUser() throws Exception
    {
    }

    protected boolean onEnumFiles(String which, List<String> enumeratedFiles) throws Exception
    {
        Util.out(String.format(">>>         Processing [%s] directory %s", Config.User, which));
        return true;
    }

    protected boolean isParallel() throws Exception
    {
        return true;
    }

    protected void printDivider() throws Exception
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
            consoleProgress.update("Processing user " + Config.User, pct);
        }

        stageProcessedFileCount++;
    }

    protected int getStageProcessedFileCount()
    {
        return stageProcessedFileCount;
    }

    protected void setStageProcessedFileCount(int count)
    {
        stageProcessedFileCount = count;

        if (consoleProgress != null)
        {
            double pct = (100.0 * stageProcessedFileCount) / TotalFileCount;
            consoleProgress.update("Processing user " + Config.User, pct);
        }
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