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
            String geckoDriver = System.getProperty("webdriver.gecko.driver");
            if (geckoDriver == null || geckoDriver.trim().length() == 0)
                System.setProperty("webdriver.gecko.driver", GeckoDriverFinder.findGeckoDriverInPath(true));

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
