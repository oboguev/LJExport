package my.LJExport.maintenance.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;

/*
 * Check for case conflicts in link file names.
 */
public class CheckLinkCaseConflicts extends MaintenanceHandler 
{
    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Checking link case conflicts");
        super.beginUsers("Checking link case conflicts");
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private Map<String, String> lc2xc = new HashMap<>();

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

        for (Node n : JSOUP.findElements(pageFlat, "img"))
        {
            String href = getLinkAttribute(n, "src");
            
            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
            if (linkInfo == null)
                continue;

            String xc = href;
            String lc = xc.toLowerCase();
            String v = lc2xc.get(lc);

            if (v == null)
            {
                lc2xc.put(lc, xc);
            }
            else if (v.equals(xc))
            {
                // do nothing
            }
            else
            {
                StringBuilder sb = new StringBuilder();
                sb.append("URL case clash: " + v + nl);
                sb.append("                " + xc);
                
                errorMessageLog.add(sb);
                Util.err(sb.toString());
            }
        }
    }
}