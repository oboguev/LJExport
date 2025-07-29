package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.url.URLCodec;
import my.LJExport.runtime.url.UrlUtil;

/*
 * Verify that local URL links are properly URL-encoded in IMG.SRC and A.HREF tags
 * and point to local files. 
 */
public class VerifyUnencodedLinks extends MaintenanceHandler
{
    private static boolean DryRun = true;

    private static int NScannedLinks = 0;
    private static int NCorrectLinks = 0;

    public VerifyUnencodedLinks() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Verify that local URL links are properly URL-encoded in IMG.SRC and A.HREF tags ");
        super.beginUsers("Verifying for unencoded links");
        txLog.writeLine(String.format("Executing VerifyUnencodedLinks in %s mode", DryRun ? "DRY RUN" : "WET RUN"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();

        Util.out("");
        Util.out("VerifyUnencodedLinks summary:");
        Util.out("");
        Util.out("  Scanned links: " + NScannedLinks);
        Util.out("  Correct links: " + NCorrectLinks);
        Util.out("  Missing links: " + (NScannedLinks - NCorrectLinks));
        Util.out("");
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
        for (String fp : Util.enumerateFilesAndDirectories(linksDir))
        {
            if (isLinksRootFileRelativePath(fp))
                continue;

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

            if (href_raw == null)
                continue;

            if (isArchiveOrg())
            {
                /* ignore bad links due to former bug in archive loader */
                if (href_raw.startsWith("../") && href_raw.endsWith("../links/null"))
                    continue;
            }

            if (!(href_raw.startsWith("../") && href_raw.contains("../links/")))
                continue;

            NScannedLinks++;

            /*
             * Check if decoded value points to a file, then everything is fine
             */
            String decoded_href = UrlUtil.decodeUrl(href_raw);

            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(decoded_href))
            {
                String rel = href2rel(decoded_href, fullHtmlFilePath);
                if (rel_exists(rel))
                {
                    NCorrectLinks++;
                    continue;
                }
            }

            Util.out(String.format("Missing link file for %s.%s link to  %s" + nl, tag, attr, decoded_href));
            Util.out(String.format("                      %s.%s in file  %s" + nl, spaces(tag), spaces(attr), fullHtmlFilePath));
        }

        return updated;
    }

    /* ===================================================================================================== */

    @SuppressWarnings("unused")
    @Override
    protected void trace(String msg) throws Exception
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