package my.LJExport.maintenance.handlers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import my.LJExport.runtime.file.ServerContent.Decision;
import my.LJExport.runtime.file.FilePath;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.ShouldDownload;
import my.LJExport.runtime.links.util.InferOriginalUrl;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeWorkContext;
import my.LJExport.runtime.url.UrlUtil;

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
 * 
 * After dry run look for:
 * 
 *     - renames to x-guid
 *     
 *     - MISSING existing link file
 *      
 *     - CHANGING EXTENSION ...
 *     - Renaming ...
 *     - Redirecting ...
 *     - Changing ... HTML
 *     
 *     - Error-response ... link file 
 *     - Deleting LinkMap ... entry for error-response URL
 *     
 *     - Changing ... LinksDir map 
 * 
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
    private Set<String> deleteLinkFiles = new HashSet<>();
    private Set<String> addedFiles = new HashSet<>();
    private List<KVEntry> renameHistory = null;

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
        deleteLinkFiles = new HashSet<>();
        addedFiles = new HashSet<>();
        renameHistory = null;

        txLog.writeLine("Starting user " + Config.User);
        super.beginUser();
        build_lc2ac();
        updatedMap = false;
        loadLinkMapFile();
        prefillFileContentExtensionMap();
        loadFileContentTypeInformation();
        loadRenameHistory();

        trace("");
        trace("");
        trace("================================= Beginning user " + Config.User);
        trace("");
        trace("");
    }

    @Override
    protected void endUser() throws Exception
    {
        linkMapEntries = applyScheduledDeletesFromLinkMap(linkMapEntries);

        if (updatedMap && !DryRun)
        {
            String mapFilePath = this.linksDir + File.separator + LinkDownloader.LinkMapFileName;

            txLog.writeLine("updating links map " + mapFilePath);
            String content = FileBackedMap.recomposeMapFile(linkMapEntries);
            Util.writeToFileVerySafe(mapFilePath, content);
            txLog.writeLine("updated OK");
        }

        if (renameHistory.size() != 0 && !DryRun)
        {
            KVFile kvfile = new KVFile(this.linksDir + File.separator + "rename-history.txt");
            txLog.writeLine("updating rename history " + kvfile.getPath());
            kvfile.save(renameHistory);
            txLog.writeLine("updated OK");
        }

        applyScheduledLinkFileDeletes();

        if (!DryRun)
        {
            deleteEmptyFolders(this.dir_lc2ac.values());
        }

        txLog.writeLine("Completed user " + Config.User);

        trace("");
        trace("");
        trace("================================= Completed user " + Config.User);
        trace("");
        trace("");

        super.endUser();
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

    private void loadLinkMapFile() throws Exception
    {
        String mapFilePath = this.linksDir + File.separator + LinkDownloader.LinkMapFileName;
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

    private void loadRenameHistory() throws Exception
    {
        KVFile kvfile = new KVFile(this.linksDir + File.separator + "rename-history.txt");
        if (kvfile.exists())
            renameHistory = kvfile.load(true);
        else
            renameHistory = new ArrayList<>();
    }

    /* ===================================================================================================== */

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

        boolean updated = false;

        updated |= process(fullHtmlFilePath, relativeFilePath, parser, pageFlat, "a", "href", false);
        updated |= process(fullHtmlFilePath, relativeFilePath, parser, pageFlat, "img", "src", true);

        if (updated && !DryRun)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullHtmlFilePath, html);
        }
    }

    private boolean process(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat, String tag, String attr, boolean image) throws Exception
    {
        boolean updated = false;

        for (Node n : JSOUP.findElements(pageFlat, tag))
        {
            updated |= process(n, fullHtmlFilePath, relativeFilePath, parser, pageFlat, tag, attr, image);
        }

        return updated;
    }

    private boolean process(Node n, String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat, String tag, String attr, boolean image) throws Exception
    {
        String href = getLinkAttribute(n, attr);
        String anchor = Util.getAnchor(href);
        href = Util.stripAnchor(href);

        String href_original = getLinkOriginalAttribute(n, "original-" + attr);
        href_original = Util.stripAnchor(href_original);

        if (href == null || !isLinksRepositoryReference(fullHtmlFilePath, href))
            return false;

        if (isArchiveOrg())
        {
            /* ignore bad links due to former bug in archive loader */
            if (href.startsWith("../") && href.endsWith("../links/null"))
                return false;
        }

        if (handleAlreadyRenamed(href, href_original, fullHtmlFilePath, n, tag, attr, anchor))
            return true;

        LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
        if (linkInfo == null)
            return false;

        String ac = file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
        if (ac == null)
        {
            String msg = String.format("Link file/dir [%s] is not present in the repository, href=[%s], filepath=[%s]",
                    Config.User, href, linkInfo.linkFullFilePath);

            boolean allow = Config.User.equals("d_olshansky") && href.contains("../links/imgprx.livejournal.net/");

            if (allow)
            {
                trace(msg);
                return false;
            }
            else if (DryRun)
            {
                trace(msg);
                return false;
            }
            else
            {
                throwException(msg);
            }
        }

        if (!ac.equals(linkInfo.linkFullFilePath))
            throwException("Mismatching link case");
        
        // if (ac.contains("\\a_bugaev\\links\\userpic.livejournal.com\\13279792\\2465292"))
        // {
        //    Util.noop();
        // }

        /*
         * Get extension from file name 
         */
        String fnExt = LinkFilepath.getMediaFileExtension(linkInfo.linkFullFilePath);

        /*
         * If it is not one of common media extensions, disregard it  
         */
        if (fnExt != null && !FileTypeDetector.commonExtensions().contains(fnExt.toLowerCase()))
            fnExt = null;

        /*
         * "image" is true if file is referenced from img.src
         * or if it is referenced from a.href with image extension
         */
        if (FileTypeDetector.isImageExtension(fnExt))
            image = true;

        /*
         * Detect implied file extension from actual file content 
         */
        String headerExtension = null;
        String contentExtension = null;
        String finalExtension = null;
        boolean reject = false;

        /*
         * First check overrides 
         */
        String relpath = abs2rel(linkInfo.linkFullFilePath);
        String contentType = fileContentTypeInformation.contentTypeForLcUnixRelpath(relpath);
        if (contentType != null)
            contentExtension = headerExtension = FileTypeDetector.fileExtensionFromMimeType(contentType);

        if (contentExtension == null || contentExtension.length() == 0)
        {
            if (fileContentExtensionMap.containsKey(linkInfo.linkFullFilePath.toLowerCase()))
            {
                contentExtension = fileContentExtensionMap.get(linkInfo.linkFullFilePath.toLowerCase());
            }
            else
            {
                byte[] content = Util.readFileAsByteArray(linkInfo.linkFullFilePath);
                contentExtension = FileTypeDetector.fileExtensionFromActualFileContent(content, fnExt);
                fileContentExtensionMap.put(linkInfo.linkFullFilePath.toLowerCase(), contentExtension);
            }
        }

        Decision decision = serverAcceptedContent(href_original, linkInfo.linkFullFilePath,
                contentExtension,
                headerExtension,
                fnExt);

        if (decision.isAccept())
        {
            // for lib.ru and www.lib.ru and some others: aaa.txt -> aaa.txt.html
            finalExtension = decision.finalExtension;
            if (finalExtension == null)
                finalExtension = contentExtension;
        }
        else if (decision.isReject())
        {
            reject = true;
        }
        else // decision.isNeutral()
        {
            /*
             * Could not determine file content type, leave file as is
             */
            if (contentExtension == null || contentExtension.length() == 0)
                return false;

            /*
             * If extension is equivalent to detected file content, do not make any change  
             */
            if (fnExt != null && FileTypeDetector.isEquivalentExtensions(fnExt, contentExtension))
                return false;

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

            switch (contentExtension.toLowerCase())
            {
            /*
             * When downloading IMG link, or other link such as to PDF, server responded with HTML or XHTML or PHP or TXT,
             * likely because image or PDF was not available, and displaying HTML page with 404 or other error.
             * Requests for actual TXT files have already been handled by isEquivalentExtensions above.
             * Requests to servers such as lib.ru that may return HTML for TXT have already been handled by serverAcceptedContent/Accept above. 
             * 
             * Do not change extension in this case, and revert to original URL if available.
             */
            case "html":
            case "xhtml":
            case "php":
            case "txt":
                reject = true;
                break;

            default:
                break;
            }

            String originalUrl = InferOriginalUrl.infer(href_original, relpath);
            if (!ShouldDownload.shouldDownload(image, originalUrl, false))
                reject = true;

            finalExtension = contentExtension;
        }

        if (reject)
        {
            return scheduleOriginalUrl(fullHtmlFilePath, linkInfo.linkFullFilePath, n, tag, attr);
        }

        if (fnExt != null && FileTypeDetector.isEquivalentExtensions(fnExt, finalExtension))
            return false;

        /*
         * Strip file extension if existed (or better leave it in place) and append new extension
         */
        String newLinkFullFilePath = linkInfo.linkFullFilePath;
        String newref = href;

        if (fnExt != null && Util.False)
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

        if (finalExtension == null || finalExtension.trim().length() == 0)
            throwException("Unexpected: null or empty final extension");

        newLinkFullFilePath += "." + finalExtension;
        newref += "." + finalExtension;

        if (fnExt != null || Util.True)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("CHANGING EXTENSION [%s] from  %s" + nl, Config.User, linkInfo.linkFullFilePath));
            sb.append(String.format("                    %s    to  %s" + nl, spaces(Config.User), newLinkFullFilePath));
            trace(sb.toString());
            txLog.writeLine(safety, sb.toString());
        }

        newref = renameFile(linkInfo.linkFullFilePath, newLinkFullFilePath, fullHtmlFilePath,
                href, href_original, finalExtension);
        if (newref == null)
            return false;

        /*
         * Fix link
         */
        updateLinkAttribute(n, attr, Util.withAnchor(newref, anchor));

        String rel = href2rel(href, fullHtmlFilePath);
        String newrel = href2rel(newref, fullHtmlFilePath);
        alreadyRenamed.put(rel.toLowerCase(), newrel);

        /*
         * Fix map
         */
        changeLinksMap(href, newref, false, fullHtmlFilePath);

        return true;
    }

    private String renameFile(String oldLinkFullFilePath,
            String newLinkFullFilePath,
            String fullHtmlFilePath,
            String href,
            String href_original,
            String finalExtension) throws Exception
    {
        /*
         * Check for conflicts with existing files and resolve them
         */
        String newref = tryRenameFile(oldLinkFullFilePath, newLinkFullFilePath, fullHtmlFilePath, href, href_original);
        if (newref != null)
            return newref;

        String tryLinkFullFilePath = oldLinkFullFilePath + "." + finalExtension;
        newref = tryRenameFile(oldLinkFullFilePath, tryLinkFullFilePath, fullHtmlFilePath, href, href_original);
        if (newref != null)
            return newref;

        File fp = new File(oldLinkFullFilePath).getParentFile();
        fp = new File(fp, "x-" + Util.uuid() + "." + finalExtension);
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

        if (addedFiles.contains(newLinkFullFilePath.toLowerCase()))
            return null;

        if (!new File(newLinkFullFilePath).exists())
        {
            if (renameFileActual(oldLinkFullFilePath, newLinkFullFilePath, fullHtmlFilePath, href, href_original, newref))
            {
                addedFiles.add(newLinkFullFilePath.toLowerCase());
                return newref;
            }
            else
            {
                return null;
            }
        }

        if (isSameFileContent(oldLinkFullFilePath, newLinkFullFilePath))
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Redirecting [%s] from  %s" + nl, Config.User, oldLinkFullFilePath));
            sb.append(String.format("          %s       to  %s" + nl, spaces(Config.User), newLinkFullFilePath));
            sb.append(String.format("  relink  %s     from  %s" + nl, spaces(Config.User), href));
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

        renameHistory.add(new KVEntry(abs2rel(oldLinkFullFilePath), abs2rel(newLinkFullFilePath)));

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

    /* ===================================================================================================== */

    private boolean handleAlreadyRenamed(String href, String href_original, String fullHtmlFilePath, Node n, String tag,
            String attr, String anchor) throws Exception
    {
        /*
         * href_original is true external url, not a relatve link
         */
        if (Util.True)
            href_original = null;

        String rel = href2rel(href, fullHtmlFilePath);
        String rel_original = href_original == null ? null : href2rel(href_original, fullHtmlFilePath);

        String newrel = this.alreadyRenamed.get(rel.toLowerCase());
        if (newrel == null && rel_original != null)
            newrel = this.alreadyRenamed.get(rel_original.toLowerCase());

        if (newrel != null)
        {
            String newref = rel2href(newrel, fullHtmlFilePath);

            updateLinkAttribute(n, attr, Util.withAnchor(newref, anchor));

            txLog.writeLine(Safety.UNSAFE, changeMessage(tag, attr, href_original, href, newref));
            trace(changeMessage(tag, attr, href_original, href, newref));

            // map was already adjusted
            // changeLinksMap(href, newref, false, fullHtmlFilePath);

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

    private String changeMessage(String tag, String attr, String href_original, String href, String newref)
    {
        if (href_original == null || href_original.trim().length() == 0)
            href_original = "(no original url)";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Changing [%s] HTML %s.%s from  %s" + nl, Config.User, tag, attr, href));
        sb.append(String.format("          %s       %s %s   to  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr), newref));
        sb.append(String.format("          %s       %s %s  for  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr),
                href_original));
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

        String relpath = abs2rel(linkFullFilePath);

        String originalUrl = JSOUP.getAttribute(n, "original-" + attr);
        originalUrl = UrlUtil.decodeHtmlAttrLink(originalUrl);
        originalUrl = InferOriginalUrl.infer(originalUrl, relpath);

        if (originalUrl != null && originalUrl.trim().length() != 0)
        {
            JSOUP.updateAttribute(n, attr, UrlUtil.encodeUrlForHtmlAttr(originalUrl, true));

            sb.append(String.format("Error-response [%s] link file  %s" + nl, Config.User, linkFullFilePath));
            sb.append(String.format("                %s         in  %s" + nl, spaces(Config.User), fullHtmlFilePath));
            sb.append(String.format("                %s  revert to  %s" + nl, spaces(Config.User), originalUrl));

            schedDeleteLinkFile(linkFullFilePath);
        }
        else
        {
            sb.append(String.format("Error-response [%s] link file  %s" + nl, Config.User, linkFullFilePath));
            sb.append(String.format("                %s         in  %s" + nl, spaces(Config.User), fullHtmlFilePath));
            sb.append(String.format("                %s      leave  as-is" + nl, spaces(Config.User)));
        }

        trace(sb.toString());
        txLog.writeLine(safety, sb.toString());

        return true;
    }

    private void schedDeleteLinkFile(String linkFullFilePath) throws Exception
    {
        String rel = this.abs2rel(linkFullFilePath);
        deleteLinkFiles.add(rel.toLowerCase());
    }

    private List<LinkMapEntry> applyScheduledDeletesFromLinkMap(List<LinkMapEntry> entries) throws Exception
    {
        List<LinkMapEntry> list = new ArrayList<>();

        for (LinkMapEntry e : entries)
        {
            if (deleteLinkFiles.contains(e.value.toLowerCase()))
            {
                StringBuilder sb = new StringBuilder();

                sb.append(String.format("Deleting LinkMap [%s] entry for error-response URL  %s" + nl,
                        Config.User, e.key));

                sb.append(String.format("                  %s                          file  %s" + nl,
                        spaces(Config.User), e.value));

                trace(sb.toString());
                txLog.writeLine(safety, sb.toString());

                // do not add to output list

                updatedMap = true;
            }
            else
            {
                list.add(e);
            }
        }

        return list;
    }

    private void applyScheduledLinkFileDeletes() throws Exception
    {
        // delete error-response files on disk
        for (String fn : deleteLinkFiles)
        {
            fn = rel2abs(fn);

            // for debugger
            final String fn0 = fn;
            Util.unused(fn0);

            fn = file_lc2ac.get(fn.toLowerCase());

            if (fn == null)
            {
                throwException("missing lc2ac mapping for file " + fn);
            }
            else if (DryRun)
            {
                trace("Fake-deleting file " + fn);
            }
            else
            {
                trace("Deleting file " + fn);
                txLog.writeLine(safety, "Deleting file " + fn);
                Files.deleteIfExists(Paths.get(fn));
            }
        }
    }

    /* ===================================================================================================== */

    @Override
    protected void trace(String msg) throws Exception
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