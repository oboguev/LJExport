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

/*
 * Check for missing link files pointed from HTML pages but not present in the repository
 */
public class CheckMissingLinks extends MaintenanceHandler
{
    private static boolean DryRun = true;

    public CheckMissingLinks() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Verify that link files are present");
        super.beginUsers("Searching for missing links");
        txLog.writeLine(String.format("Executing CheckMissingLinks in %s mode", DryRun ? "DRY RUN" : "WET RUN"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private Map<String, String> file_lc2ac = new HashMap<>();
    private Map<String, String> dir_lc2ac = new HashMap<>();

    @Override
    protected void beginUser() throws Exception
    {
        /* clear for new user */
        file_lc2ac = new HashMap<>();
        dir_lc2ac = new HashMap<>();

        super.beginUser();
        build_lc2ac();
    }

    private void build_lc2ac() throws Exception
    {
        for (String fp : Util.enumerateFiles(linksDir, null))
        {
            // skip control files located in root directory
            if (isLinksRootFileRelativePathSyntax(fp))
                continue;

            fp = linksDir + File.separator + fp;
            file_lc2ac.put(fp.toLowerCase(), fp);
        }

        for (String fp : Util.enumerateDirectories(linksDir))
        {
            fp = linksDir + File.separator + fp;
            dir_lc2ac.put(fp.toLowerCase(), fp);
        }

        /*
         * consistency validation
         */
        for (String fn : file_lc2ac.keySet())
            if (dir_lc2ac.containsKey(fn))
                throwException("Error enumerating files and directories");

        for (String fn : dir_lc2ac.keySet())
            if (file_lc2ac.containsKey(fn))
                throwException("Error enumerating files and directories");
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
            String href = getLinkAttribute(n, attr);
            href = Util.stripAnchor(href);

            if (href == null || !isLinksRepositoryReference(fullHtmlFilePath, href))
                continue;

            if (isArchiveOrg())
            {
                /* ignore bad links due to former bug in archive loader */
                if (href.startsWith("../") && href.endsWith("../links/null"))
                    continue;
            }

            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
            if (linkInfo == null)
                continue;

            String ac = file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
            if (ac == null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Missing link file [%s]  %s" + nl, Config.User, linkInfo.linkFullFilePath));
                sb.append(String.format("          in HTML  %s   %s" + nl, spaces(Config.User), fullHtmlFilePath));
                sb.append(String.format("             href  %s   %s" + nl, spaces(Config.User), href));
                trace(sb.toString());
            }
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
        Util.out(msg);
    }

    @SuppressWarnings("unused")
    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }
}