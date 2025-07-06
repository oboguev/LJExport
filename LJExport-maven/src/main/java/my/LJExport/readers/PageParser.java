package my.LJExport.readers;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.html.JSOUP;
import my.LJExport.runtime.LJUtil;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.links.LinkDownloader;

public abstract class PageParser
{
    public static class MissingCommentsTreeRootException extends Exception
    {
        private static final long serialVersionUID = 1L;

        MissingCommentsTreeRootException(String s)
        {
            super(s);
        }
    }

    protected final static int COUNT_PAGES = (1 << 0);
    protected final static int CHECK_COMMENTS_MERGEABLE = (1 << 1);
    protected final static int REMOVE_MAIN_TEXT = (1 << 2);
    protected final static int REMOVE_SCRIPTS = (1 << 3);

    protected boolean offline = false;

    protected int npages = -1;

    protected Node pageRoot;
    protected String pageSource;

    protected String rurl;
    protected String rid;

    public static void out(String s)
    {
        Main.out(s);
    }

    public static void err(String s)
    {
        Main.err(s);
    }

    protected void parseHtml(String html) throws Exception
    {
        this.pageSource = html;
        this.pageRoot = JSOUP.parseHtml(html);
    }

    protected void parseHtml() throws Exception
    {
        parseHtml(this.pageSource);
    }

    protected void removeJunk(int flags) throws Exception
    {
        /*
         * Record is a set of nested tables, with relevant content located under the <article> tag.
         * Traverse the tree and delete all tables (<table>, <tr> and <td> tags) that do not
         * have <article> as their eventual child.
         */
        removeJunk_1();

        if (0 != (flags & COUNT_PAGES))
        {
            // find out if there are multiple pages with comments
            npages = numberOfCommentPages();
        }

        if (0 != (flags & CHECK_COMMENTS_MERGEABLE))
        {
            checkCommentsMergeable();
        }

        /*
         * Remove known sections that contain no essential record-related information.
         */
        JSOUP.removeElements(pageRoot, "div", "id", "lj_controlstrip_new");
        JSOUP.removeElementsWithClass(pageRoot, "iframe", "b-watering-commentator");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-popup-outer");
        JSOUP.removeElementsWithClass(pageRoot, "div", "threeposts__inner");
        JSOUP.removeElementsWithClass(pageRoot, "div", "entry-unrelated");
        JSOUP.removeElementsWithClass(pageRoot, "div", "ng-scope");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-massaction");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-massaction-anchor");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-massaction-mobile");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-massaction-top");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-xylem");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-singlepost-prevnext");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-tree-best");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-tree-promo");
        JSOUP.removeElementsWithClass(pageRoot, "div", "appwidget-sitemessages");

        for (Node n : JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-singlepost-standout"))
        {
            JSOUP.insertAfter(n, JSOUP.makeElement("br", n));
            JSOUP.insertAfter(n, JSOUP.makeElement("br", n));
            JSOUP.removeNode(n);
        }

        if (0 != (flags & REMOVE_MAIN_TEXT))
        {
            JSOUP.removeElementsWithClass(pageRoot, "div", "b-singlepost-about");
            JSOUP.removeElementsWithClass(pageRoot, "div", "b-singlepost-wrapper");
        }

        if (0 != (flags & REMOVE_SCRIPTS))
        {
            List<Node> vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "script");
            JSOUP.removeElements(pageRoot, vnodes);
        }
    }

    private void removeJunk_1() throws Exception
    {
        // find article tags
        List<Node> articles = JSOUP.findElements(pageRoot, "article");

        // something wrong? leave it alone
        if (articles.size() == 0)
            return;

        // traverse upwards from articles and mark the nodes to keep
        Set<Node> keepSet = new HashSet<Node>();
        for (Node n : articles)
        {
            JSOUP.enumParents(keepSet, n);
            keepSet.add(n);
        }

        // traverse from root recursively downwards (like in flatten)
        // marking all <table>, <tr> and <td> not in created keep set
        // to be deleted
        List<Node> delvec = new ArrayList<>();
        rj1_enum_deletes(delvec, keepSet, new HashSet<Node>(articles), pageRoot);

        // delete these elements
        if (delvec.size() != 0)
            JSOUP.removeElements(pageRoot, delvec);
    }

    private void rj1_enum_deletes(List<Node> delvec, Set<Node> keepSet, Set<Node> stopSet, Node n) throws Exception
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
                    rj1_enum_deletes(delvec, keepSet, stopSet, JSOUP.nextSibling(n));
                    return;
                }
            }
        }

        // JSOUP.dumpNodeOffset(n);

        rj1_enum_deletes(delvec, keepSet, stopSet, JSOUP.firstChild(n));
        rj1_enum_deletes(delvec, keepSet, stopSet, JSOUP.nextSibling(n));
    }

    protected int numberOfCommentPages() throws Exception
    {
        int npages = 1;

        StringBuilder sb = new StringBuilder();

        for (Node n : JSOUP.findElements(pageRoot, "a"))
        {
            String href = JSOUP.getAttribute(n, "href");
            if (href == null)
                continue;
            href = Util.stripAnchor(href);
            if (!LJUtil.isJournalUrl(href, sb))
                continue;
            if (!Util.beginsWith(sb.toString(), this.rurl + "?", sb))
                continue;
            Map<String, String> params = Util.parseUrlParams(sb.toString());
            String np = params.get("page");
            if (np != null)
            {
                try
                {
                    int npg = Integer.parseInt(np);
                    if (npg > npages)
                        npages = npg;
                }
                catch (NumberFormatException ex)
                {
                    out("*** Error: record " + rurl + " contains invalid comments page link");
                }
            }
        }

        return npages;
    }

    protected void checkCommentsMergeable() throws Exception
    {
        List<Node> flat = JSOUP.flatten(pageRoot);
        List<Node> vn = JSOUP.findElementsWithClass(flat, "div", "b-tree-root");

        if (vn.size() == 0)
        {
            if (npages >= 2)
            {
                throwNoCommentTreeRoot();
            }
            else
            {
                List<Node> vel = JSOUP.findElementsWithClass(flat, "div", "b-xylem-nocomment");
                if (vel.size() == 0)
                {
                    throwNoCommentTreeRoot();
                }
            }
        }
        else if (vn.size() != 1)
        {
            throwNoCommentTreeRoot();
        }

        if (npages >= 2)
        {
            Node tr = vn.get(0);
            List<Node> children = JSOUP.getChildren(tr);
            if (children.size() == 0)
                throwNoCommentTreeRoot();
        }
    }

    private void throwNoCommentTreeRoot() throws Exception
    {
        Main.saveDebugPage("badpage-unable-locate-comment-tree-root.html", pageSource);
        throw new Exception("Unable to locate comment tree root");
    }

    protected void mergeComments(Node firstPageRoot) throws Exception
    {
        List<Node> vn1 = JSOUP.findElementsWithClass(JSOUP.flatten(firstPageRoot), "div", "b-tree-root");
        List<Node> vn2 = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-tree-root");

        if (vn1.size() != 1 || vn2.size() != 1)
            throw new Exception("Unable to locate comment tree root");

        Node tr1 = vn1.get(0);
        Node tr2 = vn2.get(0);

        List<Node> children = JSOUP.getChildren(tr2);
        for (Node n : children)
        {
            JSOUP.removeNode(n);
            JSOUP.addChild(tr1, n);
        }
    }

    static protected boolean isLoginLimitExceeded(String html) throws Exception
    {
        Node root = JSOUP.parseHtml(html);

        for (Node n : JSOUP.flatten(root))
        {
            if (n instanceof TextNode)
            {
                TextNode tn = (TextNode) n;
                String text = tn.text();
                text = text.replaceAll("\\s+", " ").trim();
                if (text.contains("Login limit exceeded"))
                    return true;
            }
        }

        return false;
    }

    protected boolean likelyBlockedPage(String html) throws Exception
    {
        if (Config.ProxyBlockingMessage == null || Config.ProxyBlockingMessage.length() == 0)
            return false;
        if (html == null)
            html = getPageSource();
        html = html.replaceAll("\\s+", " ");
        return html.contains(Config.ProxyBlockingMessage);
    }

    protected boolean isBlockedPage() throws Exception
    {
        return isBlockedPage(null);
    }

    protected boolean isBlockedPage(String html) throws Exception
    {
        if (Config.ProxyBlockingMessage == null || Config.ProxyBlockingMessage.length() == 0)
            return false;

        if (html == null)
            html = getPageSource();

        Node root = JSOUP.parseHtml(html);

        for (Node n : JSOUP.flatten(root))
        {
            if (n instanceof TextNode)
            {
                TextNode tn = (TextNode) n;
                String text = tn.text();
                text = text.replaceAll("\\s+", " ");
                if (text.contains(Config.ProxyBlockingMessage))
                {
                    // Main.err(">>> " + "[" + Config.User + "] " + rurl + " was blocked");
                    return true;
                }
            }
        }

        return false;
    }

    protected boolean pageHasNoComments(String html) throws Exception
    {
        // check if comments are disabled
        if (html.contains("b-xylem-nocomment"))
        {
            if (pageRoot == null)
                pageRoot = JSOUP.parseHtml(html);
            List<Node> vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-xylem-nocomment");
            if (vel.size() != 0)
                return true;
        }

        // check if tree root is empty
        if (!html.contains("b-leaf-actions-"))
        {
            if (pageRoot == null)
                pageRoot = JSOUP.parseHtml(html);
            List<Node> vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-tree-root");
            if (vel.size() != 1)
            {
                Main.saveDebugPage("badpage-unable-find-root-node-for-comments.html", html);
                throw new MissingCommentsTreeRootException("Unable to find root node for comments");
            }
            vel = JSOUP.flattenChildren(vel.get(0));
            for (Node n : vel)
            {
                if (n instanceof Element)
                    return false;
            }
            return true;
        }

        return false;
    }

    protected boolean pageHasNoComments() throws Exception
    {
        List<Node> vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-tree-root");

        if (vel.size() == 0)
        {
            if (offline)
                return true;
            vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-xylem-nocomment");
            if (vel.size() != 0)
                return true;
            throw new Exception("Unable to find root node for comments");
        }
        else if (vel.size() != 1)
        {
            throw new Exception("Unable to find root node for comments");
        }

        vel = JSOUP.flattenChildren(vel.get(0));
        for (Node n : vel)
        {
            if (n instanceof Element)
                return false;
        }

        return true;
    }

    protected boolean isLoadingPage(String html) throws Exception
    {
        // another indicator is that div.b-tree-root has style="min-height: 5123px;"

        if (html.contains("b-grove-loading"))
        {
            if (pageRoot == null)
                pageRoot = JSOUP.parseHtml(html);
            List<Node> vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-grove-loading");
            if (vel.size() != 0)
                return true;
        }

        return false;
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

    protected boolean isRepost() throws Exception
    {
        if (!pageHasNoComments())
            return false;

        List<Node> vel = JSOUP.findElementsWithClass(pageRoot, "article", "b-singlepost-body");
        if (vel.size() > 1)
            vel = filterTrueSinglepostBodies(vel);
        if (vel.size() != 1)
            throw new Exception("Unable to find record body");
        Node rbody = vel.get(0);

        List<Node> children = JSOUP.getChildren(rbody);
        vel = JSOUP.findElementsWithClass(children, "div", "repost");
        if (vel.size() != 1)
            return false;
        children.remove(vel.get(0));

        int textIndex = 0;
        int linkIndex = 0;
        int spanIndex = 0;
        String s;
        String repostTitle = null;

        for (Node n : children)
        {
            TextNode tn;

            @SuppressWarnings("unused")
            Element el;

            if (n instanceof TextNode)
            {
                tn = (TextNode) n;
                String text = tn.text().replaceAll("\\s+", " ").trim();
                if (text.length() == 0)
                    continue;

                // out("  Text: [" + text + "]");

                if (textIndex == 0 && text.equals("Originally posted by"))
                {
                    // match
                }
                else if (textIndex == 1 && text.equals("at"))
                {
                    // match
                }
                else
                {
                    return false;
                }

                textIndex++;
            }
            else if (n instanceof Element)
            {
                String tag = JSOUP.nodeName(n);
                if (tag.equals("br"))
                    continue;

                // out("  Element: " + tag);

                if (linkIndex == 0 && tag.equals("a"))
                {
                    linkIndex++;
                    s = JSOUP.getAttribute(n, "target");
                    if (s == null || !s.equals("_self"))
                        return false;
                    repostTitle = getTextUnder(n);
                }
                else if (spanIndex == 0 && tag.equals("span"))
                {
                    spanIndex++;
                    s = JSOUP.getAttribute(n, "class");
                    if (s == null)
                        return false;
                    if (!s.equals("ljuser") && !s.startsWith("ljuser "))
                        return false;
                }
                else
                {
                    return false;
                }
            }
        }

        String postTitle = null;
        vel = JSOUP.findElementsWithClass(pageRoot, null, "b-singlepost-title");
        if (vel.size() > 1)
            vel = filterTrueSinglepostTitles(vel);
        if (vel.size() == 0)
        {
            // leave postTitle null
        }
        else if (vel.size() == 1)
        {
            postTitle = getTextUnder(vel.get(0));
        }
        else
        {
            throw new Exception("Unable to find page title");
        }

        if (postTitle != null)
            postTitle = postTitle.replaceAll("\\s+", " ").trim();

        if (repostTitle != null)
            repostTitle = repostTitle.replaceAll("\\s+", " ").trim();

        if (!isEmpty(postTitle) && !same(postTitle, repostTitle))
            return false;

        return true;
    }

    private List<Node> filterTrueSinglepostBodies(List<Node> vel) throws Exception
    {
        List<Node> res = new ArrayList<>();

        for (Node n : vel)
        {
            if (isTrueSinglepostBody(n))
                res.add(n);
        }

        return res;
    }

    private boolean isTrueSinglepostBody(Node n) throws Exception
    {
        if (!matchTagClass(n, "article", "b-singlepost-body"))
            return false;

        n = JSOUP.getParent(n);
        if (n == null || !matchTagClass(n, "div", "b-singlepost-bodywrapper"))
            return false;

        n = JSOUP.getParent(n);
        if (n == null || !matchTagClass(n, "div", "b-singlepost-wrapper"))
            return false;

        n = JSOUP.getParent(n);
        if (n == null || !matchTagClass(n, "article", "b-singlepost"))
            return false;

        return true;
    }

    private List<Node> filterTrueSinglepostTitles(List<Node> vel) throws Exception
    {
        List<Node> res = new ArrayList<>();

        for (Node n : vel)
        {
            if (isTrueSinglepostTitle(n))
                res.add(n);
        }

        return res;
    }

    private boolean isTrueSinglepostTitle(Node n) throws Exception
    {
        if (!matchTagClass(n, null, "b-singlepost-title"))
            return false;

        n = JSOUP.getParent(n);
        if (n == null || !matchTagClass(n, "div", "b-singlepost-wrapper"))
            return false;

        return true;
    }

    protected boolean matchTagClass(Node n, String name, String cls) throws Exception
    {
        return (name == null || JSOUP.nodeName(n).equalsIgnoreCase(name)) &&
                (cls == null || JSOUP.classContains(JSOUP.getAttribute(n, "class"), cls));
    }

    protected boolean isEmpty(String s) throws Exception
    {
        return s == null || s.length() == 0;
    }

    protected boolean same(String s1, String s2) throws Exception
    {
        if (s1 == null && s2 == null)
            return true;
        if (s1 == null || s2 == null)
            return false;
        return s1.equals(s2);
    }

    protected String getTextUnder(Node n) throws Exception
    {
        String text = null;

        for (Node xn : JSOUP.getChildren(n))
        {
            if (xn instanceof TextNode)
            {
                TextNode tn = (TextNode) xn;
                if (text == null)
                    text = tn.text();
                else
                    text += tn.text();
            }
        }

        return text;
    }

    static protected String elementClassContains(String cname) throws Exception
    {
        return "contains(concat(' ', normalize-space(@class), ' '), ' " + cname + " ')";
    }

    static protected String xpathTagClassName(String tag, String cl, String name) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb))
        {
            formatter.format("//%1$s[@class='%2$s'][@name='%3$s']", tag, cl, name);
            return sb.toString();
        }
    }

    static protected String xpathTagClassContainsName(String tag, String cl, String name) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb))
        {
            formatter.format("//%1$s[contains(@class, '%2$s')][@name='%3$s']", tag, cl, name);
            return sb.toString();
        }
    }

    static protected String xpathTagName(String tag, String name) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb))
        {
            formatter.format("//%1$s[@name='%2$s']", tag, name);
            return sb.toString();
        }
    }

    static protected String xpathTagId(String tag, String id) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb))
        {
            formatter.format("//%1$s[@id='%2$s']", tag, id);
            return sb.toString();
        }
    }

    abstract protected String getPageSource() throws Exception;

    public void downloadExternalLinks(Node root, String linksDir) throws Exception
    {
        if (linksDir == null || Config.DownloadFileTypes == null || Config.DownloadFileTypes.size() == 0)
            return;
        
        applyProtocolAndBaseDefaults(root);

        unwrapImgPrx(root, "img", "src");
        unwrapImgPrx(root, "img", "original-src");
        unwrapImgPrx(root, "a", "href");
        unwrapImgPrx(root, "a", "original-href");

        downloadExternalLinks(root, linksDir, "a", "href", true);
        downloadExternalLinks(root, linksDir, "img", "src", false);
    }

    private void downloadExternalLinks(Node root, String linksDir, String tag, String attr, boolean filterDownloadFileTypes)
            throws Exception
    {
        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);

            if (LinkDownloader.shouldDownload(href, filterDownloadFileTypes))
            {
                String referer = LJUtil.recordPageURL(rurl);
                String newref = LinkDownloader.download(linksDir, href, referer, LinkDownloader.LINK_REFERENCE_PREFIX_PAGES);
                if (newref != null)
                {
                    JSOUP.updateAttribute(n, attr, newref);
                    JSOUP.setAttribute(n, "original-" + attr, href);
                }
            }
        }
    }

    private void unwrapImgPrx(Node root, String tag, String attr) throws Exception
    {
        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);

            if (href != null && Web.isLivejournalImgPrx(href))
            {
                String newref = Web.getRedirectLocation(href, null);
                if (newref != null)
                    JSOUP.updateAttribute(n, attr, newref);
            }
        }
    }
    
    private boolean applyProtocolAndBaseDefaults(Node root) throws Exception
    {
        boolean applied = false;
        
        /* use of | rather than || prevents evaluation short-cut */
        applied |= applyProtocolAndBaseDefaults(root, "link", "href");
        applied |= applyProtocolAndBaseDefaults(root, "a", "href");
        applied |= applyProtocolAndBaseDefaults(root, "iframe", "src");
        applied |= applyProtocolAndBaseDefaults(root, "img", "src");
        applied |= applyProtocolAndBaseDefaults(root, "video", "src");
        applied |= applyProtocolAndBaseDefaults(root, "audio", "src");
        applied |= applyProtocolAndBaseDefaults(root, "source", "src");
        applied |= applyProtocolAndBaseDefaults(root, "embed", "src");
        applied |= applyProtocolAndBaseDefaults(root, "track", "src");
        applied |= applyProtocolAndBaseDefaults(root, "object", "data");        

        return applied;
    }

    private boolean applyProtocolAndBaseDefaults(Node root, String tag, String attr) throws Exception
    {
        boolean applied = false;

        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);
            
            if (href != null)
            {
                String newref = null;
                
                if (href.startsWith("//"))
                {
                    newref = "https:" + href;
                    Util.noop();
                }
                else if (href.startsWith("/"))
                {
                    newref = String.format("https://%s.livejournal.com%s", Config.MangledUser, href);
                }
                
                if (newref != null)
                {
                    JSOUP.updateAttribute(n, attr, newref);
                    applied = true;
                }
            }
        }

        return applied;
    }
}
