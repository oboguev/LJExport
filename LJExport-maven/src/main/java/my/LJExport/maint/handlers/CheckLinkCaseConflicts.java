package my.LJExport.maint.handlers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.maint.Maintenance;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.FilePath;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;

public class CheckLinkCaseConflicts extends Maintenance
{
    protected void beginUsers()
    {
        Util.out(">>> Checking link case conflicts");
        super.beginUsers("Checking link case conflicts");
    }

    protected void endUsers()
    {
        super.endUsers();
    }

    private Map<String, String> lc2xc = new HashMap<>();

    protected void processHtmlFile(String fullFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullFilePath, relativeFilePath, parser, pageFlat);

        final String linkDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "links";
        final String linkDirSep = linkDir + File.separator;

        for (Node n : JSOUP.findElements(pageFlat, "img"))
        {
            String href = JSOUP.getAttribute(n, "src");
            if (href == null || !href.startsWith("../"))
                continue;

            File fp = new File(fullFilePath);
            fp = new File(fp, ("../" + href).replace("/", File.separator));
            fp = FilePath.canonicalFile(fp);
            String linkFullFilePath = fp.toString();
            if (!linkFullFilePath.startsWith(linkDirSep))
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
