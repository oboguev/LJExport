package my.LJExport.readers.direct;

import java.util.Vector;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.Util;
import my.LJExport.xml.JSOUP;

// example: https://sergeytsvetkov.livejournal.com/2721896.html

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
        // ###
        throw new RuntimeException("Not implemented");
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
        // ###
        throw new RuntimeException("Not implemented");
    }
}
