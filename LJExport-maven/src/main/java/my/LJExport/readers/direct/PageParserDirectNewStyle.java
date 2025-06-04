package my.LJExport.readers.direct;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;

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
        // ###
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception
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
