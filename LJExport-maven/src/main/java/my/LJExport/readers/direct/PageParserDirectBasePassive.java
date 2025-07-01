package my.LJExport.readers.direct;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;

public class PageParserDirectBasePassive extends PageParserDirectBase
{
    public PageParserDirectBasePassive()
    {
        super(new NoPageSource());
    }

    @Override
    public void removeJunk(int flags) throws Exception
    {
        throw new Exception("Not implemented");
    }

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
        throw new Exception("Not implemented");
    }

    public static class NoPageSource implements PageContentSource
    {
        @Override
        public String getPageSource() throws Exception
        {
            throw new Exception("Not implemented");
        }
    }
}
