package my.KrylovCC;

import org.apache.http.client.CookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.jsoup.nodes.Node;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.RateLimiter;
import my.LJExport.runtime.http.Web;

public class MainDownloadPages
{
    public static void main(String[] args)
    {
        new MainDownloadPages().do_main();
    }

    private static final int LoadTimeInterval = 3000;

    private void do_main()
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
            Util.out(">>> Time: " + Util.timeNow());

            Config.init("");
            Config.User = "krylov.cc";
            Config.mangleUser();

            Config.MaxConnectionsPerRoute = 1;
            RateLimiter.LJ_PAGES.setRateLimit(LoadTimeInterval);
            Web.init();
            addControlCookie();

            download_fansw();
            download_fb();
            preprocess("fansw", 0, 200, 18400);
            preprocess("fb", 0, 100, 7200);

            Util.out("");
            Util.out("Completed.");
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        Util.out(">>> Time: " + Util.timeNow());

        Main.playCompletionSound();
    }

    // https://krylov.cc/fansw.php?start=0&sort_by_rate=0
    // https://krylov.cc/fansw.php?start=200&sort_by_rate=0
    // ...
    // https://krylov.cc/fansw.php?start=800&sort_by_rate=0
    // ...
    // https://krylov.cc/fansw.php?start=18400&sort_by_rate=0
    private void download_fansw() throws Exception
    {
        for (int start = 0; start <= 18400; start += 200)
        {
            downloadPage("fansw", start, String.format("https://krylov.cc/fansw.php?start=%d&sort_by_rate=0", start));
        }
    }

    // https://krylov.cc/fb.php
    // https://krylov.cc/fb.php?start=100
    // https://krylov.cc/fb.php?start=200
    // ...
    // https://krylov.cc/fb.php?start=7200
    private void download_fb() throws Exception
    {
        for (int start = 0; start <= 7200; start += 100)
        {
            if (start == 0)
                downloadPage("fb", start, String.format("https://krylov.cc/fb.php", start));
            else
                downloadPage("fb", start, String.format("https://krylov.cc/fb.php?start=%d", start));
        }
    }

    private void downloadPage(String which, int start, String url) throws Exception
    {
        Path xpath = rawPagePath(which, start);
        String path = xpath.toAbsolutePath().toString();

        File fp = new File(path).getCanonicalFile();
        if (fp.exists() && fp.length() > 10)
            return;

        if (!fp.getParentFile().exists())
            fp.getParentFile().mkdirs();

        if (start != 0)
            Util.sleep(LoadTimeInterval);

        Util.out(String.format("Loading %s %d ...", which, start));
        Web.Response r = Web.get(url);

        if (r.code != 200)
            throw new Exception("Failed to load page, http code = " + r.code);

        Util.writeToFileSafe(fp.getCanonicalPath(), r.textBody());
    }

    public static Path rawPagePath(String which, int start)
    {
        return Paths.get(Config.DownloadRoot, Config.User, "raw-cc-pages", which, String.format("%05d.html", start));
    }

    /* ================================================================================================= */

    /*
     * cookie name: dispnmb
     * Value:"100"
     * Created:"Fri, 04 Jul 2025 06:36:55 GMT"
     * Domain:".krylov.cc"
     * Expires / Max-Age:"Sat, 04 Jul 2026 06:36:55 GMT"
     * HostOnly:false
     * HttpOnly:true
     * Last Accessed:"Fri, 04 Jul 2025 06:37:55 GMT"
     * Path:"/"
     * SameSite:"None"
     * Secure:false
     * Size:10
     */
    private void addControlCookie() throws Exception
    {
        CookieStore cookieStore = Web.getCookieStore();

        // Create the cookie (dispnmb)
        BasicClientCookie cookie = new BasicClientCookie("dispnmb", "100");

        // Set cookie attributes
        cookie.setDomain(".krylov.cc"); // Domain (with subdomains)
        cookie.setPath("/"); // Root path
        cookie.setSecure(false); // Secure flag (should be true in production!)

        // Workaround for HttpOnly/SameSite in HttpClient 4.x
        cookie.setAttribute("httponly", "true"); // HttpOnly
        cookie.setAttribute("samesite", "None"); // SameSite

        // Parse expiry date from string "04 Jul 2026 06:36:55 GMT"
        String expiryDateString = "04 Jul 2026 06:36:55 GMT";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        try
        {
            Date expiryDate = sdf.parse(expiryDateString);
            cookie.setExpiryDate(expiryDate);
        }
        catch (ParseException e)
        {
            System.err.println("Failed to parse expiry date: " + e.getMessage());
            // Fallback: Set expiry to 1 day from now if parsing fails
            cookie.setExpiryDate(new Date(System.currentTimeMillis() + 86400000));
        }

        // Add the cookie to the store
        cookieStore.addCookie(cookie);

        // (Optional) Verify the cookie was added
        // System.out.println("Cookies in store: " + cookieStore.getCookies());
    }
    
    /* ================================================================================================= */
    
    private void preprocess(String which, int start, int step, int last) throws Exception
    {
        for (int id = start; id <= last; id += step)
        {
            Path xpath = rawPagePath(which, id);
            String path = xpath.toAbsolutePath().toString();
            File fp = new File(path).getCanonicalFile();
            if (!fp.exists())
                throw new Exception("Missing file " + fp.getCanonicalPath());
            preprocess(fp.getCanonicalPath());
        }
    }

    private void preprocess(String filepath) throws Exception
    {
        PageParserDirectBase parser = new PageParserDirectBasePassive();
        parser.pageSource = Util.readFileAsString(filepath);
        parser.parseHtml(parser.pageSource);
        
        boolean updated = false;
        
        updated |= makeabs(parser.pageRoot, "link", "href");
        updated |= makeabs(parser.pageRoot, "script", "src");
        updated |= makeabs(parser.pageRoot, "a", "href");
        updated |= makeabs(parser.pageRoot, "img", "src");
        
        if (updated)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(filepath, html);
        }
    }
    
    private boolean makeabs(Node pageRoot, String tag, String attr) throws Exception
    {
        boolean updated = false;

        for (Node n : JSOUP.findElements(pageRoot, tag))
        {
            String v = JSOUP.getAttribute(n, attr);
            
            if (v != null)
            {
                String v0 = v;
                if (v.startsWith("//facebook.com/"))
                {
                    v = "http:" + v;
                }
                else if (v.startsWith("//krylov.cc/"))
                {
                    v = "https:" + v;
                }
                else if (v.startsWith("/") && !v.startsWith("//"))
                {
                    v = "https://krylov.cc" + v;
                }
                
                if (!v.equals(v0))
                {
                    JSOUP.updateAttribute(n, attr, v);
                    updated = true;
                }
            }
        }
        
        return updated;
    }
}
