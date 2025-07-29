package my.LJExport.runtime.browsers;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import org.openqa.selenium.JavascriptExecutor;

public class FirefoxUserAgent
{
    private static String resolvedUserAgent;

    public static synchronized String getUserAgent()
    {
        if (resolvedUserAgent == null)
        {
            System.setProperty("webdriver.gecko.driver", GeckoDriverFinder.findGeckoDriverInPath());

            FirefoxOptions options = new FirefoxOptions();
            options.addArguments("-headless");

            WebDriver driver = new FirefoxDriver(options);
            try
            {
                driver.get("about:blank");
                String userAgent = (String) ((JavascriptExecutor) driver)
                        .executeScript("return navigator.userAgent;");
                resolvedUserAgent = userAgent;
            }
            finally
            {
                driver.quit();
            }
        }

        return resolvedUserAgent;
    }
}
