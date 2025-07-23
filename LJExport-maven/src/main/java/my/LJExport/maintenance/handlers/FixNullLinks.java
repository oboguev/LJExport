package my.LJExport.maintenance.handlers;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.url.URLCodec;

/*
 * Fix local links ending in ../links/null
 */
public class FixNullLinks extends MaintenanceHandler
{
    private static boolean DryRun = true;

    public FixNullLinks() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Fix local URL links ending in null ");
        super.beginUsers("Fixing null links");
        txLog.writeLine(String.format("Executing FixNullLinks in %s mode", DryRun ? "DRY RUN" : "WET RUN"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private Map<String, String> rel_filedir_lc2ac = new HashMap<>();

    @Override
    protected void beginUser() throws Exception
    {
        /* clear for new user */
        rel_filedir_lc2ac = new HashMap<>();

        super.beginUser();
        build_lc2ac();
    }

    private void build_lc2ac() throws Exception
    {
        for (String fp : Util.enumerateFilesAndDirectories(linkDir))
        {
            String relpath = fp.replace(File.separatorChar, '/');
            rel_filedir_lc2ac.put(relpath.toLowerCase(), relpath);
        }
    }

    private boolean rel_exists(String rel)
    {
        return rel_filedir_lc2ac.containsKey(rel.toLowerCase());
    }

    /* ===================================================================================================== */

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

        boolean updated = false;

        updated |= process(fullHtmlFilePath, relativeFilePath, parser, pageFlat, "a", "href");
        updated |= process(fullHtmlFilePath, relativeFilePath, parser, pageFlat, "img", "src");

        if (updated && !DryRun)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullHtmlFilePath, html);
        }
    }

    private boolean process(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat, String tag, String attr) throws Exception
    {
        boolean updated = false;

        for (Node n : JSOUP.findElements(pageFlat, tag))
        {
            String href_raw = this.getLinkAttributeUndecoded(n, attr);
            String href_original = JSOUP.getAttribute(n, "original-" + attr);

            if (href_raw == null)
                continue;

            if (!(href_raw.startsWith("../") && href_raw.endsWith("../links/null")))
                continue;
            
            if (href_original != null && href_original.trim().length() != 0)
            {
                
                Util.out(String.format("Changing %s.%s link to  %s" + nl, tag, attr, href_raw));
                Util.out(String.format("         %s.%s back to  %s" + nl, spaces(tag), spaces(attr), href_original));
                Util.out(String.format("         %s.%s in file  %s" + nl, spaces(tag), spaces(attr), fullHtmlFilePath));
                
                JSOUP.updateAttribute(n, attr, href_original);
                updated = true;
            }
            else
            {
                Util.out(String.format("No original URL for null %s.%s link to  %s" + nl, tag, attr, href_raw));
                Util.out(String.format("                         %s.%s in file  %s" + nl, spaces(tag), spaces(attr), fullHtmlFilePath));
            }
        }

        return updated;
    }

    /* ===================================================================================================== */

    @SuppressWarnings("unused")
    private void trace(String msg) throws Exception
    {
        // errorMessageLog.add(msg);
        traceWriter.write(msg + nl);
        traceWriter.flush();
        // Util.out(msg);
    }

    @SuppressWarnings("unused")
    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }
}