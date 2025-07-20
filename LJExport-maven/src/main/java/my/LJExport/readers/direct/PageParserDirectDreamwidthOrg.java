package my.LJExport.readers.direct;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;

import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;

public class PageParserDirectDreamwidthOrg extends PageParserDirectBase
{
    public PageParserDirectDreamwidthOrg(PageContentSource pageContentSource)
    {
        super(pageContentSource);
    }

    public PageParserDirectDreamwidthOrg(PageParserDirectBase classic)
    {
        super(classic);
    }

    @Override
    public void removeJunk(int flags) throws Exception
    {
        Node primary = JSOUP.exactlyOne(JSOUP.findElements(pageRoot, "div", "id", "primary"));

        List<Node> delvec = new ArrayList<>();
        for (Node n : JSOUP.findElements(pageRoot, "div"))
        {
            if (n == primary || JSOUP.isInTree(n, primary) || JSOUP.isInTree(primary, n))
            {
                // leave it
            }
            else
            {
                delvec.add(n);
            }
        }

        JSOUP.removeElements(pageRoot, delvec);
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "header"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "footer"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "secondary"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "tertiary"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "lj_controlstrip"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(pageRoot, "div", "footer"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(pageRoot, "div", "bottomcomment"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithAllClasses(pageRoot, "div", Util.setOf("comment-pages", "toppages")));
        JSOUP.removeElements(pageRoot,
                JSOUP.findElementsWithAllClasses(pageRoot, "div", Util.setOf("comment-pages", "bottompages")));

        if (0 != (flags & (REMOVE_MAIN_TEXT | COUNT_PAGES | CHECK_HAS_COMMENTS | EXTRACT_COMMENTS_JSON)))
            throw new Exception("Not implemented");

        if (0 != (flags & REMOVE_SCRIPTS))
            removeScripts();

        setEncoding();

        mapProxiedImageURLs();
    }

    private void setEncoding() throws Exception
    {
        Element head = findHead();

        JSOUP.removeElements(head, JSOUP.findElements(head, "meta"));

        // Create and append <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
        Element meta = new Element(Tag.valueOf("meta"), "");
        meta.attr("http-equiv", "Content-Type");
        meta.attr("content", "text/html; charset=utf-8");
        head.appendChild(meta);
    }
    
    private void removeScripts() throws Exception
    {
        List<Node> vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "script");
        JSOUP.removeElements(pageRoot, vnodes);

        vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "noscript");
        JSOUP.removeElements(pageRoot, vnodes);
    }

    @Override
    public void unsizeArticleHeight() throws Exception
    {
        // nothing to do
    }

    @Override
    public void removeNonArticleBodyContent() throws Exception
    {
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "comments"));
    }

    /* ============================================================================= */

    @Override
    public Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception
    {
        throw new Exception("Not implemented");
    }

    @Override
    public void injectComments(Element commentsSection, CommentsTree commentTree) throws Exception
    {
        throw new Exception("Not implemented");
    }

    @Override
    public Element findMainArticle() throws Exception
    {
        return JSOUP.asElement(JSOUP.exactlyOne(JSOUP.findElements(pageRoot, "div", "id", "canvas")));
    }

    private void mapProxiedImageURLs() throws Exception
    {
        for (Node n : JSOUP.findElements(pageRoot, "img"))
        {
            String href = JSOUP.getAttribute(n, "src");
            if (href != null && href.startsWith("https://p.dreamwidth.org/"))
            {
                String newref = mapProxiedImageURL(href);
                JSOUP.updateAttribute(n, "src", newref);
                JSOUP.setAttribute(n, "original-src", href);
            }
        }

        for (Node n : JSOUP.findElements(pageRoot, "a"))
        {
            String href = JSOUP.getAttribute(n, "href");
            if (href != null && href.startsWith("https://p.dreamwidth.org/"))
            {
                String newref = mapProxiedImageURL(href);
                JSOUP.updateAttribute(n, "href", newref);
                JSOUP.setAttribute(n, "original-href", href);
            }
        }
    }

    /**
     * Converts a proxied Dreamwidth image URL to its original form. https://p.dreamwidth.org/AAA/BBB/REMAINDER -> http://REMAINDER
     */
    private String mapProxiedImageURL(String proxiedUrl)
    {
        final String prefix = "https://p.dreamwidth.org/";
        String original = mapProxiedURI(proxiedUrl, prefix);
        return "http://" + original;
    }
    
    public static String mapProxiedURI(String proxiedUrl, String prefix)
    {
        if (!proxiedUrl.startsWith(prefix))
            throw new IllegalArgumentException("Unexpected URL format: " + proxiedUrl);

        // Trim the prefix
        String remainder = proxiedUrl.substring(prefix.length());

        // Skip first segment (AAA)
        int firstSlash = remainder.indexOf('/');
        if (firstSlash < 0)
            throw new IllegalArgumentException("Malformed proxied URL (no AAA): " + proxiedUrl);

        // Skip second segment (BBB)
        int secondSlash = remainder.indexOf('/', firstSlash + 1);
        if (secondSlash < 0)
            throw new IllegalArgumentException("Malformed proxied URL (no BBB): " + proxiedUrl);

        // What remains is the original URL path
        String original = remainder.substring(secondSlash + 1);
        
        return original ;
    }
    
    /* ======================================================================== */
    
    public void removeJunkProfile() throws Exception
    {
        removeScripts();
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "masthead"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "statistics"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "shim-alpha"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "account-links"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "role", "banner"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "nav", "role", "navigation"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "class", "actions"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "footer", "role", "contentInfo"));
    }

    @Override
    public Element findContentWrapper() throws Exception
    {
        Node entry = JSOUP.requiredOuter(JSOUP.findElementsWithClass(pageRoot, "div", "entry"));
        Node inner = JSOUP.exactlyOne(JSOUP.findElementsWithClass(JSOUP.directChildren(entry), "div", "inner"));
        return JSOUP.asElement(inner);
    }
}
