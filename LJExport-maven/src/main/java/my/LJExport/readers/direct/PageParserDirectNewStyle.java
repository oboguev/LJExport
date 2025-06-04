package my.LJExport.readers.direct;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.Comment;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.Util;
import my.LJExport.xml.JSOUP;

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
            throw new RuntimeException("Not implemented");

        if (0 != (flags & REMOVE_SCRIPTS))
        {
            Vector<Node> vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "script");
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
        
        if (href.contains("/"))
            href = href.substring(href.lastIndexOf("/") + 1);
        
        if (!href.startsWith(rurl))
            return maxPage;
        href = href.substring(rurl.length());
        
        if (href.equals(""))
            return maxPage == null ? 1 : Math.max(1, maxPage);
        
        final String key = "?pages=";
        if (href.startsWith(key))
        {
            href = href.substring(key.length());
            if (isPositiveNumber(href))
            {
                int page = Integer.parseInt(href);
                return maxPage == null ? page : Math.max(1, page);
            }
        }

        return maxPage;
    }

    protected boolean hasComments() throws Exception
    {
        Boolean has = null;

        Element commentsSection = findCommentsSection(pageRoot, true);
        has = hasComments(has, commentsSection);
        
        if (has == null)
        {
            Vector<Node> articles = JSOUP.findElementsWithAllClasses(pageRoot, "article", Util.setOf("aentry"));
            for (Node article : articles)
                has = hasComments(has, article);
        }

        if (has == null)
            throw new Exception("Unable to determine if page has comments");

        return has;
    }
    
    private Boolean hasComments(Boolean has, Node under) throws Exception
    {
        Vector<Node> va = JSOUP.findElementsWithClass(under, "a", "mdspost-comments-controls__count");
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

    @Override
    public Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception
    {
        Element commentsSection = null;

        Vector<Node> articles = JSOUP.findElements(pageRootCurrent, "article");
        for (Node n : articles)
        {
            // can also use div id=comments (which is located inside div claas=acomments)
            // but additional cleanup will be needed to remove extra junk
            Vector<Node> comms = JSOUP.findElementsWithClass(JSOUP.flatten(n), "div", "acomments");
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

        String record_url = "http://" + Config.MangledUser + "." + Config.Site + "/" + rurl;
        vars.put("record_url", record_url);

        final String tdir = "templates/direct-new-style/";
        String tname = null;
        
        if (c.isDeleted())
        {
            throw new Exception("No template for deleted comment " + c.thread_url);
            // tname = tdir + "deleted-comment.txt";
            // vars.put("deleted-comment-status", "Deleted comment");
        }
        else if (c.isScreened() && c.loaded != Boolean.TRUE)
        {
            throw new Exception("No template for screened comment " + c.thread_url);
            // tname = tdir + "deleted-comment.txt";
            // vars.put("deleted-comment-status", "Screened comment");
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
                throw new Exception("No template for anonymous comment without subject " + c.thread_url);
                // tname = tdir + "anon-comment-without-subject.txt";
            }
        }
        else
        {
            if (c.subject != null && c.subject.length() != 0)
            {
                throw new Exception("No template for non-anonymous comment with subject " + c.thread_url);
                // tname = tdir + "user-comment-with-subject.txt";
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
}
