package my.LJExport.readers.direct;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.LJUtil;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;

public class PageParserDirectRossiaOrg extends PageParserDirectBase
{
    public PageParserDirectRossiaOrg(PageContentSource pageContentSource)
    {
        super(pageContentSource);
    }

    public PageParserDirectRossiaOrg(PageParserDirectBase classic)
    {
        super(classic);
    }

    @Override
    public void removeJunk(int flags) throws Exception
    {
        Element anchor = locateArticleAnchor();

        /*
         * Delete all table rows/cells and tables except directly containing @anchor
         * and also contained within the the same "td" cell as @anchor 
         */
        List<Node> preserve = new ArrayList<>();

        for (Node p = anchor;; p = p.parentNode())
        {
            if (p == null)
                throw new Exception("missing BODY element");
            preserve.add(p);
            if (p instanceof Element && JSOUP.asElement(p).tagName().equalsIgnoreCase("body"))
                break;
        }

        Element td = JSOUP.locateUpwardElement(anchor, "td");
        preserve.addAll(JSOUP.findElements(td, "td"));
        preserve.addAll(JSOUP.findElements(td, "tr"));
        preserve.addAll(JSOUP.findElements(td, "table"));

        deleteExcept("td", preserve);
        deleteExcept("tr", preserve);
        deleteExcept("table", preserve);

        if (0 != (flags & (REMOVE_MAIN_TEXT | COUNT_PAGES | CHECK_HAS_COMMENTS | EXTRACT_COMMENTS_JSON)))
            throw new Exception("Not implemented");

        if (0 != (flags & REMOVE_SCRIPTS))
        {
            List<Node> vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "script");
            JSOUP.removeElements(pageRoot, vnodes);

            vnodes = JSOUP.findElements(JSOUP.flatten(pageRoot), "noscript");
            JSOUP.removeElements(pageRoot, vnodes);
        }

        // recoverRussianText();

        if (rurl != null)
        {
            deleteActions();
            deleteAddComment();
        }
        setEncoding();
    }

    private Element locateArticleAnchor() throws Exception
    {
        Element anchor = null;

        for (Node n : JSOUP.findElements(pageRoot, "img"))
        {
            String alt = JSOUP.getAttribute(n, "alt");
            String width = JSOUP.getAttribute(n, "width");
            String height = JSOUP.getAttribute(n, "height");

            if (alt == null && width != null && height != null && width.equals("1") && height.equals("3"))
            {
                if (anchor != null)
                    throw new Exception("Duplicate article anchor");
                anchor = JSOUP.asElement(n);
            }
        }

        if (anchor == null)
            throw new Exception("Unable to locate article anchor");

        return anchor;
    }

    private void deleteExcept(String tag, List<Node> preserve) throws Exception
    {
        List<Node> vn = JSOUP.findElements(pageRoot, tag);
        for (Node n : vn)
        {
            if (!Util.containsIdentity(preserve, n))
            {
                n.remove();
            }
        }
    }

    @SuppressWarnings("unused")
    private void recoverRussianText() throws Exception
    {
        for (Node n : JSOUP.flatten(pageRoot))
        {
            if (n instanceof TextNode)
            {
                TextNode tn = (TextNode) n;
                Util.unused(tn);
            }
        }
    }

    private void deleteActions() throws Exception
    {
        Node button = null;
        Element table = null;

        for (Node an : JSOUP.findElements(pageRoot, "a"))
        {
            String href = JSOUP.getAttribute(an, "href");
            if (href != null && href.startsWith("/tools/memadd.bml?"))
            {
                button = an;
                break;
            }
        }

        table = JSOUP.locateUpwardElement(button, "table");
        table = JSOUP.locateUpwardElement(table, "table");
        JSOUP.removeElement(pageRoot, table);
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

    private void deleteAddComment() throws Exception
    {
        final String reply_url = String.format("%s/%s?mode=reply", LJUtil.userBase(), rurl);

        for (Node an : JSOUP.findElements(pageRoot, "a"))
        {
            String href = JSOUP.getAttribute(an, "href");
            String text = JSOUP.asElement(an).ownText();
            if (href != null && href.equals(reply_url) && text != null && text.equals("Добавить комментарий"))
            {
                Element p = JSOUP.locateUpwardElement(an, "p");
                JSOUP.removeElement(pageRoot, p);
                return;
            }
        }
    }

    @Override
    public void unsizeArticleHeight() throws Exception
    {
        // nothing to do
    }

    @Override
    public void removeNonArticleBodyContent() throws Exception
    {
        Node br_clear = null;
        Node div = null;
        Node hr = null;
        Node prev = null;

        for (Node n : JSOUP.findElements(pageRoot))
        {
            if (JSOUP.asElement(n).tagName().equalsIgnoreCase("br"))
            {
                String clear = JSOUP.getAttribute(n, "clear");
                if (clear != null && clear.equalsIgnoreCase("all"))
                    br_clear = n;
            }
            
            if (br_clear != null && prev == br_clear && JSOUP.asElement(n).tagName().equalsIgnoreCase("hr"))
            {
                hr = n;
            }
            
            if (JSOUP.asElement(n).tagName().equalsIgnoreCase("div"))
            {
                String id = JSOUP.getAttribute(n, "id");
                if (id != null && id.equalsIgnoreCase("Comments"))
                {
                    if (div != null)
                        throw new Exception("Duplicate section div id=comments");
                    div = n;
                }
            }
            
            prev = n;
        }

        if (div != null)
            JSOUP.removeElement(pageRoot, div);

        if (hr != null)
            JSOUP.removeElement(pageRoot, hr);
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
        Element table = null;

        for (Node n : JSOUP.findElements(pageRoot, "table"))
        {
            if (table == null)
                table = JSOUP.asElement(n);
        }

        if (table == null)
            throw new Exception("Unable to find article");

        return table;
    }
}
