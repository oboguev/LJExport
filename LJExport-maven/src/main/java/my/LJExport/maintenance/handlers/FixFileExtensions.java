package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.TxLog.Safety;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeWorkContext;

/*
 * Fix linked file name extensions according to file actual content. 
 * Scan IMG.SRC and A.HREF links.
 * If link does not have an extension in it s name, or points to a file of different actual type
 * than implied by extension, then rename the file and adjust the link.
 * Adjust repository map file as well.
 * 
 * Execute AFTER ResolveLinkCaseDifferences
 *     and AFTER FixDirectoryLinks.
 */
public class FixFileExtensions extends MaintenanceHandler
{
    private static boolean DryRun = true; // ###
    private static final Safety safety = Safety.UNSAFE;

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Fix linked file extensions");
        super.beginUsers("Fix linked file extensions");
        txLog.writeLine(String.format("Executing FixFileExtensions in %s RUN mode", DryRun ? "DRY" : "WET"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private Map<String, String> file_lc2ac = new HashMap<>();
    private Map<String, String> dir_lc2ac = new HashMap<>();
    private boolean updatedMap = false;
    private List<LinkMapEntry> linkMapEntries;
    private Map<String, List<LinkMapEntry>> relpath2entry;
    private Map<String, String> alreadyRenamed = new HashMap<>(); // rel -> rel
    private Map<String, String> fileContentExtensionMap = new HashMap<>();

    @Override
    protected void beginUser() throws Exception
    {
        /* clear for new user */
        file_lc2ac = new HashMap<>();
        dir_lc2ac = new HashMap<>();
        updatedMap = false;
        linkMapEntries = null;
        relpath2entry = null;
        alreadyRenamed = new HashMap<>();
        fileContentExtensionMap = new HashMap<>();

        txLog.writeLine("Starting user " + Config.User);
        super.beginUser();
        build_lc2ac();
        updatedMap = false;
        loadLinkMapFile();
        fillFileContentExtensionMap();
        
        trace("");
        trace("");
        trace("================================= Beginning user " + Config.User);
        trace("");
        trace("");
    }

    @Override
    protected void endUser() throws Exception
    {
        if (updatedMap && !DryRun)
        {
            String mapFilePath = this.linkDir + File.separator + LinkDownloader.LinkMapFileName;

            txLog.writeLine("updating links map " + mapFilePath);
            String content = FileBackedMap.recomposeMapFile(linkMapEntries);
            Util.writeToFileVerySafe(mapFilePath, content);
            txLog.writeLine("updated OK");
        }

        txLog.writeLine("Completed user " + Config.User);

        trace("");
        trace("");
        trace("================================= Completed user " + Config.User);
        trace("");
        trace("");
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

    private void loadLinkMapFile() throws Exception
    {
        String mapFilePath = this.linkDir + File.separator + LinkDownloader.LinkMapFileName;
        linkMapEntries = FileBackedMap.readMapFile(mapFilePath);

        relpath2entry = new HashMap<>();

        for (LinkMapEntry e : linkMapEntries)
        {
            String relpath = e.value;

            if (relpath.contains("\\") || relpath.endsWith("/"))
                throwException("Invalid map entry");

            List<LinkMapEntry> list = relpath2entry.get(relpath.toLowerCase());
            if (list == null)
            {
                list = new ArrayList<LinkMapEntry>();
                relpath2entry.put(relpath.toLowerCase(), list);
            }
            list.add(e);
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
            String href_original = href;

            if (href == null || !isLinksRepositoryReference(fullHtmlFilePath, href))
                continue;

            if (handleAlreadyRenamed(href, href_original, fullHtmlFilePath, n, tag, attr))
            {
                updated = true;
                continue;
            }

            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
            if (linkInfo == null)
                continue;

            String ac = file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
            if (ac == null)
            {
                String msg = String.format("Link file/dir [%s] is not present in the repository map, href=[%s], filepath=[%s]",
                        Config.User, href_original, linkInfo.linkFullFilePath);

                boolean allow = Config.User.equals("d_olshansky") && href.contains("../links/imgprx.livejournal.net/");

                if (DryRun || allow)
                {
                    trace(msg);
                    continue;
                }
                else
                {
                    throwException(msg);
                }
            }

            if (!ac.equals(linkInfo.linkFullFilePath))
                throwException("Mismatching link case");

            /*
             * Detect implied file extension from actual file content 
             */
            String contentExtension = null;
            if (fileContentExtensionMap.containsKey(linkInfo.linkFullFilePath.toLowerCase()))
            {
                contentExtension = fileContentExtensionMap.get(linkInfo.linkFullFilePath.toLowerCase());
            }
            else
            {
                byte[] content = Util.readFileAsByteArray(linkInfo.linkFullFilePath);
                contentExtension = FileTypeDetector.fileExtensionFromActualFileContent(content);
                fileContentExtensionMap.put(linkInfo.linkFullFilePath.toLowerCase(), contentExtension);
            }

            if (contentExtension == null || contentExtension.length() == 0)
                continue;

            if (tag.equalsIgnoreCase("img") || tag.equalsIgnoreCase("a"))
            {
                switch (contentExtension.toLowerCase())
                {
                /*
                 * When downloading IMG link, or other link, server responded with HTML or XHTML,
                 * likely because image was not availale, and displaying HTML page with 404 or other error.
                 * Do not change extension in this case.
                 */
                case "html":
                case "xhtml":
                    continue;

                default:
                    break;
                }
            }

            /*
             * Get extension from file name 
             */
            File fp = new File(linkInfo.linkFullFilePath);
            String fnExt = getFileExtension(fp.getName());
            if (fnExt != null && (fnExt.length() == 0 || fnExt.length() > 4))
                fnExt = null;

            /*
             * If it is not one of common media extensions, disregard it  
             */
            if (fnExt != null && !FileTypeDetector.commonExtensions().contains(fnExt.toLowerCase()))
                fnExt = null;

            /*
             * If it is equivalent to detected file content, do not make any change  
             */
            if (fnExt != null && FileTypeDetector.isEquivalentExtensions(fnExt, contentExtension))
                continue;

            /*
             * Strip file extension if existed and append new extension
             */
            String newLinkFullFilePath = linkInfo.linkFullFilePath;
            String newref = href;

            if (fnExt != null)
            {
                String tail = "." + fnExt;
                tail = tail.toLowerCase();

                if (!newLinkFullFilePath.toLowerCase().endsWith(tail))
                    throwException("Internal error check");
                if (!newref.toLowerCase().endsWith(tail))
                    throwException("Internal error check");

                newLinkFullFilePath = newLinkFullFilePath.substring(0, newLinkFullFilePath.length() - tail.length());
                newref = newref.substring(0, newref.length() - tail.length());
            }

            newLinkFullFilePath += "." + contentExtension;
            newref += "." + contentExtension;

            if (fnExt != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("CHANGING EXTENSION [%s] from  %s" + nl, Config.User, linkInfo.linkFullFilePath));
                sb.append(String.format("                    %s    to  %s" + nl, spaces(Config.User), newLinkFullFilePath));
                trace(sb.toString());
                txLog.writeLine(sb.toString());
            }

            /*
             * Check for conflicts
             */
            if (new File(newLinkFullFilePath).exists())
            {
                throwException("Target file already exists on disk: " + newLinkFullFilePath);
            }

            if (relpath2entry.containsKey(href2rel(newref, fullHtmlFilePath).toLowerCase()))
                throwException("Target file already exists in repository map: " + href2rel(newref, fullHtmlFilePath));

            /*
             * Log
             */
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Renaming [%s] from  %s" + nl, Config.User, linkInfo.linkFullFilePath));
            sb.append(String.format("          %s    to  %s" + nl, spaces(Config.User), newLinkFullFilePath));
            sb.append(String.format("  relink  %s  from  %s" + nl, spaces(Config.User), href_original));
            sb.append(String.format("          %s    to  %s" + nl, spaces(Config.User), newref));
            sb.append(String.format("          %s    in  %s" + nl, spaces(Config.User), fullHtmlFilePath));

            trace(sb.toString());
            txLog.writeLine(safety, sb.toString());

            if (!DryRun)
            {
                // ### test !!!!!
                boolean replaceExisting = false;
                Util.renameFile(linkInfo.linkFullFilePath, newLinkFullFilePath, replaceExisting);
                txLog.writeLine(safety, "Renamed OK");
            }
            else
            {
                txLog.writeLine(safety, "Dry-run fake renamed OK");
            }

            /*
             * Fix link
             */
            updateLinkAttribute(n, attr, newref);
            updated = true;

            String rel = href2rel(href, fullHtmlFilePath);
            String rel_original = href2rel(href_original, fullHtmlFilePath);
            String newrel = href2rel(newref, fullHtmlFilePath);

            alreadyRenamed.put(rel.toLowerCase(), newrel);
            alreadyRenamed.put(rel_original.toLowerCase(), newrel);

            /*
             * Fix map
             */
            changeLinksMap(href_original, newref, true, fullHtmlFilePath);
            changeLinksMap(href, newref, false, fullHtmlFilePath);
        }

        return updated;
    }

    private boolean handleAlreadyRenamed(String href, String href_original, String fullHtmlFilePath, Node n, String tag,
            String attr) throws Exception
    {
        String rel = href2rel(href, fullHtmlFilePath);
        String rel_original = href2rel(href_original, fullHtmlFilePath);

        String newrel = this.alreadyRenamed.get(rel.toLowerCase());
        if (newrel == null)
            newrel = this.alreadyRenamed.get(rel_original.toLowerCase());

        if (newrel != null)
        {
            String newref = rel2href(newrel, fullHtmlFilePath);
            updateLinkAttribute(n, attr, newref);

            txLog.writeLine(Safety.UNSAFE, changeMessage(tag, attr, href_original, newref));
            trace(changeMessage(tag, attr, href_original, newref));

            changeLinksMap(href_original, newref, false, fullHtmlFilePath);
            changeLinksMap(href, newref, false, fullHtmlFilePath);

            return true;
        }

        return false;
    }

    private void changeLinksMap(String href, String newref, boolean required, String fullHtmlFilePath) throws Exception
    {
        String rel = href2rel(href, fullHtmlFilePath);
        String newrel = href2rel(newref, fullHtmlFilePath);

        List<LinkMapEntry> list = relpath2entry.get(rel.toLowerCase());
        if (list == null || list.size() == 0)
        {
            if (required)
            {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("LinkMap [%s] is MISSING existing link file  %s" + nl, Config.User, rel));
                sb.append(String.format("         %s               being renamed to  %s" + nl, spaces(Config.User), newrel));
                trace(sb.toString());

                if (DryRun || Config.True)
                {
                    Util.noop();
                }
                else
                {
                    throwException("Old link is missing in the map");
                }
            }
        }
        else
        {
            for (LinkMapEntry e : list)
            {
                if (e.value.equals(rel))
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("Changing [%s] LinksDir map  %s" + nl, Config.User, e.value));
                    sb.append(String.format("          %s            to  %s" + nl, spaces(Config.User), newrel));
                    trace(sb.toString());

                    e.value = newrel;
                    updatedMap = true;
                }
                else if (e.value.equalsIgnoreCase(rel))
                {
                    throwException("Misimatching LinkDir case");
                }
                else
                {
                    // already changed
                    Util.noop();
                }
            }
        }
    }

    private String changeMessage(String tag, String attr, String href_original, String newref)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Changing [%s] HTML %s.%s from  %s" + nl, Config.User, tag, attr, href_original));
        sb.append(String.format("          %s       %s %s   to  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr), newref));
        return sb.toString();
    }

    /* ===================================================================================================== */
    
    private void fillFileContentExtensionMap() throws Exception
    {
        List<String> files = new ArrayList<>(file_lc2ac.values());
        Collections.sort(files);

        FiletypeParallelWorkContext ppwc = new FiletypeParallelWorkContext(files, TikaThreads);

        try
        {
            for (FiletypeWorkContext wcx : ppwc)
            {
                Exception ex = wcx.getException();
                if (ex != null)
                    throwException("While processing " + wcx.fullFilePath, ex);

                Objects.requireNonNull(wcx.fullFilePath, "fullFilePath is null");

                if (wcx.contentExtension == null)
                {
                    // Objects.requireNonNull(wcx.contentExtension, "extension is null");
                    Util.noop();
                }
                
                fileContentExtensionMap.put(wcx.fullFilePath.toLowerCase(), wcx.contentExtension);
            }
        }
        finally
        {
            ppwc.close();
        }

        Util.noop();
    }

    /* ===================================================================================================== */
    
    private String getFileExtension(String fn)
    {
        int dotIndex = fn.lastIndexOf('.');
        // no extension or dot is at the end
        if (dotIndex == -1 || dotIndex == fn.length() - 1)
            return null;
        else
            return fn.substring(dotIndex + 1);
    }

    private void trace(String msg) throws Exception
    {
        errorMessageLog.add(msg);
        // Util.err(msg);
        traceWriter.write(msg + nl);
        traceWriter.flush();
    }

    @SuppressWarnings("unused")
    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }

    private void throwException(String msg, Exception ex) throws Exception
    {
        throw new Exception(msg, ex);
    }
}