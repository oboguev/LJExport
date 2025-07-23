package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;

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

            boolean fix = false;

            if (href_raw.startsWith("../") && href_raw.endsWith("../links/null"))
                fix = true;
            
            if (!fix && href_raw.endsWith("/null"))
            {
                Util.err("Null link outside of links repo");
                throwException("Null link outside of links repo");
            }

            if (!fix)
                continue;

            if (href_original != null && href_original.trim().length() != 0)
            {
                if (href_original.startsWith("/web/20"))
                    href_original = "https://web.archive.org" + href_original;

                if (!href_original.startsWith("http://") && !href_original.startsWith("https://"))
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("Non-absolute %s.%s  link URL  %s" + nl, tag, attr, href_original));
                    sb.append(String.format("             %s %s   in file  %s" + nl, spaces(tag), spaces(attr), fullHtmlFilePath));
                    Util.err(sb.toString());
                }
            }

            if (href_original != null && href_original.trim().length() != 0)
            {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Changing %s.%s link from  %s" + nl, tag, attr, href_raw));
                sb.append(String.format("         %s %s   back to  %s" + nl, spaces(tag), spaces(attr), href_original));
                sb.append(String.format("         %s %s   in file  %s" + nl, spaces(tag), spaces(attr), fullHtmlFilePath));
                Util.out(sb.toString());

                JSOUP.updateAttribute(n, attr, href_original);
                updated = true;
            }
            else
            {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("No original URL for null %s.%s link to  %s" + nl, tag, attr, href_raw));
                sb.append(String.format("                         %s %s in file  %s" + nl, spaces(tag), spaces(attr),
                        fullHtmlFilePath));
                Util.err(sb.toString());
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