package my.LJExport;

import java.io.File;

import java.util.List;

import com.gargoylesoftware.htmlunit.BrowserVersion;

import my.LJExport.calendar.YYYY_MM;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.http.TrustAnySSL;
import my.LJExport.runtime.password.PasswordStorage;
import my.LJExport.runtime.ui.UIDialogPassword;

public class Config
{
    /******************************/
    /** USER-SETTABLE PARAMETERS **/
    /******************************/

    /* Login username */
    public static String LoginUser = "oboguev";

    /* List of journals to download (comma or space-separated) */
    public static final String Users = "a_bugaev";
    // public static final String Users = "roineroyce";
    // public static final String Users = "harmfulgrumpy.dreamwidth-org";
    // public static final String Users = "colonelcassad";
    // public static final String Users = "nikital2014,bash_m_ak,genby,olegnemen,eremei,afanarizm,jlm_taurus,corporatelie,wyradhe,nilsky_nikolay,von_hoffmann,a_samovarov,bantaputu,a_kaminsky,d_olshansky,rn_manifesto,ru_bezch,nep2,ego,hokma,laert,haritonov,1981dn,1981dn_dn,bantaputu,polit_ec,zhenziyou,a_bugaev,tor85,oboguev,morky,krylov,rms1,pioneer_lj,holmogor,miguel_kud,colonelcassad,galkovsky,_devol_";
    // public static final String Users = "alex_vergin,sergeytsvetkov,blog_10101"; // new-style journals 

    /* Directory path to store downloaded files */
    // public static final String DownloadRoot = "R:";
    // public static final String DownloadRoot = "/home/sergey/LJExport/journals";
    public static /*final*/ String DownloadRoot = "C:\\LJExport-journals";
    // public static /*final*/ String DownloadRoot = "F:\\WINAPPS\\LJExport\\journals";

    /* Range of dates to download (inclusive) */
    // public static final YYYY_MM LoadSince = null;
    // public static final YYYY_MM LoadTill = null;
    public static final YYYY_MM LoadSince = new YYYY_MM(2010, 1);
    public static final YYYY_MM LoadTill = new YYYY_MM(2012, 2);

    /* Whether to reload files already existing at DownloadRoot */
    public static final boolean ReloadExistingFiles = false;

    /* Rate limits (ms between requests) for LiveJournal */
    public static final int RateLimit_LiveJournal_Calendar = 500;
    public static final int RateLimit_LiveJournal_PageLoad = 1200;
    public static final int RateLimit_LiveJournal_Images = 200;
    public static final int RateLimit_LiveJournal_PageLoad_CoolOff_Requests = 750;
    public static final int RateLimit_LiveJournal_PageLoad_CoolOff_Interval = 80 * 1000;

    /* Rate limits (ms between requests) for web.archive.org */
    public static final int RateLimit_WebArchiveOrg_Requests = 1200;
    public static final int RateLimit_WebArchiveOrg_Requests_CoolOff_Requests = 750;
    public static final int RateLimit_WebArchiveOrg_Requests_CoolOff_Interval = 80 * 1000;
    public static final int WebArchiveOrg_Http429_Cooloff_Interval = 180 * 1000;

    /* Web timeouts */
    public static final int WebConnectTimeout = 1 * 60 * 1000;
    public static final int WebImageReadingSocketTimeout = 1 * 60 * 1000;
    public static final int WebPageReadingSocketTimeout = 7 * 60 * 1000;

    /* Number of threads to use */
    public static int NWorkThreads = 70;
    public static final int ThreadsPerCPU = 2;
    public static final int MaxThreads = 70;
    public static int MaxConnectionsPerRoute = 6; // max concurrent connections per route (i.e. per host)
    public static final int LinkDownloadSpawnThreshold = 30;
    public static final int LinkDownloadThreadsPoolSize = 50;

    /* Proxy server */
    public static String Proxy = null;
    // public static String Proxy = "http://proxy.xxx.com:8080";

    /* Minimum and maximum number of unloadable pages allowed before the download aborts */
    public static final int MinUnloadablePagesAllowed = 20;
    public static final int MaxUnloadablePagesAllowed = 50;

    /**************************/
    /** TECHNICAL PARAMETERS **/
    /**************************/

    public enum WebMethod
    {
        SELENIUM, HTML_UNIT, DIRECT
    };

    public static final WebMethod Method = Config.WebMethod.DIRECT;
    public static boolean AutoconfigureSite = true;
    public static /*final*/ String DefaultSite = "livejournal.com";
    public static String Site = DefaultSite;
    public static String LoginSite = DefaultSite;
    public static boolean StandaloneSite = false;
    public static final String AllowedUrlSites[] = { "livejournal.com", "livejournal.net", "olegmakarenko.ru", "lj.rossia.org",
            "dreamwidth.org" };
    public static boolean UseLogin = true;
    public static boolean StoreLoginPassword = true;
    public static String LoginPassword = null;
    public static String User = null;
    public static String MangledUser = null;
    public static String DreamwidthCaptchaChallenge = "c0:1751749200:25:300:thLfeiOlXxxyf97RHExK:a83d33f6cee8b3b77fe45c4ca3856b0c";
    public static String UserAgent = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1";
    public static String UserAgentAccept = "text/html, application/xhtml+xml, */*";
    public static String UserAgentAccept_Json = "application/json;q=1.0, text/plain;q=0.5";
    public static final String UserAgentAcceptEncoding = "gzip, deflate";
    public static final BrowserVersion HtmlUnitBrowserVersion = BrowserVersion.FIREFOX_38;
    public static final int Timeout = 150; // Selenim page read timeout
    public static String TrustStore = null;
    public static String TrustStorePassword = null;
    public static final int ProxyThreadsPerThread = 10;
    public static final int MaxProxyThreads = 200;
    public static String ProxyBlockingMessage = "The requested website is blocked";
    public static int UnloadablePagesAllowed;
    public static boolean RandomizeLoadOrder = true;
    public static final int StableIntevral = 3000;
    public static boolean ScrapingArchiveOrg = false;

    public static boolean UseFiddler = false;
    public static final String FiddlerTrustStore = null;
    public static String FidlerTrustStorePassword = null;

    public static final boolean TrustAnySSLCertificate = true;

    public static String NullFile = null;

    /* Download linked files of the listed types locally, so they can be accessed offline */
    // public static List<String> DownloadFileTypes = null;
    public static final List<String> DownloadFileTypes = Util
            .asList("jpg,jpeg,gif,png,webp,avif,bmp,svg,pdf,djvu,tif,tiff,txt,doc,docx,odt,rtf,zip,rar,7z,7zip,tar,tgz");
    public static String UserAgentAccept_Download = "image/*, application/pdf, application/x-djvu, application/msword, " +
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document, " +
            "application/rtf, application/zip, application/x-rar-compressed, application/x-tar, " +
            "application/x-7z-compressed, application/vnd.rar, application/octet-stream, */*";

    /*
     * Adding Fiddler certificate:
     * 
     * set JAVA_HOME=C:\jre1.8.0_121
     * set PATH=%PATH%;C:\jre1.8.0_121\bin
     * keytool -import -alias fidder -file C:\Users\sergey\Desktop\FiddlerRoot.cer -keystore C:\jre1.8.0_121\lib\security\cacerts -storepass changeit
     */

    public static void init(String user) throws Exception
    {
        User = user;
        mangleUser();
        autoconfigureSite();

        // http://docs.oracle.com/javase/6/docs/technotes/guides/security/jsse/ReadDebug.html
        // System.setProperty("javax.net.debug", "all");

        if (new File("/dev/null").exists())
            NullFile = "/dev/null";
        else
            NullFile = "NUL";

        if (UseFiddler)
        {
            Proxy = "http://localhost:8888";

            if (TrustStore == null && FiddlerTrustStore != null)
            {
                TrustStore = FiddlerTrustStore;
                TrustStorePassword = FidlerTrustStorePassword;
            }
        }

        if (TrustAnySSLCertificate)
            TrustAnySSL.trustAnySSL();

        if (Method == Config.WebMethod.HTML_UNIT)
        {
            UserAgent = HtmlUnitBrowserVersion.getUserAgent();
            UserAgentAccept = HtmlUnitBrowserVersion.getHtmlAcceptHeader();
        }

        if (NWorkThreads <= 0)
        {
            NWorkThreads = Runtime.getRuntime().availableProcessors() * ThreadsPerCPU;
            NWorkThreads = Math.min(NWorkThreads, MaxThreads);
        }

        prepareThreading();

        // acquireLoginPassword();

        if (UseFiddler && FiddlerTrustStore != null && FidlerTrustStorePassword == null)
        {
            FidlerTrustStorePassword = UIDialogPassword.promptPassword("Enter password for Fiddler trust store");
            if (FidlerTrustStorePassword == null)
                throw new Exception("Unable to get password");

        }

        if (Proxy == null)
            ProxyBlockingMessage = null;
    }

    public static void prepareThreading() throws Exception
    {
        prepareThreading(Config.NWorkThreads);
    }

    public static void prepareThreading(int nthreads) throws Exception
    {
        nthreads = Math.max(nthreads, Config.LinkDownloadSpawnThreshold + Config.LinkDownloadThreadsPoolSize);
        FileTypeDetector.prepareThreading(nthreads + 10);
    }

    public static void acquireLoginPassword() throws Exception
    {
        if (UseLogin && LoginPassword == null)
        {
            if (StoreLoginPassword)
            {
                LoginPassword = PasswordStorage.getPassword();
            }
            else
            {
                promptLoginPassword();
            }

            if (LoginPassword == null)
                throw new Exception("Unable to get password");
        }
    }

    public static void promptLoginPassword() throws Exception
    {
        if (UseLogin && LoginPassword == null)
        {
            LoginPassword = UIDialogPassword.promptPassword("Enter password for [" + LoginUser + "] at " + Site);
            if (LoginPassword == null)
                throw new Exception("Unable to get password");
        }
    }

    public static void mangleUser() throws Exception
    {
        int len = User.length();
        StringBuilder sb = new StringBuilder();

        for (int k = 0; k < len; k++)
        {
            char c = User.charAt(k);
            if (k != 0 && k != len - 1 && c == '_')
                c = '-';
            sb.append(c);
        }

        MangledUser = sb.toString();
    }

    public static boolean isRossiaOrg()
    {
        return Config.Site.equals("lj.rossia.org");
    }

    public static boolean isDreamwidthOrg(String loginSite)
    {
        return loginSite.equals("dreamwidth.org");
    }

    public static boolean isDreamwidthOrg()
    {
        return Config.Site.equals("dreamwidth.org");
    }

    public static boolean isLiveJournal()
    {
        if (isRossiaOrg() || isDreamwidthOrg() || Config.ScrapingArchiveOrg)
            return false;
        else if (Config.User == null || Config.User.length() == 0 || Config.User.contains(".") || Config.User.contains("-"))
            return false;
        else
            return true;
    }

    public static void autoconfigureSite() throws Exception
    {
        if (AutoconfigureSite)
        {
            if (User.contains(".dreamwidth-org"))
            {
                Config.LoginSite = Config.Site = Config.DefaultSite = "dreamwidth.org";
                Config.UseLogin = true;
                // Config.DownloadRoot += ".dreamwidth-org";
            }
            else if (User.contains(".lj-rossia-org"))
            {
                Config.LoginSite = Config.Site = Config.DefaultSite = "lj.rossia.org";
                Config.UseLogin = false;
                // Config.DownloadRoot += ".lj-rossia-org";
            }
            else
            {
                Config.LoginSite = Config.Site = Config.DefaultSite = "livejournal.com";
                Config.UseLogin = true;

                if (Util.False && Config.DownloadRoot.endsWith(".dreamwidth-org"))
                    Config.DownloadRoot = Util.stripTail(Config.DownloadRoot, ".dreamwidth-org");

                if (Util.False && Config.DownloadRoot.endsWith(".lj-rossia-org"))
                    Config.DownloadRoot = Util.stripTail(Config.DownloadRoot, ".lj-rossia-org");
            }
        }
    }

    /* development aids */
    public static boolean True = true;
    public static boolean False = false;
}
