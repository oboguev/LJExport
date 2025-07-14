package my.LJExport;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.synch.ThreadsControl;
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
    // private static final String AllUsersFromUser = "fat_yankey";
    private static final String AllUsersFromUser = null;

    // private static final String Users = ALL_USERS;
    private static final String Users = "krylov";
    // private static final String Users = "nationalism.org";

    private static final boolean ShowStylesProgress = true;
    private static final boolean DryRun = true;
    
    private static final StringBuilder errorMessageLog = new StringBuilder(); 

    public static void main(String[] args)
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
            // HttpWireTracing.enable();
            do_users(Users);
            
            if (errorMessageLog.length() != 0)
            {
                Util.err("");
                Util.err("************** STYLE RESOURCE ERRORS ************** ");
                Util.err("");
                Util.err(errorMessageLog.toString());
                Util.err("");
                Util.err("************** END OF STYLE RESOURCE ERRORS ************** ");
            }
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

            if (Config.False)
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
                MainStylesToLocal self = new MainStylesToLocal();
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

            Util.out(">>> Making HTML styles locally cached for user " + Config.User);

            HtmlFileBatchProcessingContext batchContext = new HtmlFileBatchProcessingContext();

            processDir("pages", batchContext);
            processDir("reposts", batchContext);
            processDir("monthly-pages", batchContext);
            processDir("monthly-reposts", batchContext);
            processDir("profile", batchContext);

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

    private void processDir(String which, HtmlFileBatchProcessingContext batchContextAll) throws Exception
    {
        final String userRoot = Config.DownloadRoot + File.separator + Config.User;
        final String styleCatalogDir = userRoot + File.separator + "styles";
        final String dir = userRoot + File.separator + which;
        
        String styleFallbackDir = null;
        if (!Config.User.contains("."))
            styleFallbackDir = Config.DownloadRoot + File.separator + "@livejournal-styles";

        if (!which.equals("pages"))
        {
            File fp = new File(dir).getCanonicalFile();
            if (!fp.exists() || !fp.isDirectory())
                return;
        }

        Util.out(String.format(">>> Scanning [%s] directory %s", Config.User, which));

        HtmlFileBatchProcessingContext batchContext = new HtmlFileBatchProcessingContext();
        StyleProcessor.processAllHtmlFiles(styleCatalogDir, styleFallbackDir, dir, StyleProcessorAction.TO_LOCAL, null, ShowStylesProgress, DryRun,
                batchContext, errorMessageLog);

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
