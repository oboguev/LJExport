package my.LJExport;

public class PageParserPassive extends PageParser
{
    @Override
    protected String getPageSource() throws Exception
    {
        throw new Exception("Passive page parser trying to getPageSource");
    }
}
