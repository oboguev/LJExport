package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.readers.direct.PageParserDirectDreamwidthOrg;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.links.util.RelativeLink;
import my.LJExport.runtime.links.util.RelativeLink.InvalidNestedPathException;

/*
 * Scan HTML files and check that A.HREF and IMG.SRC links have the same case
 * as actual files in the links repository.
 * If cases differ, fix link case in the HTML file to match link case.
 * 
 * Also eliminate trailing dots and spaces in path components such as:
 *     ../../../links/www.etnosy.ru/sites/default/files/bookshelf./evr.gif
 *     ../../../links/www.etnosy.ru/sites/default/files/bookshelf /evr.gif
 */
public class ResolveLinkCaseDifferences extends MaintenanceHandler
{
    private static boolean DryRun = true;

    public ResolveLinkCaseDifferences() throws Exception
    {
    }

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
        /* clear for new user */
        filedir_lc2ac = new HashMap<>();
        href_lc2ac = new HashMap<>();

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

            if (isArchiveOrg())
            {
                while (relpath.startsWith("/"))
                    relpath = relpath.substring(1);
            }

            if (relpath.contains("\\") || relpath.endsWith("/"))
                throwException("Invalid map entry");

            if (Config.isDreamwidthOrg() && relpath.startsWith("p.dreamwidth.org/"))
                relpath = PageParserDirectDreamwidthOrg.mapProxiedURI(relpath, "p.dreamwidth.org/");

            relpath = sanitizePath(relpath);

            // check file exists in repository and in the same case
            String xp = linkDir + File.separator + relpath.replace("/", File.separator);
            String ac = filedir_lc2ac.get(xp.toLowerCase());

            if (ac == null)
            {
                if (Config.isDreamwidthOrg())
                {
                    trace("File is missing in links repository: " + relpath + nl);
                    continue;
                }
                else
                {
                    throwException("File is missing in links repository");
                }
            }

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
                sb.append(String.format("          %s            to  %s" + nl, spaces(Config.User), relpath));

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

            if (isArchiveOrg())
            {
                /* ignore bad links due to former bug in archive loader */
                if (href.startsWith("../") && href.endsWith("../links/null"))
                    continue;
            }

            if (handleIncompleteOnlineURLs(n, tag, attr, href))
            {
                updated = true;
                continue;
            }

            if (!isLinksRepositoryReference(fullHtmlFilePath, href))
                continue;

            href = variants(href, fullHtmlFilePath);
            if (href == null)
                continue;

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
            sb.append(String.format("          %s   for  %s" + nl, spaces(Config.User), actualLinkFullFilePath));

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
        String h3 = LinkFilepath.encodePathComponents(href);
        String h4 = sanitizePath(LinkFilepath.encodePathComponents(href));
        String h5 = href + "_xxx.jpeg";

        List<String> list = new ArrayList<>();

        list.add(h1);

        if (!list.contains(h2))
            list.add(h2);
        if (!list.contains(h3))
            list.add(h3);
        if (!list.contains(h4))
            list.add(h4);
        if (!list.contains(h5))
            list.add(h5);

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
        {
            if (href.contains("../links/imgprx.livejournal.net/"))
            {
                trace("Ignore missing URL: " + href);
                return null;
            }

            throwException("No link repository file for " + href);
        }

        // priorities: h2 -> h4 ->  h1 ->  h3 -> h5

        if (exlist.contains(h2))
            return h2;
        if (exlist.contains(h4))
            return h4;

        if (exlist.contains(h1))
            return h1;
        if (exlist.contains(h3))
            return h3;

        if (exlist.contains(h5))
            return h5;

        throwException("Internal error");
        return null;
    }

    private String relativeToLinkRepository(String href, String fullHtmlFilePath) throws Exception
    {
        String abs = RelativeLink.resolveFileRelativeLink(fullHtmlFilePath, href, this.linkDir);
        if (abs == null)
            throwException("Internal error");

        String prefix = this.linkDir + File.separator;
        if (!abs.startsWith(prefix))
            throwException("Link is not within the repository");

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

    /* ===================================================================================================== */

    private boolean handleIncompleteOnlineURLs(Node n, String tag, String attr, String href) throws Exception
    {
        if (handleIncompleteStart(n, tag, attr, href, "../../img/", "https://www.livejournal.com/img/"))
            return true;

        if (handleIncompleteStart(n, tag, attr, href, "../../../img/", "https://www.livejournal.com/img/"))
            return true;

        if (handleIncompleteStart(n, tag, attr, href, "../../palimg/", "https://www.livejournal.com/palimg/"))
            return true;

        if (handleIncompleteStart(n, tag, attr, href, "../wp-content/uploads/", "https://unknown.host/wp-content/uploads/"))
            return true;

        if (handleIncompleteExact(n, tag, attr, href, "../images/line_sm.gif", "https://unknown.host/images/line_sm.gif"))
            return true;

        return false;
    }

    private boolean handleIncompleteStart(Node n, String tag, String attr, String href, String matchPrefix, String replaceWith)
            throws Exception
    {
        if (!href.startsWith(matchPrefix))
            return false;

        String newref = replaceWith + Util.stripStart(href, matchPrefix);
        trace(String.format("Changing [%s] HTML incomplete URL: %s => %s" + nl, Config.User, href, newref));
        updateLinkAttribute(n, attr, newref);

        return true;
    }

    private boolean handleIncompleteExact(Node n, String tag, String attr, String href, String match, String replaceWith)
            throws Exception
    {
        if (!href.equals(match))
            return false;

        String newref = replaceWith;
        trace(String.format("Changing [%s] HTML incomplete URL: %s => %s" + nl, Config.User, href, newref));
        updateLinkAttribute(n, attr, newref);

        return true;
    }
}