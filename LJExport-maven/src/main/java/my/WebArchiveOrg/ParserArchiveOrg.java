package my.WebArchiveOrg;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.Web;

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
            boolean remove = false;

            String heq = JSOUP.getAttribute(n, "http-equiv");
            if (heq != null && heq.equalsIgnoreCase("Content-Type"))
                remove = true;

            // <meta charset="UTF-8">
            String charset = JSOUP.getAttribute(n, "charset");
            if (charset != null)
                remove = true;

            if (remove)
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
        String result = null;

        for (Node n : JSOUP.findElements(pageRoot, "meta"))
        {
            // also handle <meta charset="UTF-8">
            String heq = JSOUP.getAttribute(n, "http-equiv");
            String content = JSOUP.getAttribute(n, "content");
            String charset = JSOUP.getAttribute(n, "charset");

            if (heq != null && heq.equalsIgnoreCase("Content-Type") && content != null)
            {
                String cs = Web.extractCharsetFromContentType(content);
                if (cs != null)
                    result = foundCharset(result, cs);
            }
            else if (charset != null)
            {
                result = foundCharset(result, charset);
            }
        }

        return null;
    }

    private String foundCharset(String old, String found) throws Exception
    {
        if (old == null)
        {
            return found;
        }
        else if (found == null)
        {
            return old;
        }
        else
        {
            if (!old.equalsIgnoreCase(found))
                throw new Exception(String.format("Conflicting META charset settings: %s and %s", old, found));
            return old;
        }
    }
}
