package my.LJExport;

import java.io.Console;

import com.gargoylesoftware.htmlunit.BrowserVersion;

import javax.swing.*;

public class Config
{
    /******************************/
    /** USER-SETTABLE PARAMETERS **/
    /******************************/

    /* Login username */
    static public String LoginUser = "oboguev";

    /* List of journals to download (comma or space-separated) */
    static public String Users = "krylov";
    // static public String Users = "a_bugaev,tor85,oboguev,morky,krylov,rms1,holmogor,miguel_kud,colonelcassad";

    /* Directory path to store downloaded files */
    static public String DownloadRoot = "F:\\WINAPPS\\LJExport\\journals";
    // static public String DownloadRoot = "/home/sergey/LJExport/journals";

    /* Proxy server */
    static public String Proxy = null;
    // static public String Proxy = "http://proxy.xxx.com:8080";

    /* Range of dates to download (inclusive) */
    static public YYYY_MM LoadSince = null;
    static public YYYY_MM LoadTo = null;
    // static public YYYY_MM LoadSince = new YYYY_MM(2004, 10);
    // static public YYYY_MM LoadTo = new YYYY_MM(2006, 3);

    /* Whether to reload files already existing at DownloadRoot */
    static public boolean ReloadExistingFiles = false;

    /* Minimum and maximum number of unloadable pages allowed before the download aborts */
    static public int MinUnloadablePagesAllowed = 20;
    static public int MaxUnloadablePagesAllowed = 50;

    // static public int MinUnloadablePagesAllowed = 100;
    // static public int MaxUnloadablePagesAllowed = 100;

    /**************************/
    /** TECHNICAL PARAMETERS **/
    /**************************/

    public enum WebMethod
    {
        BASIC,
        SELENIUM,
        HTML_UNIT
    };

    static public WebMethod Method = Config.WebMethod.SELENIUM;
    static public String Site = "livejournal.com";
    static public String AllowedUrlSites[] = { "livejournal.com", "livejournal.net" };
    static public String LoginPassword = null;
    static public String User = null;
    static public String MangledUser = null;
    static public String UserAgent = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1";
    static public String UserAgentAccept = "text/html, application/xhtml+xml, */*";
    static public String UserAgentAcceptEncoding = "gzip, deflate";
    static public int Timeout = 150;
    static public int NWorkThreads = 0;
    static public int ThreadsPerCPU = 2;
    static public int MaxThreads = 6;
    static public String TrustStore = null;
    static public String TrustStorePassword = null;
    static public int ProxyThreadsPerThread = 10;
    static public int MaxProxyThreads = 200;
    static public String ProxyBlockingMessage = "The requested website is blocked";
    static public int UnloadablePagesAllowed;
    static public boolean RandomizeLoadOrder = true;

    static public boolean UseFiddler = false;
    static public String FiddlerTrustStore = "F:\\WINAPPS\\LJExport\\fiddler\\FiddlerKeystore";
    static public String FidlerTrustStorePassword = null;

    static public BrowserVersion HtmlUnitBrowserVersion = BrowserVersion.FIREFOX_38;

    public static void init(String user) throws Exception
    {
        User = user;
        mangleUser();

        if (UseFiddler)
        {
            Proxy = "localhost:8888";

            if (TrustStore == null && FiddlerTrustStore != null)
            {
                TrustStore = FiddlerTrustStore;
                TrustStorePassword = FidlerTrustStorePassword;
            }
        }

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

        if (LoginPassword == null)
        {
            LoginPassword = ConfigUI.promptPassword("Enter password for [" + LoginUser + "] at " + Site);
            if (LoginPassword == null)
                throw new Exception("Unable to get password");
        }

        if (UseFiddler && FiddlerTrustStore != null && FidlerTrustStorePassword == null)
        {
            FidlerTrustStorePassword = ConfigUI.promptPassword("Enter password for Fiddler trust store");
            if (FidlerTrustStorePassword == null)
                throw new Exception("Unable to get password");

        }

        if (Proxy == null)
            ProxyBlockingMessage = null;
    }

    private static void mangleUser() throws Exception
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
}
