package my.LJExport.readers.direct;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import my.LJExport.Config;
import my.LJExport.html.JSOUP;
import my.LJExport.readers.Comment;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.HasNoComments;
import my.LJExport.runtime.LJUtil;
import my.LJExport.runtime.Util;

// examples: https://sergeytsvetkov.livejournal.com/2721896.html
//           https://genby.livejournal.com/982965.html
//           https://oboguev-2.livejournal.com/399.html

/**
 * Parser for LJ new-style pages
 */
public class PageParserDirectNewStyle extends PageParserDirectBase
{
    public PageParserDirectNewStyle(PageContentSource pageContentSource)
    {
        super(pageContentSource);
    }

    public PageParserDirectNewStyle(PageParserDirectBase classic)
    {
        super(classic);
    }

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

        if (0 != (flags & EXTRACT_COMMENTS_JSON))
        {
            if (0 == (flags & CHECK_HAS_COMMENTS))
                throw new Exception("EXTRACT_COMMENTS_JSON requires CHECK_HAS_COMMENTS");

            if (hasComments)
            {
                commentsJson = extractCommentsJson();
            }
            else
            {
                commentsJson = null;
            }
        }
        
        /*
         * Empty out the comments section
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
        JSOUP.removeElementsWithClass(pageRoot, "div", "appwidget");
        JSOUP.removeElementsWithClass(pageRoot, "div", "aentry-post-tools");
        JSOUP.removeElementsWithClass(pageRoot, "div", "social-panel");
        JSOUP.removeElementsWithClass(pageRoot, "div", "aentry-post__socials");
        JSOUP.removeElementsWithClass(pageRoot, "div", "warning-background");
        JSOUP.removeElementsWithClass(pageRoot, "p", "b-bubble-alert");
        JSOUP.removeElementsWithClass(pageRoot, "ul", "b-singlepos-tools");
        JSOUP.removeElementsWithClass(pageRoot, "div", "s-switchv3");
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "header", "role", "banner"));

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
            throw new RuntimeException("Not implemented");

        if (0 != (flags & REMOVE_SCRIPTS))
        {
            List<Node> vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "script");
            JSOUP.removeElements(pageRoot, vnodes);

            vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "noscript");
            JSOUP.removeElements(pageRoot, vnodes);
        }
    }

    private int numberOfCommentPages() throws Exception
    {
        Integer maxPage = null;

        for (Node pager : JSOUP.findElementsWithClass(pageRoot, "ul", "b-pager-pages"))
        {
            for (Node pageSelector : JSOUP.findElementsWithClass(pager, "li", "b-pager-page"))
            {
                for (Node a : JSOUP.findElements(pageSelector, "a"))
                {
                    maxPage = numberOfCommentPages(maxPage, JSOUP.getAttribute(a, "href"));
                }
            }
        }

        if (maxPage == null)
            return 1;
        else
            return maxPage;
    }

    private Integer numberOfCommentPages(Integer maxPage, String href) throws Exception
    {
        if (href == null)
            return maxPage;

        href = href.replace("&amp;", "&");
        URL url = new URL("http://localhost");
        url = new URL(url, href);

        List<NameValuePair> params = URLEncodedUtils.parse(url.toURI(), StandardCharsets.UTF_8.toString());
        for (NameValuePair pair : params)
        {
            if (pair.getName().equals("page"))
            {
                String val = pair.getValue();
                if (isPositiveNumber(val))
                {
                    int page = Integer.parseInt(val);
                    return maxPage == null ? page : Math.max(1, page);
                }
            }
        }

        return maxPage;
    }

    protected boolean hasComments() throws Exception
    {
        if (HasNoComments.rurlHasNoComments(rurl))
            return false;
        
        Boolean has = null;

        Element commentsSection = findCommentsSection(pageRoot, true);
        has = hasComments(has, commentsSection);

        if (has == null)
        {
            List<Node> articles = JSOUP.findElementsWithAllClasses(pageRoot, "article", Util.setOf("aentry"));
            for (Node article : articles)
                has = hasComments(has, article);
        }

        if (has == null && isCommentsLocked(commentsSection))
            has = false;

        if (has == null)
            throw new Exception("Unable to determine if page has comments");

        return has;
    }

    private Boolean hasComments(Boolean has, Node under) throws Exception
    {
        List<Node> va = JSOUP.findElementsWithClass(under, "a", "mdspost-comments-controls__count");
        for (Node na : va)
        {
            String s = JSOUP.nodeText(na);
            s = Util.despace(s);
            if (s.equals("0 comments"))
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

        return has;
    }

    private boolean isCommentsLocked(Element commentsSection) throws Exception
    {
        for (Node n : JSOUP.findElementsWithClass(JSOUP.flatten(commentsSection), "p", "b-bubble-alert"))
        {
            String text = JSOUP.nodeText(n);
            if (text != null && Util.despace(text).equals("Comments for this post were locked by the author"))
                return true;
        }

        return false;
    }

    @Override
    public Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception
    {
        Element commentsSection = null;

        List<Node> articles = JSOUP.findElements(pageRootCurrent, "article");
        for (Node n : articles)
        {
            // can also use div id=comments (which is located inside div claas=acomments)
            // but additional cleanup will be needed to remove extra junk
            List<Node> comms = JSOUP.findElementsWithClass(JSOUP.flatten(n), "div", "acomments");
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
        vars.put("level", "" + c.level);
        vars.put("thread_url", c.thread_url);
        vars.put("subject", c.subject);

        String record_url = LJUtil.recordPageURL(rurl);
        vars.put("record_url", record_url);

        final String tdir = "templates/direct-new-style/";
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
        else if (c.isSpammed() && c.article == null)
        {
            tname = tdir + "deleted-comment.txt";
            vars.put("deleted-comment-status", "Spammed comment");
        }
        else if (c.uname == null || c.uname.equals(Comment.DEFAULT_UNAME))
        {
            if (c.subject != null && c.subject.length() != 0)
            {
                throw new Exception("No template for anonymous comment with subject " + c.thread_url);
                // tname = tdir + "anon-comment-with-subject.txt";
            }
            else
            {
                tname = tdir + "anon-comment-without-subject.txt";
            }

            if (Config.True || vars.get("userpic") == null || vars.get("userpic").trim().length() == 0)
                vars.put("userpic", Comment.ANONYMOUS_USER_USERPIC);
        }
        else
        {
            if (c.subject != null && c.subject.length() != 0)
            {
                tname = tdir + "user-comment-with-subject.txt";
            }
            else
            {
                tname = tdir + "user-comment-without-subject.txt";
            }
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

    @Override
    public Element findMainArticle() throws Exception
    {
        List<Node> articles = JSOUP.findElementsWithAllClasses(pageRoot, "article", Util.setOf("aentry"));
        if (articles.size() == 0)
        {
            throw new Exception("Unable to locate main article");
        }
        else if (articles.size() != 1)
        {
            throw new Exception("Unexpected multiple main articles");
        }
        else
        {
            return (Element) articles.get(0);
        }
    }
}
