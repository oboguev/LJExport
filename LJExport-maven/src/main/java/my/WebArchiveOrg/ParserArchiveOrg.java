package my.WebArchiveOrg;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;

import my.LJExport.html.JSOUP;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Web;

public class ParserArchiveOrg extends PageParserDirectBasePassive
{
    public void removeArchiveJunk() throws Exception
    {
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "wm-ipp-print"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "wm-ipp-base"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "script"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "noscript"));

        for (Node n : JSOUP.findElements(pageRoot, "meta"))
        {
            String heq = JSOUP.getAttribute(n, "http-equiv");
            if (heq != null && heq.equalsIgnoreCase("Content-Type"))
                JSOUP.removeElement(pageRoot, n);
        }

        // Create and append <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
        Element meta = new Element(Tag.valueOf("meta"), "");
        meta.attr("http-equiv", "Content-Type");
        meta.attr("content", "text/html; charset=utf-8");
        findHead().appendChild(meta);
    }
    
    public void resolveAbsoluteURLs(String finalURL) throws Exception
    {
        if (finalURL != null && finalURL.length() != 0)
        {
            JSOUP.resolveURLInTree(pageRoot, "a", "href", finalURL);
            JSOUP.resolveURLInTree(pageRoot, "img", "src", finalURL);
        }
    }

    public String extractCharset() throws Exception
    {
        for (Node n : JSOUP.findElements(pageRoot, "meta"))
        {
            String heq = JSOUP.getAttribute(n, "http-equiv");
            String content = JSOUP.getAttribute(n, "content");
            if (heq != null && heq.equalsIgnoreCase("Content-Type") && content != null)
            {
                String charset = Web.extractCharset(content);
                if (charset != null)
                    return charset;
            }
        }

        return null;
    }
}
