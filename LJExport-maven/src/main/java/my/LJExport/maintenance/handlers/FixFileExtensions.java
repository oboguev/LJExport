package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.TxLog.Safety;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.file.FilePath;
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
 *     and AFTER FixLongPaths.
 *     and AFTER FixDirectoryLinks.
 */
public class FixFileExtensions extends MaintenanceHandler
{
    private static boolean DryRun = true; // ###
    private static final Safety safety = Safety.UNSAFE;

    public FixFileExtensions() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        /*
         * FixFileExtensions log is huge and will cause Eclipse console overflow
         */
        printErrorMessageLog = false;

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
    private Set<String> deleteLinkMapEntriesFor = new HashSet<>();

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
        deleteLinkMapEntriesFor = new HashSet<>();

        txLog.writeLine("Starting user " + Config.User);
        super.beginUser();
        build_lc2ac();
        updatedMap = false;
        loadLinkMapFile();
        prefillFileContentExtensionMap();

        trace("");
        trace("");
        trace("================================= Beginning user " + Config.User);
        trace("");
        trace("");
    }

    @Override
    protected void endUser() throws Exception
    {
        linkMapEntries = applyScheduledDeletes(linkMapEntries);

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

            if (isArchiveOrg())
            {
                /* ignore bad links due to former bug in archive loader */
                if (href.startsWith("../") && href.endsWith("../links/null"))
                    continue;
            }

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

            /*
             * Get extension from file name 
             */
            File fp = new File(linkInfo.linkFullFilePath);
            String fnExt = getFileExtension(fp.getName());
            if (fnExt != null && (fnExt.length() == 0 || fnExt.length() > 4)) // ###
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
             *  The following transitions were detected in scan:
             *
             *     doc     txt
             *     gif     txt
             *     jpeg    txt
             *     jpg     txt
             *     pdf     txt
             *     png     txt
             *     zip     txt
             *     
             *     png     avif
             *     
             *     gif     bmp
             *     jpg     bmp
             *     
             *     jpeg    gif
             *     jpg     gif
             *     png     gif
             *     
             *     bmp     jpg
             *     gif     jpg
             *     pdf     jpg
             *     png     jpg
             *     tif     jpg
             *     webp    jpg
             *     
             *     doc     odt
             *     
             *     gif     pdf
             *     jpg     pdf
             *     
             *     jpg     php
             *     png     php
             *     
             *     gif     png
             *     jpeg    png
             *     jpg     png
             *     
             *     mp4     qt
             *     
             *     doc     rtf
             *     
             *     jpg     svg
             *     png     svg
             *     
             *     gif     webp
             *     jpeg    webp
             *     jpg     webp
             *     png     webp
             *     
             * and also many transitions -> html, xhtml    
             */

            if (tag.equalsIgnoreCase("img") || tag.equalsIgnoreCase("a"))
            {
                switch (contentExtension.toLowerCase())
                {
                /*
                 * When downloading IMG link, or other link, server responded with HTML or XHTML or PHP or TXT,
                 * likely because image was not available, and displaying HTML page with 404 or other error.
                 * Requests for actual TXT files have already been handled by isEquivalentExtensions above.
                 * 
                 * Do not change extension in this case, and revert to original URL if available.
                 */
                case "html":
                case "xhtml":
                case "php":
                case "txt":
                    updated = scheduleOriginalUrl(fullHtmlFilePath, linkInfo.linkFullFilePath, n, tag, attr);
                    continue;

                default:
                    break;
                }
            }

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

            newref = renameFile(linkInfo.linkFullFilePath, newLinkFullFilePath, fullHtmlFilePath,
                    href, href_original, contentExtension);
            if (newref == null)
                continue;

            newLinkFullFilePath = this.href2abs(newref, fullHtmlFilePath);

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

    private String renameFile(String oldLinkFullFilePath,
            String newLinkFullFilePath,
            String fullHtmlFilePath,
            String href,
            String href_original,
            String contentExtension) throws Exception
    {
        /*
         * Check for conflicts with existing files and resolve them
         */
        String newref = tryRenameFile(oldLinkFullFilePath, newLinkFullFilePath, fullHtmlFilePath, href, href_original);
        if (newref != null)
            return newref;

        String tryLinkFullFilePath = oldLinkFullFilePath + "." + contentExtension;
        newref = tryRenameFile(oldLinkFullFilePath, tryLinkFullFilePath, fullHtmlFilePath, href, href_original);
        if (newref != null)
            return newref;

        File fp = new File(oldLinkFullFilePath).getParentFile();
        fp = new File(fp, "x-" + Util.uuid() + "." + contentExtension);
        fp = FilePath.canonicalFile(fp);
        tryLinkFullFilePath = fp.getCanonicalPath();
        newref = tryRenameFile(oldLinkFullFilePath, tryLinkFullFilePath, fullHtmlFilePath, href, href_original);
        if (newref == null)
            throwException("Unable to resolve file collision");

        return newref;
    }

    private String tryRenameFile(String oldLinkFullFilePath,
            String newLinkFullFilePath,
            String fullHtmlFilePath,
            String href,
            String href_original) throws Exception
    {
        String newref = abs2href(newLinkFullFilePath, fullHtmlFilePath);

        if (!new File(newLinkFullFilePath).exists())
        {
            if (!renameFileActual(oldLinkFullFilePath, newLinkFullFilePath, fullHtmlFilePath, href, href_original, newref))
                return null;
            return newref;
        }
        else if (isSameFileContent(oldLinkFullFilePath, newLinkFullFilePath))
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Redirecting [%s] from  %s" + nl, Config.User, oldLinkFullFilePath));
            sb.append(String.format("          %s       to  %s" + nl, spaces(Config.User), newLinkFullFilePath));
            sb.append(String.format("  relink  %s     from  %s" + nl, spaces(Config.User), href_original));
            sb.append(String.format("          %s       to  %s" + nl, spaces(Config.User), newref));
            sb.append(String.format("          %s       in  %s" + nl, spaces(Config.User), fullHtmlFilePath));

            trace(sb.toString());
            txLog.writeLine(safety, sb.toString());

            return newref;
        }
        else
        {
            return null;
        }
    }

    private boolean renameFileActual(String oldLinkFullFilePath,
            String newLinkFullFilePath,
            String fullHtmlFilePath,
            String href,
            String href_original,
            String newref) throws Exception
    {
        if (relpath2entry.containsKey(href2rel(newref, fullHtmlFilePath).toLowerCase()))
            throwException("Target file already exists in repository map: " + href2rel(newref, fullHtmlFilePath));

        /*
         * Log
         */
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Renaming [%s] from  %s" + nl, Config.User, oldLinkFullFilePath));
        sb.append(String.format("          %s    to  %s" + nl, spaces(Config.User), newLinkFullFilePath));
        sb.append(String.format("  relink  %s  from  %s" + nl, spaces(Config.User), href_original));
        sb.append(String.format("          %s    to  %s" + nl, spaces(Config.User), newref));
        sb.append(String.format("          %s    in  %s" + nl, spaces(Config.User), fullHtmlFilePath));

        trace(sb.toString());
        txLog.writeLine(safety, sb.toString());

        if (!DryRun)
        {
            boolean done = false;
            String cause = null;

            try
            {
                boolean replaceExisting = false;
                Util.renameFile(oldLinkFullFilePath, newLinkFullFilePath, replaceExisting);
                done = true;
            }
            catch (Exception ex)
            {
                cause = ex.getLocalizedMessage();
            }

            if (done)
            {
                txLog.writeLine(safety, "Renamed OK");
            }
            else
            {
                txLog.writeLine(safety, "Renaming FAILED, cause: " + cause);
            }

            return done;
        }
        else
        {
            txLog.writeLine(safety, "Dry-run fake renamed OK");
            return true;
        }
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

    private void prefillFileContentExtensionMap() throws Exception
    {
        List<String> files = new ArrayList<>(file_lc2ac.values());
        Collections.sort(files);

        FiletypeParallelWorkContext ppwc = new FiletypeParallelWorkContext(files, FileTypeDetectionThreads);

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

    private boolean scheduleOriginalUrl(String fullHtmlFilePath, String linkFullFilePath, Node n, String tag, String attr)
            throws Exception
    {
        StringBuilder sb = new StringBuilder();

        String original_attr_name = "original-" + attr;

        String original_attr_value = JSOUP.getAttribute(n, original_attr_name);
        if (original_attr_value != null)
        {
            JSOUP.updateAttribute(n, attr, original_attr_value);

            sb.append(String.format("Error-response [%s] link file  %s" + nl, Config.User, linkFullFilePath));
            sb.append(String.format("                %s         in  %s" + nl, spaces(Config.User), fullHtmlFilePath));
            sb.append(String.format("                %s  revert to  %s" + nl, spaces(Config.User), original_attr_value));
        }
        else
        {
            sb.append(String.format("Error-response [%s] link file  %s" + nl, Config.User, linkFullFilePath));
            sb.append(String.format("                %s         in  %s" + nl, spaces(Config.User), fullHtmlFilePath));
            sb.append(String.format("                %s      leave  as-is" + nl, spaces(Config.User)));
        }

        schedDeleteMapEntryFor(linkFullFilePath);

        trace(sb.toString());
        txLog.writeLine(safety, sb.toString());

        return true;
    }

    private void schedDeleteMapEntryFor(String linkFullFilePath) throws Exception
    {
        String rel = this.abs2rel(linkFullFilePath);
        deleteLinkMapEntriesFor.add(rel.toLowerCase());
    }

    private List<LinkMapEntry> applyScheduledDeletes(List<LinkMapEntry> entries) throws Exception
    {
        List<LinkMapEntry> list = new ArrayList<>();

        for (LinkMapEntry e : entries)
        {
            if (deleteLinkMapEntriesFor.contains(e.value.toLowerCase()))
            {
                StringBuilder sb = new StringBuilder();
                
                sb.append(String.format("Deleting LinkMap [%s] entry for error-response URL  %s" + nl,
                        Config.User, e.key));
                
                sb.append(String.format("                  %s                          file  %s" + nl,
                        spaces(Config.User), e.value));

                trace(sb.toString());
                txLog.writeLine(safety, sb.toString());

                updatedMap = true;
            }
            else
            {
                list.add(e);
            }
        }

        return list;
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

    private boolean isSameFileContent(String fp1, String fp2) throws Exception
    {
        byte[] ba1 = Util.readFileAsByteArray(fp1);
        byte[] ba2 = Util.readFileAsByteArray(fp2);
        return Arrays.equals(ba1, ba2);
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