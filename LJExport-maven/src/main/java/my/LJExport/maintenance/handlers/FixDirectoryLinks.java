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
 * Fix IMG.SRC and A.HREF links pointing to a directory.
 * If this directory has a single file, redirect the link to this file.
 * Adjust repository map file as well.
 * 
 * Execute AFTER ResolveLinkCaseDifferences.
 */
public class FixDirectoryLinks extends MaintenanceHandler
{
    private static boolean DryRun = true; // ###

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Fix links pointing to directories");
        super.beginUsers("Fix links pointing to directories");
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
        super.beginUser();
        build_lc2ac();
        // ###loadLinkMapFile()
    }

    private void build_lc2ac() throws Exception
    {
        for (String fp : Util.enumerateFiles(linkDir, null))
        {
            fp = linkDir + File.separator + fp;
            file_lc2ac.put(fp.toLowerCase(), fp);
        }

        for (String fp : Util.enumerateDirectories(linkDir))
        {
            fp = linkDir + File.separator + fp;
            dir_lc2ac.put(fp.toLowerCase(), fp);
        }
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
            String href = JSOUP.getAttribute(n, attr);

            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href, true);
            if (linkInfo == null)
                continue;

            // regular file?
            if (null != file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase()))
                continue;

            if (null != dir_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase()))
            {
                String msg = String.format("Link file/dir [%s] is not present in the repository map, href=[%s], filepath=[%s]",
                        Config.User, href, linkInfo.linkFullFilePath);

                if (DryRun)
                {
                    trace(msg);
                    continue;
                }
                else
                {
                    throwException(msg);
                }
            }

            File fp = new File(linkInfo.linkFullFilePath).getCanonicalFile();
            int count = countContainedFiles(fp);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[%s] %s.%s => [%d] %s", Config.User, tag, attr, count, linkInfo.linkFullFilePath));
            trace(sb.toString());

            if (count != 1)
            {
                Util.noop();
                // ### throwException("Multiple files in linked directory " + linkInfo.linkFullFilePath);
                continue;
            }

            if (count == 1)
            {
                // ###
                // ### JSOUP.updateAttribute(n, attr, newref);
                updated = true;
            }
        }

        return updated;
    }

    /* ===================================================================================================== */

    private int countContainedFiles(File fp)
    {
        int count = 0;

        for (File fpx : fp.listFiles())
        {
            if (fpx.isFile())
                count++;
        }

        return count;
    }

    private void trace(String msg)
    {
        errorMessageLog.add(msg);
        Util.err(msg);
    }

    @SuppressWarnings("unused")
    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }
}
