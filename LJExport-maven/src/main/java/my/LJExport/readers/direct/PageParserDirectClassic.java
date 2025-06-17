package my.LJExport.readers.direct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.Comment;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.Util;
import my.LJExport.xml.JSOUP;

public class PageParserDirectClassic extends PageParserDirectBase
{
    public PageParserDirectClassic(PageContentSource pageContentSource)
    {
        super(pageContentSource);
    }

    public PageParserDirectClassic(PageParserDirectBase classic)
    {
        super(classic);
    }

    public static class MissingCommentsTreeRootException extends Exception
    {
        private static final long serialVersionUID = 1L;

        MissingCommentsTreeRootException(String s)
        {
            super(s);
        }
    }

    protected boolean offline = false;

    @Override
    public void removeJunk(int flags) throws Exception
    {
        /*
         * Record is a set of nested tables, with relevant content located under the <article> tag.
         * Traverse the tree and delete all tables (<table>, <tr> and <td> tags) that do not
         * have <article> as their eventual child.
         */
        removeNonArticleParents();

        if (0 != (flags & COUNT_PAGES))
        {
            // find out if there are multiple pages with comments
            npages = numberOfCommentPages();
        }

        if (0 != (flags & CHECK_HAS_COMMENTS))
        {
            // count comments
            hasComments = hasComments();
        }

        /*
         * Empty out the comments section
         * under <article> find <div id="comments"> and empty it
         */
        Element commentsSection = findCommentsSection(pageRoot, false);
        if (commentsSection != null)
            JSOUP.removeNodes(JSOUP.getChildren(commentsSection));

        /*
         * Remove known sections that contain no essential record-related information.
         */
        JSOUP.removeElements(pageRoot, "div", "id", "lj_controlstrip_new");
        JSOUP.removeElementsWithClass(pageRoot, "iframe", "b-watering-commentator");
        JSOUP.removeElementsWithClass(pageRoot, "div", "b-popup-outer");
        JSOUP.removeElementsWithClass(pageRoot, "div", "threeposts__inner");
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
        JSOUP.removeElementsWithClass(pageRoot, "ul", "b-singlepos-tools");

        for (Node n : JSOUP.findElementsWithClass(pageRoot, "div", "entry-unrelated"))
        {
            String id = JSOUP.getAttribute(n, "id");
            if (id != null && id.equals("comments"))
                continue;
            JSOUP.removeElement(pageRoot, n);
        }

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

            vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "noscript");
            JSOUP.removeElements(pageRoot, vnodes);
        }
    }

    @Override
    public Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception
    {
        Element commentsSection = null;

        List<Node> articles = JSOUP.findElements(pageRootCurrent, "article");
        for (Node n : articles)
        {
            List<Node> comms = JSOUP.findElements(JSOUP.flatten(n), "div", "id", "comments");
            for (Node cn : comms)
            {
                if (commentsSection == null)
                    commentsSection = (Element) cn;
                else if (commentsSection != cn)
                    throw new Exception("Multiple comment sections");
            }
        }

        List<Node> alones = this.findStandaloneCommentsSections(pageRootCurrent);
        for (Node n : alones)
        {
            List<Node> comms = JSOUP.findElements(JSOUP.flatten(n), "div", "id", "comments");
            for (Node cn : comms)
            {
                if (commentsSection == null)
                    commentsSection = (Element) cn;
                else if (commentsSection != cn)
                    throw new Exception("Multiple comment sections");
            }
        }

        if (required && commentsSection == null)
            throw new Exception("Page has no comments section");

        return commentsSection;
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

    protected boolean hasComments() throws Exception
    {
        Boolean has = null;

        Element commentsSection = findCommentsSection(pageRoot, true);
        has = hasComments(has, commentsSection);

        if (has == null)
        {
            List<Node> articles = JSOUP.findElementsWithAllClasses(pageRoot, "article", Util.setOf("b-singlepost", "hentry"));
            for (Node article : articles)
                has = hasComments(has, article);
        }

        if (has == null)
            throw new Exception("Unable to determine if page has comments");

        return has;
    }

    private Boolean hasComments(Boolean has, Node under) throws Exception
    {
        if (pageSource.contains("b-xylem-nocomment"))
        {
            List<Node> vel = JSOUP.findElementsWithClass(under, "div", "b-xylem-nocomment");
            for (Node n : vel)
            {
                String s = JSOUP.nodeText(n);
                s = Util.despace(s);
                if (s.equals("Comments for this post were disabled by the author")
                        || s.equals("Comments for this post were locked by the author"))
                {
                    has = hasComments(has, Boolean.FALSE);
                }
            }
        }

        if (pageSource.contains("b-xylem-cell-amount"))
        {
            List<Node> vel = JSOUP.findElementsWithClass(under, "li", "b-xylem-cell-amount");
            for (Node n : vel)
            {
                String s = JSOUP.nodeText(n);
                s = Util.despace(s);
                if (s.equals("0 comments") ||
                        s.equals("Comments for this post were disabled by the author") ||
                        s.equals("Comments for this post were locked by the author"))
                {
                    has = hasComments(has, Boolean.FALSE);
                }
                else if (s.equals("1 comment"))
                {
                    has = hasComments(has, Boolean.TRUE);
                }
                else
                {
                    String sa[] = s.split(" ");
                    if (sa.length == 2 && sa[1].equals("comments") && isPositiveNumber(sa[0]))
                        has = hasComments(has, Boolean.TRUE);
                }
            }
        }

        return has;
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

    @Override
    public void injectComments(Element commentsSection, CommentsTree commentTree) throws Exception
    {
        List<Comment> list = commentTree.flatten();
        for (Comment c : list)
            injectComment(commentsSection, c);
    }

    private void injectComment(Element commentsSection, Comment c) throws Exception
    {
        String dname = c.dname;
        if (dname == null)
            dname = c.uname;

        String profile_url = c.profile_url;
        if (profile_url == null)
            profile_url = c.commenter_journal_base + "profile/";

        String journal_url = c.journal_url;
        if (journal_url == null)
        {
            journal_url = c.commenter_journal_base;
            if (Util.lastChar(journal_url) == '/')
                journal_url = Util.stripLastChar(journal_url, '/');
        }

        String userhead_url = c.userhead_url;
        if (userhead_url == null)
            userhead_url = Comment.DEFAULT_USERHEAD_URL;

        Map<String, String> vars = new HashMap<>();
        vars.put("username", c.uname);
        vars.put("dname", dname);
        vars.put("profile_url", profile_url);
        vars.put("journal_url", journal_url);
        vars.put("userhead_url", userhead_url);
        vars.put("thread", c.thread);
        vars.put("commenter_journal_base", c.commenter_journal_base);
        vars.put("article", c.article);
        vars.put("ctime", c.ctime);
        vars.put("userpic", c.userpic);
        vars.put("offset_px", "" + 30 * (c.level - 1));
        vars.put("thread_url", c.thread_url);
        vars.put("subject", c.subject);

        String record_url = "http://" + Config.MangledUser + "." + Config.Site + "/" + rurl;
        vars.put("record_url", record_url);

        final String tdir = "templates/direct-classic/";
        String tname = null;

        if (c.isDeleted())
        {
            tname = tdir + "deleted-comment.txt";
            vars.put("deleted-comment-status", "Deleted comment");
        }
        else if (c.isScreened() && c.loaded != Boolean.TRUE)
        {
            tname = tdir + "deleted-comment.txt";
            vars.put("deleted-comment-status", "Screened comment");
        }
        else if (c.isSuspended() && c.article == null)
        {
            tname = tdir + "deleted-comment.txt";
            vars.put("deleted-comment-status", "Suspended comment");
        }
        else if (c.uname == null || c.uname.equals(Comment.DEFAULT_UNAME))
        {
            if (c.subject != null && c.subject.length() != 0)
                tname = tdir + "anon-comment-with-subject.txt";
            else
                tname = tdir + "anon-comment-without-subject.txt";
        }
        else
        {
            if (c.subject != null && c.subject.length() != 0)
                tname = tdir + "user-comment-with-subject.txt";
            else
                tname = tdir + "user-comment-without-subject.txt";
        }

        String template = Util.loadResource(tname);
        String html = expandVars(template, vars);

        html = html.replace("\r\n", "\n");

        if (Util.lastChar(html) == '\n')
            html = Util.stripLastChar(html, '\n');

        html = Arrays.stream(html.split("\n"))
                .map(String::trim)
                .collect(Collectors.joining("\n"));

        if (Util.lastChar(html) == '\n')
            html = Util.stripLastChar(html, '\n');

        html = html.replace("\n", "");

        injectHtml(commentsSection, html, record_url);
    }
}
