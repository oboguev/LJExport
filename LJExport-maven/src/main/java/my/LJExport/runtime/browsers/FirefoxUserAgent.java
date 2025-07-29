package my.LJExport.runtime.browsers;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import my.LJExport.runtime.Util;

import org.openqa.selenium.JavascriptExecutor;

public class FirefoxUserAgent
{
    public static String getUserAgent()
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
            return userAgent;
        }
        finally
        {
            driver.quit();
        }
    }
}
