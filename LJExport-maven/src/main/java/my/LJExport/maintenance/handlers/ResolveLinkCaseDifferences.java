package my.LJExport.maintenance.handlers;

import java.io.File;
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

/*
 * Scan HTML file and check that A.HREF and IMG.SRC links have the same case
 * as files in the links repository.
 * If they differ, fix link case in the HTML file to match link case.
 * 
 * Also eliminate trailing dots and spaces in path components such as:
 *     ../../../links/www.etnosy.ru/sites/default/files/bookshelf./evr.gif
 */
public class ResolveLinkCaseDifferences extends MaintenanceHandler
{
    private static boolean DryRun = true;   // ###
    
    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Resolve divergences between HTML link upper/lower case and repository filename case");
        super.beginUsers("Resolving link upper/lower case divergences");
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private Map<String, String> lc2ac = new HashMap<>();

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
            fp = linkDir + File.separator + fp;
            lc2ac.put(fp.toLowerCase(), fp);
        }
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
            relpath = sanitizePath(relpath);
            
            // check file exists in repository and in the same case
            String xp = linkDir + File.separator + relpath.replace("/", File.separator);
            String ac = lc2ac.get(xp.toLowerCase());
            
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
            String href = JSOUP.getAttribute(n, attr);
            String original_href = href;

            href = preprocesHref(href);
            if (href == null)
                continue;

            // strip trailing dots and spaces in path components
            // ../../../links/www.etnosy.ru/sites/default/files/bookshelf./evr.gif
            boolean sanitized = false;
            {
                String pre = href;
                href = sanitizePath(href);
                if (!href.equals(pre))
                    sanitized = true;
            }

            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href, false);
            if (linkInfo == null)
                continue;

            String actualLinkFullFilePath = lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
            if (actualLinkFullFilePath == null)
            {
                String msg = String.format("Link file [%s] is not present in the repository map, href=[%s], file=[%s]", 
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

            if (!newref.equalsIgnoreCase(original_href) && !sanitized)
                throwException("Unexpected link path (mismatch)");

            StringBuilder sb = new StringBuilder();

            sb.append(String.format("Changing [%s] HTML  %s" + nl, Config.User, original_href));
            sb.append(String.format("          %s    to  %s", spaces(Config.User), newref));

            trace(sb.toString());

            JSOUP.updateAttribute(n, attr, newref);
            updated = true;
        }

        return updated;
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
    
    private String spaces(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray())
        {
            sb.append(' ');
            Util.unused(c);
        }
        return sb.toString();
    }

    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }
}