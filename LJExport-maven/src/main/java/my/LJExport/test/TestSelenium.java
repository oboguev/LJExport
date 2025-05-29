package my.LJExport.test;

import java.util.*;
import java.util.concurrent.TimeUnit;

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

import my.LJExport.*;
import my.LJExport.runtime.ProxyServer;
import my.LJExport.runtime.Util;
import my.LJExport.xml.JSOUP;

// http://seleniumhq.github.io/selenium/docs/api/java/index.html
// http://docs.seleniumhq.org/docs
// http://docs.seleniumhq.org/docs/03_webdriver.jsp

// ProxyLight: set proxy and prune all non-LJ requests 
// http://www.supermind.org/blog/968/howto-collect-webdriver-http-request-and-response-headers

public class TestSelenium
{
    public static void test1()
    {
        try
        {
            FirefoxProfile profile = new FirefoxProfile();
            profile.setPreference("browser.startup.page", 0);
            profile.setPreference("browser.startup.homepage", "about:blank");
            // profile.setPreference("general.config.filename", "mozilla.cfg");
            // profile.setPreference("general.config.obscure_value", 0);
            profile.setPreference("startup.homepage_override_url", "about:blank");
            profile.setPreference("startup.homepage_welcome_url", "about:blank");
            profile.setPreference("startup.homepage_welcome_url.additional", "about:blank");

            WebDriver driver = new FirefoxDriver(profile);
            WebDriver driver2 = new FirefoxDriver(profile);

            // And now use this to visit Google
            driver2.get("http://www.google.com");
            driver.get("http://www.google.com");
            // NB: should wait for it to complete loading!!!
            // Alternatively the same thing can be done like this
            // driver.navigate().to("http://www.google.com");

            // Find the text input element by its name
            WebElement element = driver.findElement(By.name("q"));

            // Enter something to search for
            element.sendKeys("Cheese!");

            // Now submit the form. WebDriver will find the form for us from the element
            element.submit();

            // Check the title of the page
            System.out.println("Page title is: " + driver.getTitle());

            // Google's search is rendered dynamically with JavaScript.
            // Wait for the page to load, timeout after 10 seconds
            (new WebDriverWait(driver, 10)).until(new ExpectedCondition<Boolean>()
            {
                public Boolean apply(WebDriver d)
                {
                    return d.getTitle().toLowerCase().startsWith("cheese!");
                }
            });

            // Should see: "cheese! - Google Search"
            out("Page title is: " + driver.getTitle());

            // Close the browser
            driver.quit();
            driver2.quit();

            out("Done.");
            out("Well done.");
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void test2(org.apache.http.client.CookieStore cookieStore)
    {
        try
        {
            // create profile
            FirefoxProfile profile = new FirefoxProfile();
            profile.setPreference("browser.startup.homepage", "about:blank");
            profile.setPreference("browser.startup.page", 0);

            // create driver
            RemoteWebDriver driver = new FirefoxDriver(profile);
            driver.manage().timeouts().pageLoadTimeout(20, TimeUnit.SECONDS);
            driver.manage().timeouts().setScriptTimeout(20, TimeUnit.SECONDS);
            driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);

            driver.get(Config.Site);
            loadCookies(driver, cookieStore);
            listCookies(driver);

            out("loading");
            driver.get("http://colonelcassad.livejournal.com/2532366.html?format=light");
            listCookies(driver);
            out("begin sleep");
            // Thread.sleep(30 * 1000);
            out("end sleep");
            // TODO: check loaded fine
            WebElement b_massaction = driver.findElementByXPath("//div[@class=\"b-massaction\"]");
            if (b_massaction != null)
            {
                out("found massaction");
            }
            else
            {
                out("no massaction");
            }
            WebElement el = driver.findElement(By.tagName("body"));
            // http://stackoverflow.com/questions/10520294/locating-child-nodes-of-webelements-in-selenium
            // https://www.simple-talk.com/dotnet/.net-framework/xpath,-css,-dom-and-selenium-the-rosetta-stone
            // http://www.w3.org/TR/xpath/#path-abbrev
            if (el != null)
            {
                out("found body");
            }
            else
            {
                out("no body");
            }
            b_massaction = driver.findElement(By.tagName("div").className("b-massaction"));
            if (b_massaction != null)
            {
                out("found massaction2");
            }
            else
            {
                out("no massaction2");
            }
            // Expand
            // div class=b-massaction
            // ..div class=b-massaction-checkall
            // ....input id=checkall type=checkbox
            // ..div class=b-ljbutton
            // ....button value=expand data-value=expand name=mode type=submit
            // TODO
            String pageSource = driver.getPageSource();
            // try JSoup

            // Close the browser
            driver.quit();

            out("Done.");
            out("Well done.");
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static ProxyServer startProxyServer() throws Exception
    {
        ProxyServer proxy_server = new ProxyServer(10, 10);
        proxy_server.setFiltering(true);
        proxy_server.start();
        return proxy_server;
    }

    public static void test3()
    {
        try
        {
            ProxyServer proxy_server = startProxyServer();
            String proxy_url = "localhost:" + proxy_server.getPort();

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
            RemoteWebDriver driver = new FirefoxDriver(new FirefoxBinary(), profile, caps);
            driver.manage().timeouts().pageLoadTimeout(Config.Timeout, TimeUnit.SECONDS);
            driver.manage().timeouts().setScriptTimeout(Config.Timeout, TimeUnit.SECONDS);
            driver.manage().timeouts().implicitlyWait(Config.Timeout, TimeUnit.SECONDS);

            driver.get("http://" + Config.Site + "/login.bml");

            // Check that login form is present:
            // .div class=s-body
            // ...(intermediate nested levels)
            // ...input id=user name=user class=b-input type=text
            // ...input id=lj_loginwidget_password name=password class=b-input
            // ...type=password button name="action:login" type=submit
            // These elements may be multiple on the page (multiple copies of login form)
            // with only one of them visible, so find which one it is
            By by_user = By.xpath(xpathTagClassName("input", "b-input", "user"));
            By by_password = By.xpath(xpathTagClassName("input", "b-input", "password"));
            By by_login = By.xpath(xpathTagName("button", "action:login"));

            WebElement el_user = getDisplayed(driver.findElements(by_user));
            WebElement el_password = getDisplayed(driver.findElements(by_password));
            WebElement el_login = getDisplayed(driver.findElements(by_login));

            el_user.sendKeys(Config.LoginUser);
            el_password.sendKeys(Config.LoginPassword);
            el_login.click();

            String url = driver.getCurrentUrl();
            if (!url.equalsIgnoreCase("http://www.livejournal.com/"))
                throw new Exception("Login failure");

            String saveDir = "F:\\WINAPPS\\LJExport\\@develop\\";
            driver.get("http://colonelcassad.livejournal.com/2532366.html?format=light");
            Util.writeToFile(saveDir + "colonelcassad-2532366.html", driver.getPageSource());

            // driver.get("http://oboguev.livejournal.com/1852047.html");
            // Util.writeToFile(saveDir + "oboguev-1852047.html", driver.getPageSource());

            WebElement b_massaction = getOne(driver.findElementsByXPath("//div[@class=\"b-massaction\"]"));
            WebElement massaction_checkbox = getOne(b_massaction.findElements(By
                    .xpath(".//input[@id='checkall'][@type='checkbox']")));
            WebElement massaction_expand = getOne(b_massaction.findElements(By.xpath(".//button[@value='expand'][@type='submit']")));

            if (b_massaction.isDisplayed() &&
                    massaction_checkbox.isDisplayed() &&
                    massaction_expand.isDisplayed())
            {
                StringBuilder sb = new StringBuilder();
                new Formatter(sb).format("//li[%1$s and (%2$s or %3$s)]",
                        elementClassContains("b-leaf-actions-item"),
                        elementClassContains("b-leaf-actions-expand"),
                        elementClassContains("b-leaf-actions-expandchilds"));

                List<WebElement> els;
                List<WebElement> vels;

                /*
                 * while logically this would not be needed, but for whatever
                 * obscure reason it triggers something on the browser's side of
                 * the WebDriver that makes subsequent code work without locking up
                 * in a wait
                 */
                els = driver.findElements(By.xpath(sb.toString()));
                vels = selectDisplayed(els);

                massaction_checkbox.click();
                massaction_expand.click();

                long t0 = System.currentTimeMillis();

                for (;;)
                {
                    els = driver.findElements(By.xpath(sb.toString()));
                    if (!anyDisplayed(els))
                        break;
                    // vels = selectDisplayed(els);
                    // if (vels.size() == 0)
                    //     break;
                    Thread.sleep(500);
                    if (System.currentTimeMillis() - t0 > Config.Timeout * 1000)
                        throw new Exception("Unable to expand comments");
                }

                Util.writeToFile(saveDir + "colonelcassad-2532366-after.html", driver.getPageSource());

                JSOUP.dumpNodesOffset(JSOUP.flatten(JSOUP.parseHtml(driver.getPageSource())));
            }

            driver.quit();

            proxy_server.stop();

            out("Done.");
            out("Well done.");
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static String elementClassContains(String cname) throws Exception
    {
        return "contains(concat(' ', normalize-space(@class), ' '), ' " + cname + " ')";
    }

    private static String xpathTagClassName(String tag, String cl, String name) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb))
        {
            formatter.format("//%1$s[@class='%2$s'][@name='%3$s']", tag, cl, name);
            return sb.toString();
        }
    }

    private static String xpathTagName(String tag, String name) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb))
        {
            formatter.format("//%1$s[@name='%2$s']", tag, name);
            return sb.toString();
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

    private static WebElement getDisplayed(List<WebElement> els) throws Exception
    {
        for (WebElement el : els)
        {
            if (el.isDisplayed())
                return el;
        }
        throw new Exception("Unable to find required disiplayed element in the page");
    }

    private static WebElement getOne(List<WebElement> els) throws Exception
    {
        if (els.size() == 0)
            throw new Exception("Unable to find required element in the page");
        if (els.size() != 1)
            throw new Exception("Unable to find required element in the page (multiple matching alements are present)");
        return els.get(0);
    }

    private static void loadCookies(RemoteWebDriver driver, org.apache.http.client.CookieStore cookieStore) throws Exception
    {
        out("loading cookies");
        for (org.apache.http.cookie.Cookie xc : cookieStore.getCookies())
        {
            if (!xc.getDomain().equalsIgnoreCase(Config.Site))
                continue;
            org.openqa.selenium.Cookie cookie = new org.openqa.selenium.Cookie(xc.getName(), xc.getValue(), xc.getDomain(),
                    xc.getPath(), xc.getExpiryDate(), xc.isSecure(), false);
            driver.manage().addCookie(cookie);
        }
        out("loaded cookies");
    }

    private static void listCookies(RemoteWebDriver driver) throws Exception
    {
        out("--- cookies ---");
        for (Cookie c : driver.manage().getCookies())
            out("    " + c.toString());
        out("--- end cookies ---");
    }

    public static void out(String s) throws Exception
    {
        Main.out(s);
    }
}
