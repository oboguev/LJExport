package my.LJExport.readers.direct;

import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.Comment;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.LinkDownloader;
import my.LJExport.runtime.Util;
import my.LJExport.xml.JSOUP;

public class PageParserDirect
{
    public PageParserDirect(PageContentSource pageContentSource)
    {
        this.pageContentSource = pageContentSource;
    }

    public static class MissingCommentsTreeRootException extends Exception
    {
        private static final long serialVersionUID = 1L;

        MissingCommentsTreeRootException(String s)
        {
            super(s);
        }
    }

    private final PageContentSource pageContentSource;

    public final static int COUNT_PAGES = (1 << 0);
    public final static int REMOVE_MAIN_TEXT = (1 << 2);
    public final static int REMOVE_SCRIPTS = (1 << 3);

    protected boolean offline = false;

    public int npages = -1;

    public Node pageRoot;
    public String pageSource;

    public String rurl;
    public String rid;

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

        /*
         * Empty out the comments section
         * under <article> find <div id="comments"> and empty it
         */
        Element commentsSection = findCommentsSection(pageRoot);
        if (commentsSection != null)
            JSOUP.removeNodes(JSOUP.getChildren(commentsSection));

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
            Vector<Node> vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "script");
            JSOUP.removeElements(pageRoot, vnodes);

            vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "noscript");
            JSOUP.removeElements(pageRoot, vnodes);
        }
    }

    protected Element findCommentsSection(Node pageRootCurrent) throws Exception
    {
        Element commentsSection = null;

        Vector<Node> articles = JSOUP.findElements(pageRootCurrent, "article");
        for (Node n : articles)
        {
            Vector<Node> comms = JSOUP.findElements(JSOUP.flatten(n), "div", "id", "comments");
            for (Node cn : comms)
            {
                if (commentsSection == null)
                    commentsSection = (Element) cn;
                else if (commentsSection != cn)
                    throw new Exception("Multiple comment sections");
            }
        }

        return commentsSection;
    }

    private void removeJunk_1() throws Exception
    {
        // find article tags
        Vector<Node> articles = JSOUP.findElements(pageRoot, "article");

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
        Vector<Node> delvec = new Vector<Node>();
        rj1_enum_deletes(delvec, keepSet, new HashSet<Node>(articles), pageRoot);

        // delete these elements
        if (delvec.size() != 0)
            JSOUP.removeElements(pageRoot, delvec);
    }

    private void rj1_enum_deletes(Vector<Node> delvec, Set<Node> keepSet, Set<Node> stopSet, Node n) throws Exception
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
            if (!Util.isJournalUrl(href, sb))
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
            Vector<Node> vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-xylem-nocomment");
            if (vel.size() != 0)
                return true;
        }

        // check if tree root is empty
        if (!html.contains("b-leaf-actions-"))
        {
            if (pageRoot == null)
                pageRoot = JSOUP.parseHtml(html);
            Vector<Node> vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-tree-root");
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
        Vector<Node> vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-tree-root");

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
            Vector<Node> vel = JSOUP.findElementsWithClass(JSOUP.flatten(pageRoot), "div", "b-grove-loading");
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
            Vector<Node> vel = JSOUP.findElements(JSOUP.flatten(pageRoot), "body");
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

        Vector<Node> vel = JSOUP.findElementsWithClass(pageRoot, "article", "b-singlepost-body");
        if (vel.size() > 1)
            vel = filterTrueSinglepostBodies(vel);
        if (vel.size() != 1)
            throw new Exception("Unable to find record body");
        Node rbody = vel.get(0);

        Vector<Node> children = JSOUP.getChildren(rbody);
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

    private Vector<Node> filterTrueSinglepostBodies(Vector<Node> vel) throws Exception
    {
        Vector<Node> res = new Vector<Node>();

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

    private Vector<Node> filterTrueSinglepostTitles(Vector<Node> vel) throws Exception
    {
        Vector<Node> res = new Vector<Node>();

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

    private String getPageSource() throws Exception
    {
        return pageContentSource.getPageSource();
    }

    /*static*/ public void downloadExternalLinks(Node root, String linksDir) throws Exception
    {
        if (linksDir == null || Config.DownloadFileTypes == null || Config.DownloadFileTypes.size() == 0)
            return;
        downloadExternalLinks(root, linksDir, "a", "href");
        downloadExternalLinks(root, linksDir, "img", "src");
    }

    /*static*/ private void downloadExternalLinks(Node root, String linksDir, String tag, String attr) throws Exception
    {
        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);

            if (LinkDownloader.shouldDownload(href))
            {
                String newref = LinkDownloader.download(linksDir, href);
                if (newref != null)
                    JSOUP.updateAttribute(n, attr, newref);
            }
        }
    }

    public void injectComments(Element commentsSection, CommentsTree commentTree) throws Exception
    {
        List<Comment> list = commentTree.flatten();
        for (Comment c : list)
            injectComment(commentsSection, c);

        // ### flatten, then for... do injectComment

        // c.isDeleted()

        // https://pioneer-lj.livejournal.com/1949522.html?thread=131141202#t131141202

        // username = pioneer_lj  (fron c.uname)
        // thread = 131141202 
        // commenter_journal_base = "https://sergay33.livejournal.com/"
        // article = ....
        // ctime = "November 30 2011, 03:00:30"
        // userpic = "https://l-userpic.livejournal.com/109580921/12651460"
        // offset_px = 0, 30, 60 etc. (level - 1) x 30
        // thread_url ="https://krylov.livejournal.com/2352931.html?thread=105150243#t105150243" 
        // record_url = https://pioneer-lj.livejournal.com/1949522.html
        // 
        // subject  ""  
        // 
        // what if anonymous user with/without subject?
    }

    private void injectComment(Element commentsSection, Comment c) throws Exception
    {
        Map<String, String> vars = new HashMap<>();
        vars.put("username", c.uname);
        vars.put("thread ", c.thread);
        vars.put("commenter_journal_base", c.commenter_journal_base);
        vars.put("article ", c.article);
        vars.put("ctime", c.ctime);
        vars.put("userpic", c.userpic);
        vars.put("offset_px", "" + 30 * (c.level - 1));
        vars.put("thread_url", c.thread_url);
        vars.put("subject ", c.subject);

        String record_url = "http://" + Config.MangledUser + "." + Config.Site + "/" + rurl;
        vars.put("record_url", record_url);
        
        String tname = null;
        
        if (c.isDeleted())
        {
            tname = "templates/direct/deleted-comment.txt";
        }
        else if (c.uname == null || c.uname.equals(Comment.DEFAULT_UNAME))
        {
            if (c.subject != null && c.subject.length() != 0) 
                tname = "templates/direct/anon-comment-with-subject.txt";
            else
                tname = "templates/direct/anon-comment-without-subject.txt";
        }
        else
        {
            if (c.subject != null && c.subject.length() != 0) 
                tname = "templates/direct/user-comment-with-subject.txt";
            else
                tname = "templates/direct/user-comment-without-subject.txt";
        }
        
        // ### make two anon templates
        
        String template = Util.loadResource(tname);
        String html = expandVars(template, vars);
        injectHtml(commentsSection, html);
    }

    private void injectHtml(Element commentsSection, String html)
    {
        List<Node> nodes = org.jsoup.parser.Parser.parseFragment(html, commentsSection, null);

        for (Node node : nodes)
        {
            commentsSection.appendChild(node);
        }
    }
    
    private String expandVars(String template, Map<String, String> vars)
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
}
