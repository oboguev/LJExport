package my.LJExport;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;

import org.apache.http.HttpStatus;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.jsoup.nodes.*;

public class PageReaderSelenium extends PageParser implements PageReader
{
    private static Object Lock = new String("PageReaderSelenium.Lock");
    private static int next_session_number = 1;
    static final int UndelayedLoginSessions = 4;
    private static final String PROCEED = "<PROCEED>";
    private WebElement massaction_checkbox;
    private WebElement massaction_expand;

    public static void reinit() throws Exception
    {
        next_session_number = 1;
    }

    private RemoteWebDriver driver;
    private String fileDir;
    private String linksDir;

    final private boolean saveAsSinglePage = true;

    public static boolean manualOverride = false;

    static class Context
    {
        public RemoteWebDriver driver;
        public boolean logged_in = false;

        public Context(RemoteWebDriver driver, boolean logged_in)
        {
            this.driver = driver;
            this.logged_in = logged_in;
        }

        public void close()
        {
            if (driver != null)
            {
                if (logged_in)
                    PageReaderSelenium.try_logout(driver);
                driver.quit();
                driver = null;
            }
        }
    }

    public static Context makeContext() throws Exception
    {
        RemoteWebDriver driver = null;

        try
        {
            String proxy_url = "localhost:" + Main.proxyServer.getPort();

            org.openqa.selenium.Proxy proxy = new org.openqa.selenium.Proxy()
                    .setHttpProxy(proxy_url)
                    .setFtpProxy(proxy_url)
                    .setSslProxy(proxy_url);
            DesiredCapabilities caps = new DesiredCapabilities();
            caps.setCapability(CapabilityType.PROXY, proxy);

            // create profile
            FirefoxProfile profile = new FirefoxProfile();
            profile.setPreference("browser.startup.page", 0);
            profile.setPreference("browser.startup.homepage", "about:blank");
            profile.setPreference("startup.homepage_override_url", "about:blank");
            profile.setPreference("startup.homepage_welcome_url", "about:blank");
            profile.setPreference("startup.homepage_welcome_url.additional", "about:blank");

            // create driver
            driver = new FirefoxDriver(new FirefoxBinary(), profile, caps);
            driver.manage().timeouts().pageLoadTimeout(Config.Timeout, TimeUnit.SECONDS);
            driver.manage().timeouts().setScriptTimeout(Config.Timeout, TimeUnit.SECONDS);
            driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);

            // position window
            if (Config.NWorkThreads != 1)
            {
                driver.manage().window().setSize(new org.openqa.selenium.Dimension(150, 100));
                driver.manage().window().setPosition(new org.openqa.selenium.Point(30, 30));
            }

            PageReaderSelenium reader = new PageReaderSelenium("http://" + Config.Site + "/login.bml", null, null,
                                                               new Context(driver, false));
            reader.driver = driver;

            // perform login
            delayLogin();
            if (!reader.login())
            {
                driver.quit();
                return null;
            }

            return new Context(driver, true);
        }
        catch (Exception ex)
        {
            if (driver != null)
                driver.quit();
            throw ex;
        }
    }

    private boolean login() throws Exception
    {
        long initialLoginTime = System.currentTimeMillis();

        for (;;)
        {
            driver.get(this.rurl);

            // Check that login form is present:
            // .div class=s-body
            // ...(intermediate nested levels)
            // ...input id=user name=user class=b-input type=text
            // ...input id=lj_loginwidget_password name=password class=b-input
            // ...type=password button name="action:login" type=submit
            // These elements may be multiple on the page (multiple copies of login form)
            // with only one of them visible, so find which one it is
            // By by_user = By.xpath(xpathTagClassContainsName("input", "b-input", "user"));
            // By by_password = By.xpath(xpathTagClassContainsName("input", "b-input", "password"));
            // By by_login = By.xpath(xpathTagName("button", "action:login"));

            By by_user = By.xpath(xpathTagId("input", "user"));
            By by_password = By.xpath(xpathTagId("input", "lj_loginwidget_password"));
            By by_login = By.xpath(xpathTagName("button", "action:login"));

            WebElement el_user = getDisplayedOne(by_user);
            WebElement el_password = getDisplayedOne(by_password);
            WebElement el_login = getDisplayed(driver.findElements(by_login));

            el_user.sendKeys(Config.LoginUser);
            el_password.sendKeys(Config.LoginPassword);
            long currentLoginTime = System.currentTimeMillis();
            el_login.click();

            switch (waitLoginCompletion(driver, initialLoginTime, currentLoginTime))
            {
            case LOGGED_IN:
                return true;

            case RETRY_LOGIN:
                continue;

            case FAILED_LOGIN:
                return false;
            }
        }
    }

    enum LoginStatus
    {
        LOGGED_IN, RETRY_LOGIN, FAILED_LOGIN
    }

    private static LoginStatus waitLoginCompletion(RemoteWebDriver driver, long initialLoginTime, long currentLoginTime)
            throws Exception
    {
        long dt;

        for (;;)
        {
            String url = driver.getCurrentUrl();

            if (Util.isValidPostLoginUrl(url))
                return LoginStatus.LOGGED_IN;

            String pageHtml = driver.getPageSource();
            if (pageHtml.contains("You're logged in as"))
                return LoginStatus.LOGGED_IN;

            if (!Util.isLoginPageURL(url))
            {
                dt = System.currentTimeMillis() - currentLoginTime;
                Main.err("Login failure (URL: " + url + "), after " + dt + " ms (current attempt)");
                return LoginStatus.FAILED_LOGIN;
            }

            dt = System.currentTimeMillis() - initialLoginTime;
            if (dt >= Config.Timeout * 1000)
            {
                // Main.err("Login failure (URL=login), after " + dt + " ms (since initial attempt)");
                return LoginStatus.FAILED_LOGIN;
            }

            if (isLoginLimitExceeded(driver.getPageSource()))
            {
                Thread.sleep(Util.random(5 * 1000, 10 * 1000));
                return LoginStatus.RETRY_LOGIN;
            }

            Thread.sleep(1 * 1000);
        }
    }

    public PageReaderSelenium(String rurl, String fileDir, String linksDir, Context context)
    {
        // rurl = "2106296.html"; // test: tor85 (no comments)  
        // rurl = "175603.html";  // test: a_bugaev (comments disabled)
        // rurl = "2532366.html"; // test: colonelcassad
        // rurl = "5182367.html"; // test: oboguev (private, no comments)
        // rurl = "2504913.html"; // test: krylov (unexpandable link)
        this.rurl = rurl;
        this.rid = rurl.substring(0, rurl.indexOf('.'));
        this.fileDir = fileDir + File.separator;
        this.linksDir = linksDir;
        this.driver = context.driver;
    }

    public void readPage() throws Exception
    {
        if (saveAsSinglePage)
        {
            // process page 1 completely
            pageSource = loadPage(1);
            if (pageSource == null)
            {
                Main.markFailedPage(rurl);
                return;
            }

            if (pageRoot == null)
                parseHtml();

            removeJunk(COUNT_PAGES | REMOVE_SCRIPTS | CHECK_COMMENTS_MERGEABLE);
            Node firstPageRoot = pageRoot;

            // load extra pages of comments
            for (int npage = 2; npage <= npages; npage++)
            {
                pageSource = loadPage(npage);
                if (pageSource == null)
                {
                    Main.markFailedPage(rurl);
                    return;
                }

                if (pageRoot == null)
                    parseHtml();

                removeJunk(REMOVE_MAIN_TEXT | REMOVE_SCRIPTS);
                mergeComments(firstPageRoot);
            }

            downloadExternalLinks(firstPageRoot, linksDir);
            pageSource = JSOUP.emitHtml(firstPageRoot);
            Util.writeToFileSafe(fileDir + rid + ".html", pageSource);
        }
        else
        {
            // save page 1 completely, except for junk sections
            pageSource = loadPage(1);
            if (pageSource == null)
            {
                Main.markFailedPage(rurl);
                return;
            }

            String rbody_1 = removeJunkAndEmitHtml(COUNT_PAGES);

            // load extra pages of comments
            for (int npage = 2; npage <= npages; npage++)
            {
                pageSource = loadPage(npage);
                if (pageSource == null)
                {
                    Main.markFailedPage(rurl);
                    return;
                }

                pageSource = removeJunkAndEmitHtml(REMOVE_MAIN_TEXT);
                Util.writeToFileSafe(fileDir + rid + "_page_" + npage + ".html", pageSource);
            }

            Util.writeToFileSafe(fileDir + rid + ".html", rbody_1);
        }

        // out(">>> done " + rurl);
    }

    private String removeJunkAndEmitHtml(int flags) throws Exception
    {
        if (pageRoot == null)
            parseHtml();
        removeJunk(flags);
        downloadExternalLinks(pageRoot, linksDir);
        return JSOUP.emitHtml(pageRoot);
    }

    private String loadPage(int npage) throws Exception
    {
        return loadPage(npage, 1);
    }

    private String loadPage(int npage, int attempt) throws Exception
    {
        long t0 = System.currentTimeMillis();

        pageSource = null;
        pageRoot = null;
        massaction_checkbox = null;
        massaction_expand = null;

        /*
         * Try to load from manual-save override location.
         * Some pages cannot have their comments expanded, even manually, due to LJ bug,
         * see e.g. http://oboguev.livejournal.com/701758.html
         * and need to be loaded from the manual-override area.
         */
        pageSource = Main.manualPageLoad(rurl, npage);
        if (pageSource != null)
            return pageSource;

        /*
         * Perform initial page load
         */
        String res = loadPage_initial(npage, attempt, t0);
        if (res != PROCEED)
            return res;

        /*
         * See if we need to expand comments 
         */
        res = loadPage_checkNeedExpandAllComments(npage, attempt, t0);
        if (res != PROCEED)
            return res;

        /*
         * Expand comments 
         */
        pageSource = loadPage_doExpandAllComments(npage, attempt);
        if (pageSource == null)
            return null;

        /*
         * Second round of comment expansion.
         * 
         * On some pages, "Expand All" expands some (most) of the comments,
         * but not all of the comments, leaving elements <span class="b-leaf-seemore-expand">
         * with text like "...and 27 more comments..." next to them.
         * We should locate and trigger an <a> element under these span elements
         * until these span elements are gone.
         */
        return loadPage_doExpandMoreComments(npage, attempt);
    }

    private String loadPage_initial(int npage, int attempt, long t0) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append("http://" + Config.MangledUser + "." + Config.Site + "/" + rurl + "?format=light");
        if (npage != 1)
            sb.append("&page=" + npage);

        boolean retry = true;
        int retries = 0;

        for (int pass = 0;; pass++)
        {
            pageRoot = null;
            Main.checkAborting();

            if (retry)
            {
                driver.get(sb.toString());
                retry = false;
                retries++;
                pass = 0;
            }

            pageSource = driver.getPageSource();

            if (isLoadingPage(pageSource))
            {
                if (System.currentTimeMillis() - t0 > Config.Timeout * 1000)
                {
                    // if page is stuck, retry 2 times from the very start before giving up
                    if (attempt < 3)
                    {
                        return loadPage(npage, attempt + 1);
                    }
                    else
                    {
                        if (Main.unloadablePages.incrementAndGet() < Config.UnloadablePagesAllowed)
                            return null;
                        throw new Exception("Page fails to complete the inital load");
                    }
                }
                else
                {
                    Thread.sleep(1000 * (1 + pass));
                }
            }
            else if (isBadGatewayPage(pageSource))
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else
            {
                break;
            }
        }

        return PROCEED;
    }

    private String loadPage_checkNeedExpandAllComments(int npage, int attempt, long t0) throws Exception
    {
        try
        {
            if (pageHasNoComments(pageSource))
                return pageSource;
        }
        catch (MissingCommentsTreeRootException ex)
        {
            if (Main.unloadablePages.incrementAndGet() < Config.UnloadablePagesAllowed)
                return null;
            else
                throw ex;
        }

        if (likelyBlockedPage(pageSource) && isBlockedPage(pageSource))
            return null;

        WebElement b_massaction = getOneOptional(driver.findElementsByXPath("//div[@class=\"b-massaction\"]"));

        if (b_massaction == null)
        {
            if (isBlockedPage())
            {
                return null;
            }
            else
            {
                throw new Exception("Unable to find required element in the page");
            }
        }

        massaction_checkbox = getOne(b_massaction.findElements(By
                .xpath(".//input[@id='checkall'][@type='checkbox']")));
        massaction_expand = getOne(b_massaction.findElements(By.xpath(".//button[@value='expand'][@type='submit']")));

        if (!b_massaction.isDisplayed() ||
            !massaction_checkbox.isDisplayed() ||
            !massaction_expand.isDisplayed())
        {
            pageRoot = null;
            return driver.getPageSource();
        }

        return PROCEED;
    }

    private String loadPage_doExpandAllComments(int npage, int attempt) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb))
        {
            formatter.format("//li[%1$s and (%2$s or %3$s)]",
                             elementClassContains("b-leaf-actions-item"),
                             elementClassContains("b-leaf-actions-expand"),
                             elementClassContains("b-leaf-actions-expandchilds"));
        }

        massaction_checkbox.click();
        massaction_expand.click();

        /*
         * Wait for comment expansion to complete 
         */
        long t0 = System.currentTimeMillis();
        long tBlockedCheckInterval = 20 * 1000;
        long tBlockedCheck = t0 - tBlockedCheckInterval / 2;

        for (int pass = 0;; pass++)
        {
            Main.checkAborting();
            boolean page_idle = false;

            pageSource = null;
            pageRoot = null;

            if (manualOverride)
            {
                /*
                 * Set under the debugger for extremely poorly-behaved pages
                 * where comment expansion has to be performed (to the extent it 
                 * can be performed at all) and controlled manually. 
                 */
                pageSource = driver.getPageSource();
                return pageSource;
            }
            else if (anyDisplayed(By.xpath(sb.toString())))
            {
                if (System.currentTimeMillis() - tBlockedCheck >= tBlockedCheckInterval)
                {
                    if (isBlockedPage())
                        return null;
                    tBlockedCheck = System.currentTimeMillis();
                }

                List<WebElement> els = findElementsNow(By.xpath(sb.toString() + "/a"));
                int clicked = 0;

                driver.manage().timeouts().implicitlyWait(1, TimeUnit.MILLISECONDS);
                for (WebElement el : els)
                {
                    try
                    {
                        if (el.isDisplayed() && !isDeadLink(el))
                        {
                            el.click();
                            clicked++;
                        }
                    }
                    catch (org.openqa.selenium.StaleElementReferenceException ex)
                    {
                        clicked++;
                    }
                    catch (Throwable tex)
                    {
                        driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);
                        throw tex;
                    }
                }
                driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);

                if (clicked == 0)
                    page_idle = true;
            }
            else
            {
                page_idle = true;
            }

            if (page_idle)
            {
                pageSource = driver.getPageSource();
                if (isBlockedPage(pageSource))
                    return null;
                if (!isLoadingPage(pageSource) && isStable(pageSource))
                    return pageSource;
            }

            if (System.currentTimeMillis() - t0 > Config.Timeout * 1000)
            {
                // if the page is stuck, retry 2 times from the very start before giving up
                if (attempt < 3)
                {
                    return loadPage(npage, attempt + 1);
                }
                else
                {
                    Main.saveDebugPage("badpage-unable-expand-comments.html", driver.getPageSource());
                    if (Main.unloadablePages.incrementAndGet() < Config.UnloadablePagesAllowed)
                        return null;
                    throw new Exception("Unable to expand comments");
                }
            }

            Thread.sleep(sleepTime(500, 3000, pass));
        }
    }

    private String loadPage_doExpandMoreComments(int npage, int attempt) throws Exception
    {
        // Repeatedly locate all <span class="b-leaf-seemore-expand"> and trigger an <a> element
        // under them until all such span elements are gone.

        // span class="b-leaf-seemore-expand"
        //         a=class="b-pseudo" target="_self" href="..." rel="nofollow"
        //             text=Expand

        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb))
        {
            formatter.format("//span[%1$s]/a", elementClassContains("b-leaf-seemore-expand"));
        }

        long t0 = System.currentTimeMillis();

        for (int pass = 0;; pass++)
        {
            if (!pageSource.contains("b-leaf-seemore-expand") && isStable(pageSource))
                return pageSource;

            Main.checkAborting();

            List<WebElement> els = findElementsNow(By.xpath(sb.toString()));
            if (els.size() == 0)
            {
                pageSource = driver.getPageSource();

                if (isBlockedPage(pageSource))
                    return null;
                else if (isStable(pageSource))
                    return pageSource;
            }

            if (System.currentTimeMillis() - t0 > Config.Timeout * 1000)
            {
                // if page is stuck, retry 2 times from the very start before giving up
                if (attempt < 3)
                {
                    return loadPage(npage, attempt + 1);
                }
                else
                {
                    Main.saveDebugPage("badpage-unable-expand-remaining-comments.html", driver.getPageSource());
                    if (Main.unloadablePages.incrementAndGet() < Config.UnloadablePagesAllowed)
                        return null;
                    throw new Exception("Unable to expand remaining comments");
                }
            }

            driver.manage().timeouts().implicitlyWait(1, TimeUnit.MILLISECONDS);
            for (WebElement el : els)
            {
                try
                {
                    el.click();
                }
                catch (org.openqa.selenium.StaleElementReferenceException ex)
                {
                }
                catch (Throwable tex)
                {
                    driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);
                    throw tex;
                }
            }
            driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);

            Thread.sleep(sleepTime(500, 3000, pass));

            Main.checkAborting();

            pageSource = driver.getPageSource();
            if (isBlockedPage(pageSource))
                return null;
        }
    }

    private int sleepTime(int t1, int t2, int pass)
    {
        int t = t1;

        for (int k = 0; k < pass; k++)
        {
            t += t / 2;
            if (t >= t2)
                return t2;
        }

        return t;
    }

    private static WebElement getOneOptional(List<WebElement> els) throws Exception
    {
        if (els.size() == 0)
            return null;
        if (els.size() != 1)
            throw new Exception("Unable to find required element in the page (multiple matching alements are present)");
        return els.get(0);
    }

    private static WebElement getOne(List<WebElement> els) throws Exception
    {
        if (els.size() == 0)
            throw new Exception("Unable to find required element in the page");
        if (els.size() != 1)
            throw new Exception("Unable to find required element in the page (multiple matching alements are present)");
        return els.get(0);
    }

    private boolean anyDisplayed(By by) throws Exception
    {
        long t0 = System.currentTimeMillis();

        for (;;)
        {
            List<WebElement> els = findElementsNow(by);

            try
            {
                driver.manage().timeouts().implicitlyWait(1, TimeUnit.MILLISECONDS);
                return anyDisplayed(els);
            }
            catch (org.openqa.selenium.StaleElementReferenceException ex)
            {
            }
            finally
            {
                driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);
            }

            Main.checkAborting();

            if (System.currentTimeMillis() - t0 > Config.Timeout * 1000)
                throw new Exception("Unable to check whether comments have been expanded");
        }

    }

    private static boolean anyDisplayed(List<WebElement> els) throws Exception
    {
        for (WebElement el : els)
        {
            if (el.isDisplayed())
                return true;
        }
        return false;
    }

    private static WebElement getDisplayed(List<WebElement> els) throws Exception
    {
        for (WebElement el : els)
        {
            if (el.isDisplayed())
                return el;
        }
        throw new Exception("Unable to find required visible element in the page");
    }

    private WebElement getDisplayedOne(By by) throws Exception
    {
        long t0 = System.currentTimeMillis();

        try
        {
            driver.manage().timeouts().implicitlyWait(1, TimeUnit.MILLISECONDS);

            for (;;)
            {
                List<WebElement> els = driver.findElements(by);
                els = selectDisplayed(els);

                if (els.size() == 1)
                    return els.get(0);
                else if (els.size() != 0)
                    throw new Exception("Multiple elements are visible");

                if (System.currentTimeMillis() - t0 > Config.Timeout)
                    throw new Exception("Unable to find required visible element");

                Thread.sleep(100);
            }
        }
        finally
        {
            driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);
        }
    }

    private static List<WebElement> selectDisplayed(List<WebElement> els) throws Exception
    {
        List<WebElement> vels = new ArrayList<WebElement>();
        for (WebElement el : els)
        {
            if (el.isDisplayed())
                vels.add(el);
        }
        return vels;
    }

    private List<WebElement> findElementsNow(By by) throws Exception
    {
        try
        {
            driver.manage().timeouts().implicitlyWait(1, TimeUnit.MILLISECONDS);
            return driver.findElements(by);
        }
        finally
        {
            driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);
        }
    }

    private static void delayLogin() throws Exception
    {
        int sn;

        synchronized (Lock)
        {
            sn = next_session_number++;
        }

        if (sn <= UndelayedLoginSessions)
            return;

        Thread.sleep(5 * (sn - UndelayedLoginSessions));
    }

    public static void try_logout(RemoteWebDriver driver)
    {
        try
        {
            logout(driver);
        }
        catch (Exception ex)
        {
            Main.setLogoutFailed();
        }
    }

    public static void logout(RemoteWebDriver driver) throws Exception
    {
        int timeout = 30;
        driver.manage().timeouts().pageLoadTimeout(timeout, TimeUnit.SECONDS);
        driver.manage().timeouts().setScriptTimeout(timeout, TimeUnit.SECONDS);
        driver.manage().timeouts().implicitlyWait(timeout, TimeUnit.SECONDS);

        driver.get(Config.Site);
        Node root = JSOUP.parseHtml(driver.getPageSource());

        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (String url : JSOUP.extractHrefs(root))
        {
            if (Util.isLogoutURL(url, sb))
            {
                found = true;
                break;
            }
        }

        if (!found)
            throw new Exception("Unable to find logout URL");

        driver.get(sb.toString());
    }

    private boolean isStable(String src) throws Exception
    {
        Thread.sleep(Config.StableIntevral);
        return getPageSource().equals(src);
    }

    private boolean isDeadLink(WebElement el) throws Exception
    {
        try
        {
            String tag = el.getTagName();
            if (tag == null || !tag.equalsIgnoreCase("a"))
                return false;

            String href = el.getAttribute("href");
            if (href == null || href.length() == 0)
                return false;

            return Main.isDeadLink(href);
        }
        catch (org.openqa.selenium.StaleElementReferenceException ex)
        {
        }

        return false;
    }

    @Override
    protected String getPageSource() throws Exception
    {
        return driver.getPageSource();
    }
}
