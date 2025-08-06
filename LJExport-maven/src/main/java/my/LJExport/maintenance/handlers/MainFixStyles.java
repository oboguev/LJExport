package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import com.helger.css.ECSSVersion;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSExpression;
import com.helger.css.decl.CSSExpressionMemberTermURI;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSExpressionMember;
import com.helger.css.reader.CSSReader;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.ErrorMessageLog;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.synch.ThreadsControl;
import my.LJExport.styles.StyleManager;

public class MainFixStyles
{
    private static final String ALL_USERS = "<all>";
    // private static final String AllUsersFromUser = "harmfulgrumpy";
    private static final String AllUsersFromUser = null;

    private static final String Users = ALL_USERS;
    // private static final String Users = "oboguev,alex_vergin,salery,pioneer_lj,genby,andronic,a_bugaev,1981dn,1981dn_dn,chukcheev,rigort,kirovtanin,kornev,bantaputu,zhenziyou,takoe_nebo,von_hoffmann,jlm_taurus,ivanov_petrov,fritzmorgen";
    // private static final String Users = "belan";
    // private static final String Users = "nationalism.org";
    // private static final String Users = "udod99.lj-rossia-org";

    @SuppressWarnings("unused")
    private static final boolean DryRun = true;

    private static final ErrorMessageLog errorMessageLog = new ErrorMessageLog();

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
        /* can be set in debugger */
        boolean forceExitNow = false;

        if (users.equals(ALL_USERS))
        {
            List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
            users = String.join(",", list);
        }

        Config.init("");

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
                MainFixStyles self = new MainFixStyles();
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
    }

    private void do_user(String user) throws Exception
    {
        try
        {
            Config.User = user;
            Config.mangleUser();

            Util.out(">>> Checking/fixing styles locally cached for user " + Config.User);

            String userStyleCatalogDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "styles";
            StyleManager sm = new StyleManager(userStyleCatalogDir, null, true);
            sm.init();
            String userStyleDir = sm.getStyleDir();
            sm.close();

            List<String> cssFiles = Util.enumerateFiles(userStyleDir, Set.of(".css"));
            for (String cssFile : cssFiles)
            {
                cssFile = userStyleDir + File.separator + cssFile;
                checkCssFile(cssFile);
            }
        }
        finally
        {
            ThreadsControl.shutdownAfterUser();
        }
    }

    private void checkCssFile(String cssFile) throws Exception
    {
        String cssText = Util.readFileAsString(cssFile);

        CascadingStyleSheet css = CSSReader.readFromString(cssText, ECSSVersion.CSS30);

        // Walk @import rules
        for (CSSImportRule importRule : css.getAllImportRules())
        {
            String url = importRule.getLocationString();
            examineLink(url);
        }

        // Walk all style rules
        for (CSSStyleRule rule : css.getAllStyleRules())
        {
            for (CSSDeclaration declaration : rule.getAllDeclarations())
            {
                CSSExpression expr = declaration.getExpression();
                if (expr != null)
                {
                    for (ICSSExpressionMember member : expr.getAllMembers())
                    {
                        if (member instanceof CSSExpressionMemberTermURI)
                        {
                            CSSExpressionMemberTermURI uriTerm = (CSSExpressionMemberTermURI) member;
                            String originalUrl = uriTerm.getURIString();
                            examineLink(originalUrl);
                        }
                    }
                }
            }
        }
    }

    private void examineLink(String url)
    {
        if (url == null || url.startsWith("data:"))
            return;
        String lc = url.toLowerCase();

        lc = lc.replace("://l-stat.livejournal.net//", "://l-stat.livejournal.net/");

        switch (lc)
        {
        /* known non-existing files */
        case "https://l-stat.livejournal.net/img/icons/lock-16-gray.gif?v=1":
        case "http://l-stat.livejournal.net/img/icons/lock-16-gray.gif?v=1":
            return;
        
        case "https://l-stat.livejournal.net/img/popup/splash/figure_frank.png":
        case "http://l-stat.livejournal.net/img/popup/splash/figure_frank.png":
            return;
        
        case "https://stat.livejournal.com/img/icons/lock-16-gray.gif?v=1":
        case "http://stat.livejournal.com/img/icons/lock-16-gray.gif?v=1":
            return;

        case "https://l-stat.livejournal.net/stc/upgrade-paid-icon.gif?v=1":
        case "http://l-stat.livejournal.net/stc/upgrade-paid-icon.gif?v=1":
            return;
        }

        if (lc.startsWith("/") || lc.startsWith("http://") || lc.startsWith("https://"))
            Util.out("CSS link: " + url);
    }
}