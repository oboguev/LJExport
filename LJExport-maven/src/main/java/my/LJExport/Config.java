package my.LJExport;

// import java.io.Console;
import java.io.File;

// import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
// import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

import com.gargoylesoftware.htmlunit.BrowserVersion;

import my.LJExport.calendar.YYYY_MM;
import my.LJExport.runtime.ConfigUI;
import my.LJExport.runtime.Util;

// import javax.swing.*;

public class Config
{
    /******************************/
    /** USER-SETTABLE PARAMETERS **/
    /******************************/

    /* Login username */
    public static String LoginUser = "oboguev";

    /* List of journals to download (comma or space-separated) */
    public static final String Users = "andrewmed";
    // public static final String Users = "nikital2014,bash_m_ak,genby,olegnemen,eremei,afanarizm,jlm_taurus,corporatelie,wyradhe,nilsky_nikolay,von_hoffmann,a_samovarov,bantaputu,a_kaminsky,d_olshansky,rn_manifesto,ru_bezch,nep2,ego,hokma,laert,haritonov,1981dn,1981dn_dn,bantaputu,polit_ec,zhenziyou,a_bugaev,tor85,oboguev,morky,krylov,rms1,pioneer_lj,holmogor,miguel_kud,colonelcassad,galkovsky,_devol_";
    // public static final String Users = "alex_vergin,sergeytsvetkov,blog_10101"; // new-style journals 

    /* Directory path to store downloaded files */
    // public static final String DownloadRoot = "R:";
    // public static final String DownloadRoot = "F:\\@";
    // public static final String DownloadRoot = "C:\\WINAPPS\\LJExport\\journals";
    // public static final String DownloadRoot = "/home/sergey/LJExport/journals";
    // public static final String DownloadRoot = "C:\\LJExport-journals";
    public static final String DownloadRoot = "F:\\WINAPPS\\LJExport\\journals";

    /* Proxy server */
    public static String Proxy = null;
    // public static String Proxy = "http://proxy.xxx.com:8080";

    /* Range of dates to download (inclusive) */
    public static final YYYY_MM LoadSince = null;
    public static final YYYY_MM LoadTo = null;
    // public static final YYYY_MM LoadSince = new YYYY_MM(2009, 4);
    // public static final YYYY_MM LoadTo = new YYYY_MM(2009, 4);

    /* Whether to reload files already existing at DownloadRoot */
    public static final boolean ReloadExistingFiles = false;

    /* Minimum and maximum number of unloadable pages allowed before the download aborts */
    public static final int MinUnloadablePagesAllowed = 20;
    public static final int MaxUnloadablePagesAllowed = 50;

    /**************************/
    /** TECHNICAL PARAMETERS **/
    /**************************/

    public enum WebMethod
    {
        BASIC, SELENIUM, HTML_UNIT, DIRECT
    };

    public static final WebMethod Method = Config.WebMethod.DIRECT;
    public static final String Site = "livejournal.com";
    public static final String AllowedUrlSites[] = { "livejournal.com", "livejournal.net" };
    public static String LoginPassword = null;
    public static String User = null;
    public static String MangledUser = null;
    public static String UserAgent = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1";
    public static String UserAgentAccept = "text/html, application/xhtml+xml, */*";
    public static final String UserAgentAcceptEncoding = "gzip, deflate";
    public static final BrowserVersion HtmlUnitBrowserVersion = BrowserVersion.FIREFOX_38;
    public static final int Timeout = 150;
    public static int NWorkThreads = 70;
    public static final int ThreadsPerCPU = 2;
    public static final int MaxThreads = 70;
    public static int MaxConnectionsPerRoute = 6; // max concurrent connections per route (i.e. per host)
    public static String TrustStore = null;
    public static String TrustStorePassword = null;
    public static final int ProxyThreadsPerThread = 10;
    public static final int MaxProxyThreads = 200;
    public static String ProxyBlockingMessage = "The requested website is blocked";
    public static int UnloadablePagesAllowed;
    public static boolean RandomizeLoadOrder = true;
    public static final int StableIntevral = 3000;
    public static final int RateLimitCalendar = 500;
    public static final int RateLimitPageLoad = 1200;
    public static final int RateLimitImages = 200;

    public static boolean UseFiddler = false;
    public static final String FiddlerTrustStore = null;
    public static String FidlerTrustStorePassword = null;

    public static final boolean TrustAnySSL = true;

    public static String NullFile = null;

    /* Download linked files of the listed types locally, so they can be accessed offline */
    // public static List<String> DownloadFileTypes = null;
    public static final List<String> DownloadFileTypes = Util
            .asList("jpg,jpeg,gif,png,webp,pdf,djvu,tif,tiff,doc,docx,rtf,zip,rar");

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

        if (TrustAnySSL)
        {
            SSLContext sslContext = SSLContext.getInstance("SSL");

            // set up a TrustManager that trusts everything
            TrustManager[] trustManagers = new TrustManager[1];
            trustManagers[0] = new LooseTrustManager();
            sslContext.init(null, trustManagers, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
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

    /* development aids */
    public static boolean True = true;
    public static boolean False = false;

    public static class LooseTrustManager implements X509TrustManager
    {
        public X509Certificate[] getAcceptedIssuers()
        {
            // System.err.println("getAcceptedIssuers =============");
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType)
        {
            // System.err.println("checkClientTrusted =============");
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType)
        {
            // System.err.println("checkServerTrusted =============");
        }
    }
}
