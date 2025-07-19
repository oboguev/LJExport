package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.RelativeLink;
import my.LJExport.runtime.links.RelativeLink.InvalidNestedPathException;

/*
 * Scan HTML files and check that A.HREF and IMG.SRC links have the same case
 * as actual files in the links repository.
 * If cases differ, fix link case in the HTML file to match link case.
 * 
 * Also eliminate trailing dots and spaces in path components such as:
 *     ../../../links/www.etnosy.ru/sites/default/files/bookshelf./evr.gif
 *     ../../../links/www.etnosy.ru/sites/default/files/bookshelf /evr.gif
 *     
 * Execute AFTER FixLinkEncoding
 */
public class ResolveLinkCaseDifferences extends MaintenanceHandler
{
    private static boolean DryRun = true; // ###

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Resolve divergences between HTML link upper/lower case and repository filename case");
        super.beginUsers("Resolving link upper/lower case divergences");
        txLog.writeLine(String.format("Executing ResolveLinkCaseDifferences in %s mode", DryRun ? "DRY RUN" : "WET RUN"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private Map<String, String> filedir_lc2ac = new HashMap<>();
    private Map<String, String> href_lc2ac = new HashMap<>();

    @Override
    protected void beginUser() throws Exception
    {
        super.beginUser();
        build_lc2ac();
        scanAndUpateLinkMapFile();
    }

    private void build_lc2ac() throws Exception
    {
        for (String fp : Util.enumerateFilesAndDirectories(linkDir))
        {
            String relpath = fp.replace(File.separatorChar, '/');
            href_lc2ac.put(relpath.toLowerCase(), relpath);

            fp = linkDir + File.separator + fp;
            filedir_lc2ac.put(fp.toLowerCase(), fp);
        }
    }

    private boolean href_exists(String href)
    {
        return href_lc2ac.containsKey(href.toLowerCase());
    }

    private void scanAndUpateLinkMapFile() throws Exception
    {
        // edit map file to strip trailing dots and spaces in componentes
        // ../../../links/www.etnosy.ru/sites/default/files/bookshelf./evr.gif

        String mapFilePath = this.linkDir + File.separator + LinkDownloader.LinkMapFileName;
        boolean update = false;

        List<LinkMapEntry> list = FileBackedMap.readMapFile(mapFilePath);

        for (LinkMapEntry e : list)
        {
            String relpath = e.value;

            if (relpath.contains("\\") || relpath.endsWith("/"))
                throwException("Invalid map entry");

            relpath = sanitizePath(relpath);

            // check file exists in repository and in the same case
            String xp = linkDir + File.separator + relpath.replace("/", File.separator);
            String ac = filedir_lc2ac.get(xp.toLowerCase());

            if (ac == null)
                throwException("File is missing in links repository");

            if (!ac.startsWith(linkDir + File.separator))
                throwException("File is outside of links repository");

            if (!ac.equalsIgnoreCase(xp))
                throwException("Mismatch between link repository map file and repository files");

            relpath = Util.stripStart(ac, linkDir + File.separator);
            relpath = relpath.replace(File.separator, "/");

            if (!relpath.equals(e.value))
            {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Changing [%s] LinksDir map  %s" + nl, Config.User, e.value));
                sb.append(String.format("          %s            to  %s", spaces(Config.User), relpath));

                trace(sb.toString());

                e.value = relpath;
                update = true;
            }
        }

        if (update && !DryRun)
        {
            txLog.writeLine("updating links map " + mapFilePath);
            String content = FileBackedMap.recomposeMapFile(list);
            Util.writeToFileVerySafe(mapFilePath, content);
            txLog.writeLine("updated OK");
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
            String href = getLinkAttribute(n, attr);
            String original_href = href;

            if (href == null)
                continue;
            
            {
                // #######
                if (href.startsWith("../../img/userinfo.gif?"))
                    continue;
                if (href.startsWith("../../img/community.gif?"))
                    continue;
                if (href.equals("../images/line_sm.gif"))
                    continue;
            }
            
            if (!isLinksRepositoryReference(fullHtmlFilePath, href))
                continue;
            
            href = variants(href, fullHtmlFilePath);

            // strip trailing dots and spaces in path components
            // ../../../links/www.etnosy.ru/sites/default/files/bookshelf./evr.gif
            // href = sanitizePath(href);

            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
            if (linkInfo == null)
                continue;

            String actualLinkFullFilePath = filedir_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
            if (actualLinkFullFilePath == null)
            {
                String msg = String.format("Link file [%s] is not present in the repository, href=[%s], file=[%s]",
                        Config.User, original_href, linkInfo.linkFullFilePath);

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

            String newref = RelativeLink.fileRelativeLink(actualLinkFullFilePath, fullHtmlFilePath, Config.DownloadRoot);

            if (newref.equals(original_href))
                continue;
            
            StringBuilder sb = new StringBuilder();

            sb.append(String.format("Changing [%s] HTML  %s" + nl, Config.User, original_href));
            sb.append(String.format("          %s    to  %s" + nl, spaces(Config.User), newref));
            sb.append(String.format("          %s    in  %s" + nl, spaces(Config.User), fullHtmlFilePath));
            sb.append(String.format("          %s   for  %s", spaces(Config.User), actualLinkFullFilePath));

            trace(sb.toString());

            updateLinkAttribute(n, attr, newref);
            updated = true;
        }

        return updated;
    }

    /*
     * Older LinkDownloaded did not encode file names to HREF.
     * Try to recover it.  
     */
    private String variants(String href, String fullHtmlFilePath) throws Exception
    {
        // String expected = RelativeLink.resolveFileRelativeLink(fullHtmlFilePath, href, this.linkDir);
        // Util.unused(expected);

        String h1 = href;
        String h2 = sanitizePath(href);
        String h3 = LinkDownloader.encodePathComponents(href);
        String h4 = sanitizePath(LinkDownloader.encodePathComponents(href));

        List<String> list = new ArrayList<>();

        list.add(h1);

        if (!list.contains(h2))
            list.add(h2);
        if (!list.contains(h3))
            list.add(h3);
        if (!list.contains(h4))
            list.add(h4);

        List<String> exlist = new ArrayList<>();
        for (String h : list)
        {
            String relativeToRepository = null;

            try
            {
                relativeToRepository = relativeToLinkRepository(h, fullHtmlFilePath);
            }
            catch (InvalidNestedPathException ex)
            {
                continue;
            }

            if (relativeToRepository != null && href_exists(relativeToRepository))
                exlist.add(h);
        }

        if (exlist.size() == 1)
            return exlist.get(0);

        // return null;
        if (exlist.size() == 0)
            throwException("No link repository file for " + href);
        else
            throwException("Multpiple link repository file mappings for " + href);

        return null;
    }

    private String relativeToLinkRepository(String href, String fullHtmlFilePath) throws Exception
    {
        String abs = RelativeLink.resolveFileRelativeLink(fullHtmlFilePath, href, this.linkDir);
        if (abs == null)
            throw new Exception("Internal error");

        String prefix = this.linkDir + File.separator;
        if (!abs.startsWith(prefix))
            throw new Exception("Link is not within the repository");

        String result = abs.substring(prefix.length());
        result = result.replace(File.separatorChar, '/');

        return result;

    }

    /* ===================================================================================================== */

    private String sanitizePath(String path) throws Exception
    {
        StringBuilder sb = new StringBuilder();

        // cannot have trailing dots or spaces:
        // from  ../../../links/www.etnosy.ru/sites/default/files/bookshelf./evr.gif
        // to    ../../../links/www.etnosy.ru/sites/default/files/bookshelf/evr.gif
        for (String pc : path.split("/"))
        {
            if (sb.length() != 0)
                sb.append("/");

            if (pc.length() == 0)
            {
                throwException("Path component is empty");
            }
            else if (pc.equals("."))
            {
                throwException("Path component is dot");
            }
            else if (pc.equals(".") || pc.equals(".."))
            {
                sb.append(pc);
            }
            else if (pc.endsWith(".") || pc.endsWith(" "))
            {
                while (pc.endsWith(".") || pc.endsWith(" "))
                {
                    if (pc.endsWith("."))
                        pc = Util.stripTail(pc, ".");
                    else if (pc.endsWith(" "))
                        pc = Util.stripTail(pc, " ");
                }

                if (pc.length() == 0)
                    throwException("Path component is empty");

                sb.append(pc);
            }
            else
            {
                sb.append(pc);
            }
        }

        return sb.toString();
    }

    private void trace(String msg)
    {
        errorMessageLog.add(msg);
        Util.err(msg);
    }

    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }
}