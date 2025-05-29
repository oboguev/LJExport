package my.LJExport.test;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

// import java.io.*;
// import java.util.*;

public class TestHtmlUnit
{
    // https://sourceforge.net/p/htmlunit/bugs/1737/
    public static void test()
    {
        try (WebClient webClient = new WebClient(BrowserVersion.FIREFOX_38);)
        {
            // WebClient webClient = new WebClient(BrowserVersion.FIREFOX_38);

            HtmlPage page = webClient.getPage("http://colonelcassad.livejournal.com/2532366.html?format=light");
            
            @SuppressWarnings("unused")
            String pageAsXml = page.asXml();
            
            @SuppressWarnings("unused")
            String pageAsText = page.asText();
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
