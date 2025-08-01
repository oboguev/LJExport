package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.url.URLCodec;
import my.LJExport.runtime.url.UrlUtil;

/*
 * Fix local URL links not properly URL-encoded in IMG.SRC and A.HREF tags 
 */
public class FixUnencodedLinks extends MaintenanceHandler
{
    private static boolean DryRun = true;

    private static int NScannedLinks = 0;
    private static int NCorrectLinks = 0;
    private static int NFixableLinks = 0;
    private static int NDanglingLinks = 0;

    public FixUnencodedLinks() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> * Fix local URL links not properly URL-encoded in IMG.SRC and A.HREF tags ");
        super.beginUsers("Fixing unencoded links");
        txLog.writeLine(String.format("Executing FixUnencodedLinks in %s mode", DryRun ? "DRY RUN" : "WET RUN"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();

        Util.out("");
        Util.out("FixUnencodedLinks summary:");
        Util.out("");
        Util.out("  Scanned links: " + NScannedLinks);
        Util.out("  Correct links: " + NCorrectLinks);
        Util.out("  Fixable links: " + NFixableLinks);
        Util.out(  "Dangling links: " + NDanglingLinks);
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

            /*
             * Does href_raw point to a file?
             * If so, encode it. 
             */
            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href_raw))
            {
                String rel = href2rel(href_raw, fullHtmlFilePath);
                if (rel_exists(rel))
                {
                    updateLinkAttribute(n, attr, href_raw);
                    updated = true;
                    changeMessage(href_raw, LinkFilepath.encodePathComponents(href_raw), fullHtmlFilePath, tag, attr);
                    continue;
                }

            }

            String href2 = URLCodec.encode(href_raw).replace("%2F", "/");
            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href2))
            {
                String rel = href2rel(href2, fullHtmlFilePath);
                if (rel_exists(rel))
                {
                    updateLinkAttribute(n, attr, href2);
                    updated = true;
                    changeMessage(href_raw, LinkFilepath.encodePathComponents(href2), fullHtmlFilePath, tag, attr);
                    continue;
                }
            }

            String href3 = URLCodec.encodeFilename(href_raw).replace("%2F", "/");
            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href3))
            {
                String rel = href2rel(href3, fullHtmlFilePath);
                if (rel_exists(rel))
                {
                    updateLinkAttribute(n, attr, href3);
                    updated = true;
                    changeMessage(href_raw, LinkFilepath.encodePathComponents(href3), fullHtmlFilePath, tag, attr);
                    continue;
                }
            }

            danglingLinkMessage(href_raw, fullHtmlFilePath, tag, attr);
            NDanglingLinks++;
        }

        return updated;
    }

    /* ===================================================================================================== */

    private void changeMessage(String href_raw, String href_replacement, String fullHtmlFilePath, String tag, String attr)
            throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Changing [%s] HTML %s.%s from  %s" + nl, Config.User, tag, attr, href_raw));
        sb.append(String.format("          %s       %s %s   to  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr),
                href_replacement));
        sb.append(String.format("          %s       %s %s   in  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr),
                fullHtmlFilePath));

        trace(sb.toString());
        Util.out(sb.toString());

        NFixableLinks++;
    }

    private void danglingLinkMessage(String href_raw, String fullHtmlFilePath, String tag, String attr)
            throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Dangling [%s] HTML %s.%s link  %s" + nl, Config.User, tag, attr, href_raw));
        sb.append(String.format("          %s       %s %s   in  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr),
                fullHtmlFilePath));

        trace(sb.toString());
        Util.err(sb.toString());
        errorMessageLog.add(sb.toString());
    }

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