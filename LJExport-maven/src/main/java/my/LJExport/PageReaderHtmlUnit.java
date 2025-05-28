package my.LJExport;

import org.apache.http.HttpStatus;
import org.w3c.dom.Node;

import com.gargoylesoftware.htmlunit.IncorrectnessListener;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HTMLParserListener;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.io.*;
import java.util.*;

public class PageReaderHtmlUnit implements PageReader
{
    private String rurl;
    private String rid;
    private String fileDir;
    private int npages = -1;
    private WebClient webClient;

    private final static int COUNT_PAGES = (1 << 0);
    private final static int REMOVE_MAIN_TEXT = (1 << 1);

    static class Context
    {
        public WebClient webClient;

        public void close()
        {
            if (webClient != null)
            {
                webClient.close();
                webClient = null;
            }
        }
    }

    static class IncorrectnessListenerX implements IncorrectnessListener
    {
        private IncorrectnessListener listener;

        IncorrectnessListenerX(WebClient wc)
        {
            listener = wc.getIncorrectnessListener();
            wc.setIncorrectnessListener(this);
        }

        public void notify(String message, Object origin)
        {
            if (message.startsWith("Obsolete content type encountered"))
                return;
            listener.notify(message, origin);
        }
    }

    static class HTMLParserListenerX implements HTMLParserListener
    {
        private HTMLParserListener listener;

        HTMLParserListenerX(WebClient wc)
        {
            listener = wc.getHTMLParserListener();
            wc.setHTMLParserListener(this);
        }

        public void error(String message, java.net.URL url, String html, int line, int column, String key)
        {
            listener.error(message, url, html, line, column, key);
        }

        public void warning(String message, java.net.URL url, String html, int line, int column, String key)
        {
            listener.warning(message, url, html, line, column, key);
        }
    }

    public static Context makeContext() throws Exception
    {
        Context context = new Context();
        context.webClient = new WebClient(Config.HtmlUnitBrowserVersion);
        new IncorrectnessListenerX(context.webClient);
        new HTMLParserListenerX(context.webClient);
        // ### cookie manager (per-thread)
        // ### set cache (per-thread)
        return context;
    }

    public PageReaderHtmlUnit(String rurl, String fileDir, Context context)
    {
        rurl = "2532366.html"; // ###
        // rurl = "2532366.html"; // test: colonelcassad
        // rurl = "5182367.html"; // test: oboguev (private)
        this.rurl = rurl;
        this.rid = rurl.substring(0, rurl.indexOf('.'));
        this.fileDir = fileDir + File.separator;
        this.webClient = context.webClient;
    }

    public void readPage() throws Exception
    {
        HtmlPage page = webClient.getPage("http://" + Config.MangledUser + "." + Config.Site + "/" + rurl + "?format=light");
        String pageAsXml = page.asXml();
        String pageAsText = page.asText();

        out("-----");
    }

    public static void out(String s) throws Exception
    {
        Main.out(s);
    }
}
