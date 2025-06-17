package my.LJExport.readers.direct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.LinkDownloader;
import my.LJExport.runtime.Util;
import my.LJExport.xml.JSOUP;

public abstract class PageParserDirectBase
{
    private final PageContentSource pageContentSource;

    public PageParserDirectBase(PageContentSource pageContentSource)
    {
        this.pageContentSource = pageContentSource;
    }

    public PageParserDirectBase(PageParserDirectBase other)
    {
        this.pageContentSource = other.pageContentSource;

        this.npages = other.npages;
        this.hasComments = other.hasComments;
        this.pageRoot = other.pageRoot;
        this.pageSource = other.pageSource;
        this.rurl = other.rurl;
        this.rid = other.rid;
    }

    protected String getPageSource() throws Exception
    {
        return pageContentSource.getPageSource();
    }

    /* ============================================================== */

    public final static int COUNT_PAGES = (1 << 0);
    public final static int CHECK_HAS_COMMENTS = (1 << 1);
    public final static int REMOVE_MAIN_TEXT = (1 << 2);
    public final static int REMOVE_SCRIPTS = (1 << 3);

    public int npages = -1;
    public Boolean hasComments = null;

    public Node pageRoot;
    public String pageSource;

    public String rurl;
    public String rid;

    public void resetParser()
    {
        npages = -1;
        hasComments = null;

        pageRoot = null;
        pageSource = null;

        rurl = null;
        rid = null;
    }

    /* ============================================================== */

    public static void out(String s)
    {
        Main.out(s);
    }

    public static void err(String s)
    {
        Main.err(s);
    }

    public void parseHtml(String html) throws Exception
    {
        this.pageSource = html;
        this.pageRoot = JSOUP.parseHtml(html);
    }

    public void parseHtml() throws Exception
    {
        parseHtml(this.pageSource);
    }

    protected boolean isBadGatewayPage(String html) throws Exception
    {
        if (html.contains("Bad Gateway:") || html.contains("Gateway Timeout"))
        {
            if (pageRoot == null)
                pageRoot = JSOUP.parseHtml(html);
            List<Node> vel = JSOUP.findElements(JSOUP.flatten(pageRoot), "body");
            if (vel.size() != 1)
                throw new Exception("Unable to find BODY element in the html page");
            for (Node n : JSOUP.getChildren(vel.get(0)))
            {
                if (n instanceof TextNode)
                {
                    TextNode tn = (TextNode) n;
                    if (tn.text().contains("Bad Gateway:") || tn.text().contains("Gateway Timeout"))
                        return true;
                }
            }
        }

        return false;
    }

    /*static*/ public boolean downloadExternalLinks(Node root, String linksDir) throws Exception
    {
        if (linksDir == null || Config.DownloadFileTypes == null || Config.DownloadFileTypes.size() == 0)
            return false;

        boolean downloaded = false;

        downloaded = downloaded || downloadExternalLinks(root, linksDir, "a", "href", true);
        downloaded = downloaded || downloadExternalLinks(root, linksDir, "img", "src", false);

        return downloaded;
    }

    /*static*/ private boolean downloadExternalLinks(Node root, String linksDir, String tag, String attr,
            boolean filterDownloadFileTypes) throws Exception
    {
        boolean downloaded = false;

        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);

            if (LinkDownloader.shouldDownload(href, filterDownloadFileTypes))
            {
                String referer = "http://" + Config.MangledUser + "." + Config.Site + "/" + rurl;
                String newref = LinkDownloader.download(linksDir, href, referer);
                if (newref != null)
                {
                    JSOUP.updateAttribute(n, attr, newref);
                    JSOUP.setAttribute(n, "original-" + attr, href);
                    downloaded = true;
                }
            }
        }

        return downloaded;
    }

    public boolean remapLocalRelativeLinks(String oldPrefix, String newPrefix) throws Exception
    {
        boolean remapped = false;

        remapped = remapped || remapLocalRelativeLinks(oldPrefix, newPrefix, "a", "href");
        remapped = remapped || remapLocalRelativeLinks(oldPrefix, newPrefix, "img", "src");

        return remapped;
    }

    private boolean remapLocalRelativeLinks(String oldPrefix, String newPrefix, String tag, String attr) throws Exception
    {
        boolean remapped = false;
        final String fileProtocol = "file://";

        for (Node n : JSOUP.findElements(this.pageRoot, tag))
        {
            String ref = JSOUP.getAttribute(n, attr);
            if (ref != null)
            {
                if (ref.startsWith(fileProtocol))
                    ref = ref.substring(fileProtocol.length());

                if (ref.startsWith(oldPrefix))
                {
                    ref = newPrefix + ref.substring(oldPrefix.length());
                    JSOUP.updateAttribute(n, attr, ref);
                    remapped = true;
                }
            }
        }

        return remapped;
    }

    /* ============================================================== */

    public String detectPageStyle() throws Exception
    {
        List<Node> vnodes = JSOUP.findElements(pageRoot, "article");
        String style = null;

        for (Node n : vnodes)
        {
            Set<String> classes = JSOUP.getClassesLowercase(n);

            if (classes.contains("aentry") && classes.contains("aentry--post2017"))
                style = detectPageStyle(style, "new-style");

            if (classes.contains("b-singlepost") && classes.contains("hentry"))
                style = detectPageStyle(style, "classic");

            if (classes.contains("b-singlepost-body") && classes.contains("entry-content"))
                style = detectPageStyle(style, "classic");
        }

        if (style == null)
            throw new Exception("Unable to detect page style (missing indicators)");

        return style;
    }

    private String detectPageStyle(String s1, String s2) throws Exception
    {
        if (s1 == null || s1.equals(s2))
            return s2;

        throw new Exception("Unable to detect page style (conflicting indicators)");
    }

    /* ============================================================== */

    public abstract void removeJunk(int flags) throws Exception;

    public abstract Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception;

    public abstract void injectComments(Element commentsSection, CommentsTree commentTree) throws Exception;

    public abstract Element findMainArticle() throws Exception;

    /* ============================================================== */

    protected void injectHtml(Element commentsSection, String html, String base_url)
    {
        List<Node> nodes = org.jsoup.parser.Parser.parseFragment(html, commentsSection, base_url);
        nodes = new ArrayList<>(nodes);

        for (Node node : nodes)
            commentsSection.appendChild(node);
    }

    protected String expandVars(String template, Map<String, String> vars)
    {
        String res = template;

        for (String key : vars.keySet())
        {
            String value = vars.get(key);
            if (value != null)
                res = res.replace("{$" + key + "}", value);
        }

        return res;
    }

    protected void removeNonArticleParents() throws Exception
    {
        // find article tags
        List<Node> articles = JSOUP.findElements(pageRoot, "article");

        // something wrong? leave it alone
        if (articles.size() == 0)
            return;

        // in older LJ page styles <div id=comments> can be not under article, but standalone 
        List<Node> alones = findStandaloneCommentsSections(pageRoot);
        List<Node> articles_and_alones = JSOUP.union(articles, alones);

        // traverse upwards from articles and mark the nodes to keep
        Set<Node> keepSet = new HashSet<Node>();
        for (Node n : articles_and_alones)
        {
            JSOUP.enumParents(keepSet, n);
            keepSet.add(n);
        }

        // traverse from root recursively downwards (like in flatten)
        // marking all <table>, <tr> and <td> not in created keep set
        // to be deleted
        List<Node> delvec = new ArrayList<>();
        removeNonArticleParents_enum_deletes(delvec, keepSet, new HashSet<Node>(articles_and_alones), pageRoot);

        // delete these elements
        if (delvec.size() != 0)
            JSOUP.removeElements(pageRoot, delvec);
    }

    private void removeNonArticleParents_enum_deletes(List<Node> delvec, Set<Node> keepSet, Set<Node> stopSet, Node n)
            throws Exception
    {
        if (n == null)
            return;

        if (stopSet.contains(n))
        {
            // JSOUP.dumpNodeOffset(n, "STOP *** ");
            return;
        }

        if (n instanceof Element)
        {
            Element el = (Element) n;
            String name = JSOUP.nodeName(el);
            if (name.equalsIgnoreCase("table") || name.equalsIgnoreCase("tr") || name.equalsIgnoreCase("td"))
            {
                if (!keepSet.contains(n))
                {
                    delvec.add(n);
                    // JSOUP.dumpNodeOffset(n, "DELETE *** ");
                    removeNonArticleParents_enum_deletes(delvec, keepSet, stopSet, JSOUP.nextSibling(n));
                    return;
                }
            }
        }

        // JSOUP.dumpNodeOffset(n);

        removeNonArticleParents_enum_deletes(delvec, keepSet, stopSet, JSOUP.firstChild(n));
        removeNonArticleParents_enum_deletes(delvec, keepSet, stopSet, JSOUP.nextSibling(n));
    }

    protected boolean isPositiveNumber(String s)
    {
        try
        {
            int i = Integer.parseInt(s);
            return i >= 1;
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    protected Boolean hasComments(Boolean has1, Boolean has2)
    {
        if (has1 == null || has1.equals(has2))
            return has2;
        throw new RuntimeException("Page has conflicting comment count sections");
    }

    // in older LJ page styles <div id=comments> can be not under article, but standalone 
    // find <div id=comments> sections that are not under <article>  
    protected List<Node> findStandaloneCommentsSections(Node root) throws Exception
    {
        List<Node> vn = new ArrayList<>();

        for (Node n : JSOUP.findElements(root, "div", "id", "comments"))
        {
            if (!JSOUP.hasParent(n, "article"))
                vn.add(n);
        }

        return vn;
    }

    /* ============================================================== */

    public String extractCleanedHead() throws Exception
    {
        List<Node> heads = JSOUP.findElements(pageRoot, "head");
        if (heads.size() != 1)
            throw new Exception("Unable to locate the HEAD tag");

        // perform deep clone
        Node head = heads.get(0).clone();

        // remove individual entries
        JSOUP.removeElements(head, JSOUP.findElements(head, "title"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "meta"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "next"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "prev"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "noscript"));
        JSOUP.removeElements(head, JSOUP.findComments(head));
        JSOUP.removeWhitespaceNodes(head);

        String outerHtml = head.outerHtml();
        return outerHtml;
    }

    public void cleanHead(String titleText) throws Exception
    {
        List<Node> heads = JSOUP.findElements(pageRoot, "head");
        if (heads.size() != 1)
            throw new Exception("Unable to locate the HEAD tag");

        // perform deep clone
        Element head = (Element) heads.get(0);

        // remove individual entries
        JSOUP.removeElements(head, JSOUP.findElements(head, "title"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "meta"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "next"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "prev"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "noscript"));
        // JSOUP.removeElements(head, JSOUP.findComments(head));

        // Create and append <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
        Element meta = new Element(Tag.valueOf("meta"), "");
        meta.attr("http-equiv", "Content-Type");
        meta.attr("content", "text/html; charset=utf-8");
        head.appendChild(meta);

        // Create and append <title>....</title>
        Element title = new Element(Tag.valueOf("title"), "");
        title.text(titleText);
        head.appendChild(title);
    }

    // remove comments section and other parts except article body
    public void removeNonArticleBodyContent() throws Exception
    {
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "comments"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(pageRoot, "div", "acomments"));

        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "hello-world"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(pageRoot, "div", "b-fader"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(pageRoot, "div", "b-singlepost-reactions"));

        List<Node> vel = new ArrayList<>();

        for (Node n : JSOUP.findElements(pageRoot, "div"))
        {
            if (null != JSOUP.getAttribute(n, "suggestion-for-unlogged") ||
                    null != JSOUP.getAttribute(n, "rd-post-view-related-list"))
            {
                vel.add(n);
            }
        }

        JSOUP.removeElements(pageRoot, vel);
    }

    public Element getBodyTag() throws Exception
    {
        List<Node> bodies = JSOUP.findElements(pageRoot, "body");
        if (bodies.size() != 1)
            throw new Exception("Unable to locate the BODY tag");
        return (Element) bodies.get(0);
    }

    // from article tag scan upwards
    // in all "td" remove numeric "height"
    // such as td height=585
    public void unsizeArticleHeight() throws Exception
    {
        List<Node> articles = JSOUP.findElements(pageRoot, "article");

        for (Node article : articles)
        {
            for (Node ap : JSOUP.enumParents(article))
            {
                if (ap instanceof Element)
                {
                    Element el = (Element) ap;

                    if (el.tagName().equalsIgnoreCase("td"))
                    {
                        String height = JSOUP.getAttribute(el, "height");
                        if (height != null && isNumber(height))
                            JSOUP.deleteAttribute(el, "height");
                    }
                }
            }
        }
    }

    private boolean isNumber(String s)
    {
        try
        {
            int v = Integer.parseInt(s);
            Util.unused(v);
            return true;
        }
        catch (Exception ex)
        {
            return false;
        }
    }
}
