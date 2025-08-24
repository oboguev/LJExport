package my.LJExport.maintenance.handlers;

import java.util.List;

import org.jsoup.nodes.Node;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;

/*
 * Убрать из записей пометку о мемориальном статусе журнала 
 */
public class RemoveMemorialStatusNotice extends MaintenanceHandler
{
    private static final boolean DryRun = true;
    
    public RemoveMemorialStatusNotice() throws Exception
    {
    }
    
    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Removing memorial status notice");
        super.beginUsers("Removing memorial status notice");
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    @Override
    protected void beginUser() throws Exception
    {
        super.beginUser();
    }

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

        boolean update = parser.removeMemorialStatusNotice();

        if (update)
        {
            if (DryRun)
            {
                supertrace("DRY RUN: not updating HTML file " + fullHtmlFilePath);
            }
            else
            {
                String html = JSOUP.emitHtml(parser.pageRoot);
                Util.writeToFileSafe(fullHtmlFilePath, html);
                supertrace("Updated HTML file " + fullHtmlFilePath);
            }
        }
    }
}
