package my.LJExport.readers.direct;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;

/**
 * Parser for LJ new-style pages
 */
public class PageReaderDirectNewStyle extends PageParserDirectBase
{
    public PageReaderDirectNewStyle(PageContentSource pageContentSource)
    {
        super(pageContentSource);
    }

    @Override
    public void removeJunk(int flags) throws Exception
    {
        // ###
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Element findCommentsSection(Node pageRootCurrent) throws Exception
    {
        // ###
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void injectComments(Element commentsSection, CommentsTree commentTree) throws Exception
    {
        // ###
        throw new RuntimeException("Not implemented");
    }
}
