package my.LJExport.runtime.http.browserproxy;

public class ChromeOptions
{
    public boolean noFirstRun = true;
    public boolean disablePopupBlocking = false;
    public boolean disableWebSecurity = false;
    public boolean disableExtensions = true;
    public boolean headless = true;
    public boolean enableJavaScript = false;
    public int maxRedirects = 20;
    public int pageNavigationTimeoutMillis = 30000;  // used by CDP navigate
    public int javaRequestTimeoutMillis = 35000;     // enforced on Java side (optional)
}
