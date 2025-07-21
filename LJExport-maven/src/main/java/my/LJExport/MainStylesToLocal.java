package my.LJExport;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.ErrorMessageLog;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.synch.ThreadsControl;
import my.LJExport.runtime.ui.UIDialogQuestion;
import my.LJExport.styles.HtmlFileBatchProcessingContext;
import my.LJExport.styles.StyleProcessor;
import my.LJExport.styles.StyleProcessor.StyleProcessorAction;

/*
 * Make styles in html pages to become locally cached,
 * rather than fetched from remote server.
 * 
 * Use stack -Xss32m
 */
public class MainStylesToLocal
{
    private static final String ALL_USERS = "<all>";
    // private static final String AllUsersFromUser = "kot_begemott";
    private static final String AllUsersFromUser = null;

    private static final String Users = ALL_USERS;
    // private static final String Users = "oboguev";
    // private static final String Users = "nationalism.org";

    private static final boolean ShowStylesProgress = true;
    private static final boolean DryRun = true;

    private static final ErrorMessageLog errorMessageLog = new ErrorMessageLog();

    private static int ParallelismDefault = 20;
    private static int ParallelismMonthly = 5;

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
            // HttpWireTracing.enable();

            do_users(Users);
        }
        catch (Exception ex)
        {
            Util.err("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
            Main.emergency_logout();
        }

        if (errorMessageLog.length() != 0)
        {
            Util.err("");
            Util.err("************** STYLE RESOURCE ERRORS ************** ");
            Util.err("");
            Util.err(errorMessageLog.toString());
            Util.err("");
            Util.err("************** END OF STYLE RESOURCE ERRORS ************** ");
        }

        Main.playCompletionSound();
    }

    private static void do_users(String users) throws Exception
    {
        if (!DryRun)
        {
            String response = "Отменить";
            
            try
            {
                String mult = users.contains(",") ? "ей" : "я";
                
                String questionText = String.format(
                        "Точно ли вы желаете переменить стили с удалённых на архивированные для пользовател%s %s ?",
                        mult,
                        users.replace(",", ", "));
                response = UIDialogQuestion.askQuestion(questionText, "Отменить", "Да", "Отменить");
            }
            catch (Exception ex)
            {
                Util.out(">>> Диалог закрыт, отмена операции");
                return;
            }
            
            if (!response.equals("Да"))
            {
                Util.out(">>> Отмена");
                return;
            }
        }

        /* can be set in debugger */
        boolean forceExitNow = false;

        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.init("");
        Web.init();
        Main.do_login();
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
                MainStylesToLocal self = new MainStylesToLocal();
                self.do_user(user);
            }
            finally
            {
                ThreadsControl.shutdownAfterUser();
            }

            if (errorMessageLog.length() != 0)
            {
                File fp = new File(Config.DownloadRoot).getCanonicalFile().getParentFile();
                fp = new File(fp, "MainStylesToLocal.log").getCanonicalFile();
                StringBuilder sb = new StringBuilder("Time: " + Util.timeNow() + "\n\n");
                sb.append(errorMessageLog);
                Util.writeToFileSafe(fp.getCanonicalPath(), sb.toString());
                Util.out(">>> Saved accumulated error message log to file " + fp.getCanonicalPath());
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

            Util.out(">>> Making HTML styles locally cached for user " + Config.User);

            HtmlFileBatchProcessingContext batchContext = new HtmlFileBatchProcessingContext();

            processDir("pages", batchContext, ParallelismDefault);
            processDir("reposts", batchContext, ParallelismDefault);
            processDir("monthly-pages", batchContext, ParallelismMonthly);
            processDir("monthly-reposts", batchContext, ParallelismMonthly);
            processDir("profile", batchContext, ParallelismDefault);

            Main.out(">>> Completed making HTML styles locally cached for user " + Config.User);
            String remark = DryRun ? " (DRY RUN)" : "";

            Util.out(String.format(">>> Total files scanned: %d, updated in memory: %d, updated on disk: %d%s",
                    batchContext.scannedHtmlFiles.get(),
                    batchContext.updatedHtmlFiles.get(),
                    batchContext.savedHtmlFiles.get(),
                    remark));
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    private void processDir(String which, HtmlFileBatchProcessingContext batchContextAll, int parallelism) throws Exception
    {
        final String userRoot = Config.DownloadRoot + File.separator + Config.User;
        final String styleCatalogDir = userRoot + File.separator + "styles";
        final String dir = userRoot + File.separator + which;

        String styleFallbackDir = null;
        if (Config.isLiveJournal())
            styleFallbackDir = Config.DownloadRoot + File.separator + "@livejournal-styles";
        else
            Util.err(String.format("Processing NOT for LiveJournal, user %s, site %s", Config.User, Config.Site));

        if (!which.equals("pages"))
        {
            File fp = new File(dir).getCanonicalFile();
            if (!fp.exists() || !fp.isDirectory())
                return;
        }

        Util.out(String.format(">>> Scanning [%s] directory %s", Config.User, which));

        HtmlFileBatchProcessingContext batchContext = new HtmlFileBatchProcessingContext();
        StyleProcessor.processAllHtmlFiles(styleCatalogDir, styleFallbackDir, dir, StyleProcessorAction.TO_LOCAL, null,
                ShowStylesProgress, DryRun,
                batchContext, errorMessageLog,
                parallelism);

        String remark = DryRun ? " (DRY RUN)" : "";
        Util.out(String.format(">>> Completed [%s] directory %s, files scanned: %d, updated in memory: %d, updated on disk: %d%s",
                Config.User,
                which,
                batchContext.scannedHtmlFiles.get(),
                batchContext.updatedHtmlFiles.get(),
                batchContext.savedHtmlFiles.get(),
                remark));

        batchContextAll.add(batchContext);
    }
}
