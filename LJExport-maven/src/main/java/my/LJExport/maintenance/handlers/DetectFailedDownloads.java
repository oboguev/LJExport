package my.LJExport.maintenance.handlers;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
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
import my.LJExport.runtime.ContentProvider;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.file.ServerContent;
import my.LJExport.runtime.file.ServerContent.Decision;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.MiscUrls;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.util.InferOriginalUrl;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeWorkContext;
import my.LJExport.runtime.url.AwayLink;
import my.LJExport.runtime.url.UrlConsolidator;
import my.LJExport.runtime.url.UrlUtil;

/*
 * Detect linked files pointed by IMG.SRC and A.HREF that contain HTML/XHTML/PHP/TXT content -- 
 * error pages saying that files was unavailable.
 * List them in failed-link-downloads.txt. 
 */
public class DetectFailedDownloads extends MaintenanceHandler
{
    private static boolean DryRun = false; // ###

    static enum Phase
    {
        ScanLinks, UpdateMissingOriginalLinks
    }

    private Phase phase = Phase.ScanLinks;
    private boolean needUpdateMissingOriginalLinks = false;

    public DetectFailedDownloads() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        /*
         * FixFileExtensions log is huge and will cause Eclipse console overflow
         */
        printErrorMessageLog = false;

        Util.out(">>> Detect failed downloads of linked files");
        super.beginUsers("Detect failed downloads of linked files");
        txLog.writeLine(String.format("Executing DetectFailedDownloads in %s RUN mode", DryRun ? "DRY" : "WET"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private Map<String, String> file_lc2ac = new HashMap<>();
    private Map<String, String> fileContentExtensionMap = new HashMap<>();
    private Map<String, FailedLinkInfo> failedLinkInfo = new HashMap<>();
    private List<LinkMapEntry> linkMapEntries;
    private Map<String, List<LinkMapEntry>> relpath2entry;
    private int stageFileCount;

    @Override
    protected void beginUser() throws Exception
    {
        if (phase == Phase.ScanLinks)
        {
            /* clear for new user */
            file_lc2ac = new HashMap<>();
            fileContentExtensionMap = new HashMap<>();
            failedLinkInfo = new HashMap<>();
            linkMapEntries = null;
            relpath2entry = null;

            txLog.writeLine("Starting user " + Config.User);
            super.beginUser();
            build_lc2ac();
            loadLinkMapFile();
            loadFileContentTypeInformation();
            prefillFileContentExtensionMap();

            stageFileCount = this.getStageProcessedFileCount();

            trace("");
            trace("");
            trace("================================= Beginning user " + Config.User);
            trace("");
            trace("");
        }
        else if (phase == Phase.UpdateMissingOriginalLinks)
        {
            this.setStageProcessedFileCount(stageFileCount);

            trace("");
            trace("================================= Updating HTML files for user " + Config.User);
            trace("");

            if (DryRun)
                Util.out("Updating HTML files for user " + Config.User + " ... [DRY RUN]");
            else
                Util.out("Updating HTML files for user " + Config.User + " ...");
        }
    }

    @Override
    protected void endUser() throws Exception
    {
        if (phase == Phase.ScanLinks)
        {
            List<KVEntry> list = new ArrayList<>();
            boolean haveDeletes = false;

            for (FailedLinkInfo fli : failedLinkInfo.values())
            {
                fli.prepare(relpath2entry);

                if (fli.urls.size() == 1)
                {
                    String url = fli.urls.get(0);

                    if (!fli.image && !LinkDownloader.shouldDownload(url, true))
                    {
                        fli.delete = true;
                        haveDeletes = true;
                    }
                    else
                    {
                        if (fli.image)
                            url = "image:" + url;
                        else
                            url = "document:" + url;

                        list.add(new KVEntry(url, fli.relpath));
                    }
                }
            }

            if (!DryRun)
            {
                KVFile kvfile = new KVFile(linksDirSep + "failed-link-downloads.txt");

                if (list.size() == 0)
                {
                    kvfile.delete();
                    trace("No failed downloads, removed failed-link-downloads.txt for user " + Config.User);
                    Util.out("No failed downloads, removed failed-link-downloads.txt for user " + Config.User);
                }
                else
                {
                    kvfile.save(list);

                    String info = " with " + list.size() + " file";
                    if (list.size() > 1)
                        info += "s";

                    trace("Stored failed-link-downloads.txt for user " + Config.User + info);
                    Util.out("Stored failed-link-downloads.txt for user " + Config.User + info);
                }
            }

            if (needUpdateMissingOriginalLinks || haveDeletes)
            {
                this.repeatUser();
                phase = Phase.UpdateMissingOriginalLinks;
            }
            else
            {
                printCompletedUser();
                super.endUser();
            }
        }
        else if (phase == Phase.UpdateMissingOriginalLinks)
        {
            executePendingDeletes();
            printCompletedUser();
            super.endUser();
        }
    }

    private void printCompletedUser() throws Exception
    {
        trace("");
        trace("");
        trace("================================= Completed user " + Config.User);
        trace("");
        trace("");
    }

    private void build_lc2ac() throws Exception
    {
        for (String fp : Util.enumerateFiles(linksDir, null))
        {
            if (isLinksRootFileRelativePathSyntax(fp))
                continue;

            fp = linksDir + File.separator + fp;
            file_lc2ac.put(fp.toLowerCase(), fp);
        }
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

    /* ===================================================================================================== */

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

        boolean updated = false;

        updated |= process(fullHtmlFilePath, relativeFilePath, parser, pageFlat, "a", "href");
        updated |= process(fullHtmlFilePath, relativeFilePath, parser, pageFlat, "img", "src");

        if (updated && !DryRun && phase == Phase.UpdateMissingOriginalLinks)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullHtmlFilePath, html);
        }
    }

    private boolean process(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat, String tag, String attr) throws Exception
    {
        boolean updated = false;

        if (phase == Phase.ScanLinks)
        {
            for (Node n : JSOUP.findElements(pageFlat, tag))
                processScanLinks(fullHtmlFilePath, n, tag, attr);
        }
        else if (phase == Phase.UpdateMissingOriginalLinks)
        {
            for (Node n : JSOUP.findElements(pageFlat, tag))
            {
                updated |= processUpdateMissingOriginalLinks(fullHtmlFilePath, n, tag, attr);
                updated |= processMarkedDeletes(fullHtmlFilePath, n, tag, attr);
            }
        }

        return updated;
    }

    private void processScanLinks(String fullHtmlFilePath, Node n, String tag, String attr) throws Exception
    {
        String href = getLinkAttribute(n, attr);
        String href_original = getLinkOriginalAttribute(n, "original-" + attr);

        if (href == null || !isLinksRepositoryReference(fullHtmlFilePath, href))
            return;

        if (isArchiveOrg())
        {
            /* ignore bad links due to former bug in archive loader */
            if (href.startsWith("../") && href.endsWith("../links/null"))
                return;
        }

        LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
        if (linkInfo == null)
            return;

        String ac = file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
        if (ac == null)
        {
            String msg = String.format("Link file/dir [%s] is not present in the repository map, href=[%s], filepath=[%s]",
                    Config.User, href, linkInfo.linkFullFilePath);

            boolean allow = Config.User.equals("d_olshansky") && href.contains("../links/imgprx.livejournal.net/");

            if (DryRun || allow)
            {
                trace(msg);
                return;
            }
            else
            {
                throwException(msg);
            }
        }

        if (!ac.equals(linkInfo.linkFullFilePath))
            throwException("Mismatching link case");

        /*
         * Get extension from file name 
         */
        String fnExt = LinkFilepath.getMediaFileExtension(linkInfo.linkFullFilePath);

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
            String relpath = this.abs2rel(linkInfo.linkFullFilePath);
            String contentType = this.fileContentTypeInformation.contentTypeForLcUnixRelpath(relpath);
            contentExtension = FileTypeDetector.fileExtensionFromMimeType(contentType);

            if (contentExtension == null || contentExtension.length() == 0)
            {
                byte[] content = Util.readFileAsByteArray(linkInfo.linkFullFilePath);
                contentExtension = FileTypeDetector.fileExtensionFromActualFileContent(content, fnExt);
            }

            fileContentExtensionMap.put(linkInfo.linkFullFilePath.toLowerCase(), contentExtension);
        }

        /*
         * If it is not one of common media extensions, disregard it  
         */
        if (fnExt != null && !FileTypeDetector.commonExtensions().contains(fnExt.toLowerCase()))
            fnExt = null;

        /*
         * If it is equivalent to detected file content, do not make any change  
         */
        if (fnExt != null && FileTypeDetector.isEquivalentExtensions(fnExt, contentExtension))
            return;

        Decision decision = serverAcceptedContent(href_original, linkInfo.linkFullFilePath, contentExtension, fnExt);

        if (decision.isAccept())
            return;

        boolean reject = decision.isReject();

        if (!reject)
        {
            /*
             * Accept one kind of image for another image kind (e.g. if x.jpg actually contains png, a common case)
             */
            if (tag.equalsIgnoreCase("img") || FileTypeDetector.isImageExtension(fnExt))
            {
                if (FileTypeDetector.isImageExtension(contentExtension))
                    return;
            }
        }

        if (decision.isNeutral())
        {
            if (contentExtension == null || contentExtension.length() == 0)
            {
                reject = true;
            }
            else
            {
                switch (contentExtension.toLowerCase())
                {
                /*
                 * When downloading IMG link, or other link, server responded with HTML or XHTML or PHP or TXT,
                 * likely because image was not available, and displaying HTML page with 404 or other error.
                 * Requests for actual TXT files have already been handled by isEquivalentExtensions above.
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
            }
        }

        if (reject)
        {
            String relpath = this.abs2rel(linkInfo.linkFullFilePath);
            FailedLinkInfo fli = failedLinkInfo.get(relpath.toLowerCase());
            if (fli == null)
                failedLinkInfo.put(relpath.toLowerCase(), fli = new FailedLinkInfo(relpath));

            if (href_original != null && href_original.trim().length() != 0 && Util.isAbsoluteURL(href_original))
                fli.addUrl(href_original);

            if (tag.equalsIgnoreCase("img"))
                fli.image = true;

            if (href_original == null)
                needUpdateMissingOriginalLinks = true;
        }
    }

    private Decision serverAcceptedContent(String href_original, String linkFullFilePath, String contentExtension, String fnExt)
            throws Exception
    {
        String href = null;

        if (href_original != null && href_original.trim().length() != 0 && Util.isAbsoluteURL(href_original))
            href = href_original;

        if (href == null)
        {
            String relpath = this.abs2rel(linkFullFilePath);
            href = InferOriginalUrl.infer(relpath);
        }

        if (href == null)
            return ServerContent.DecisionNeutral;
        else
            return ServerContent.acceptContent(href, contentExtension, fnExt, new ContentProvider(linkFullFilePath), null);
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
                String relpath = this.abs2rel(wcx.fullFilePath);
                String contentType = this.fileContentTypeInformation.contentTypeForLcUnixRelpath(relpath);
                String contentExtension = FileTypeDetector.fileExtensionFromMimeType(contentType);

                if (contentExtension != null && contentExtension.length() != 0)
                {
                    fileContentExtensionMap.put(wcx.fullFilePath.toLowerCase(), contentExtension);
                }
                else
                {
                    Exception ex = wcx.getException();
                    if (ex != null)
                        throwException("While processing " + wcx.fullFilePath, ex);

                    Objects.requireNonNull(wcx.fullFilePath, "fullFilePath is null");

                    if (wcx.contentExtension == null && !wcx.empty && !wcx.zeroes && wcx.size > 10)
                    {
                        Util.err("Unrecognized file content: " + wcx.fullFilePath);
                        Util.noop();
                    }

                    fileContentExtensionMap.put(wcx.fullFilePath.toLowerCase(), wcx.contentExtension);
                }
            }
        }
        finally
        {
            ppwc.close();
        }

        Util.noop();
    }

    /* ===================================================================================================== */

    public static class FailedLinkInfo
    {
        public FailedLinkInfo(String relpath)
        {
            this.relpath = relpath;
        }

        public final String relpath;
        public final List<String> urls = new ArrayList<>();
        public boolean image = false;
        public boolean delete = false;

        public void prepare(Map<String, List<LinkMapEntry>> relpath2entry) throws Exception
        {
            List<LinkMapEntry> entries = relpath2entry.get(relpath.toLowerCase());
            if (entries != null)
            {
                for (LinkMapEntry e : entries)
                    addUrl(e.key);
            }

            MiscUrls.uniqYimgCom(urls);

            if (urls.size() > 1)
            {
                weedOutImgPrx();

                String url = UrlConsolidator.consolidateUrlVariants(urls, false);
                if (url == null)
                    url = UrlConsolidator.consolidateUrlVariants(urls, true);
                if (url != null)
                {
                    try
                    {
                        new URI(url);
                    }
                    catch (Exception ex)
                    {
                        // malformed url
                        throwException("Internal error: consolidateUrlVariants returned malformed url " + url);
                    }

                    urls.clear();
                    addUrl(url);
                }
            }

            if (urls.size() > 1)
            {
                String msg = "Multiple URLs for link file: " + relpath;
                Util.err(msg);
                for (String s : urls)
                    Util.err("    " + s);
                Util.err("------------------------------");

                if (Util.True && DryRun)
                {
                    // ###
                    String url = urls.get(0);
                    urls.clear();
                    urls.add(url);
                }
                else
                {
                    throwException(msg);
                }
            }

            if (urls.size() == 1)
                return;

            /* infer URL from relpath */
            String url = InferOriginalUrl.infer(relpath);
            if (url != null)
                addUrl(url);
        }

        private void addUrl(String url) throws Exception
        {
            url = unwrap(url);
            if (!urls.contains(url))
                urls.add(url);
        }

        private String unwrap(String url) throws Exception
        {
            for (;;)
            {
                String prev = url;
                url = unwrapPass(url);
                if (url.equals(prev))
                    return url;
            }
        }

        private String unwrapPass(String url) throws Exception
        {
            String xurl;

            /* strip anchor */
            xurl = Util.stripAnchor(url);
            if (!xurl.equals(url))
                return xurl;

            /* decode wrapper */
            xurl = AwayLink.unwrapAwayLinkDecoded(url);
            if (!xurl.equals(url))
                return xurl;

            return url;
        }

        /*
         * If there is/are non-imgprx URL or URLs, remove all imgprx URLs.
         * Otherwise If there is/are St-format imgprx URLs, remove all non-St imgprx urls.
         * 
         * Priority trumping order:
         *   - non-imgprx
         *   - imgprx St
         *   - imgprx non-St
         */
        private void weedOutImgPrx()
        {
            Set<String> xs_imgprx_st = new HashSet<>();
            Set<String> xs_imgprx = new HashSet<>();
            boolean hasOther = false;

            for (String s : urls)
            {
                if (isImgPrxSt(s))
                    xs_imgprx_st.add(s);
                else if (isImgPrx(s))
                    xs_imgprx.add(s);
                else
                    hasOther = true;
            }

            if (hasOther)
            {
                removeUrls(xs_imgprx_st);
                removeUrls(xs_imgprx);
            }
            else if (xs_imgprx_st.size() != 0)
            {
                removeUrls(xs_imgprx);
            }
        }

        private boolean isImgPrxSt(String url)
        {
            String lc = url.toLowerCase();
            return lc.startsWith("https://imgprx.livejournal.net/st/") ||
                    lc.startsWith("http://imgprx.livejournal.net/st/") ||
                    lc.startsWith("imgprx.livejournal.net/st/");
        }

        private boolean isImgPrx(String url)
        {
            String lc = url.toLowerCase();
            return lc.startsWith("https://imgprx.livejournal.net/") ||
                    lc.startsWith("http://imgprx.livejournal.net/") ||
                    lc.startsWith("imgprx.livejournal.net/");
        }

        private void removeUrls(Collection<String> xs)
        {
            for (String s : xs)
                urls.remove(s);
        }
    }

    /* ===================================================================================================== */

    private boolean processUpdateMissingOriginalLinks(String fullHtmlFilePath, Node n, String tag, String attr) throws Exception
    {
        if (null != JSOUP.getAttribute(n, "original-" + attr))
            return false;

        String href = getLinkAttribute(n, attr);
        if (href == null || !isLinksRepositoryReference(fullHtmlFilePath, href))
            return false;

        if (isArchiveOrg())
        {
            /* ignore bad links due to former bug in archive loader */
            if (href.startsWith("../") && href.endsWith("../links/null"))
                return false;
        }

        LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
        if (linkInfo == null)
            return false;

        String ac = file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
        if (ac == null)
            return false;

        if (!ac.equals(linkInfo.linkFullFilePath))
            throwException("Mismatching link case");

        String relpath = this.abs2rel(linkInfo.linkFullFilePath);
        FailedLinkInfo fli = failedLinkInfo.get(relpath.toLowerCase());
        if (fli == null || fli.urls.size() != 1)
            return false;

        String url = fli.urls.get(0);
        JSOUP.setAttribute(n, "original-" + attr, UrlUtil.encodeUrlForHtmlAttr(url));
        return true;
    }

    /* ===================================================================================================== */

    private boolean processMarkedDeletes(String fullHtmlFilePath, Node n, String tag, String attr) throws Exception
    {
        String href = getLinkAttribute(n, attr);
        if (href == null || !isLinksRepositoryReference(fullHtmlFilePath, href))
            return false;

        if (isArchiveOrg())
        {
            /* ignore bad links due to former bug in archive loader */
            if (href.startsWith("../") && href.endsWith("../links/null"))
                return false;
        }

        LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
        if (linkInfo == null)
            return false;

        String ac = file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
        if (ac == null)
            return false;

        if (!ac.equals(linkInfo.linkFullFilePath))
            throwException("Mismatching link case");

        String relpath = this.abs2rel(linkInfo.linkFullFilePath);
        FailedLinkInfo fli = failedLinkInfo.get(relpath.toLowerCase());
        if (fli == null || !fli.delete)
            return false;

        if (tag.equalsIgnoreCase("img"))
            throwException("Unexpected delete for a file referenced by IMG.SRC");

        String original_href_encoded = JSOUP.getAttribute(n, "original-" + attr);
        if (original_href_encoded != null && original_href_encoded.length() != 0)
        {
            original_href_encoded = AwayLink.unwrapAwayLinkEncoded(original_href_encoded);
            JSOUP.updateAttribute(n, attr, original_href_encoded);
        }
        else
        {
            String url = fli.urls.get(0);
            url = UrlUtil.encodeUrlForHtmlAttr(url);
            JSOUP.updateAttribute(n, attr, url);
        }

        return true;
    }

    /* ===================================================================================================== */

    private void executePendingDeletes() throws Exception
    {
        /*
         * Build a list of files to delete
         */
        Map<String, String> deleteRelPaths = new HashMap<>();
        for (FailedLinkInfo fli : failedLinkInfo.values())
        {
            if (fli.delete)
            {
                deleteRelPaths.put(fli.relpath.toLowerCase(), fli.relpath);
            }
        }
        if (deleteRelPaths.size() == 0)
            return;

        /*
         * Remove entries from links map
         */
        String mapFilePath = this.linksDir + File.separator + LinkDownloader.LinkMapFileName;
        List<LinkMapEntry> list = FileBackedMap.readMapFile(mapFilePath);
        List<LinkMapEntry> xlist = new ArrayList<>();
        int nremoved = 0;
        for (LinkMapEntry e : list)
        {
            // add to xlist to keep the entry
            if (!deleteRelPaths.containsKey(e.value.toLowerCase()))
            {
                xlist.add(e);
            }
            else
            {
                nremoved++;
            }
        }

        if (xlist.size() != list.size())
        {
            String msg = String.format("Removed %d link map %s for %s",
                    nremoved,
                    Util.plural(nremoved, "entry", "entries"),
                    Util.nplural(deleteRelPaths.size(), "file", "files"));

            if (DryRun)
            {
                msg += nl + "DRY RUN: not updating link map file " + mapFilePath;
                this.supertrace(msg);
            }
            else
            {
                msg += nl + "Updating link map file " + mapFilePath;
                supertrace(msg);
                txLog.writeLineSafe(msg);
                String content = FileBackedMap.recomposeMapFile(xlist);
                Util.writeToFileVerySafe(mapFilePath, content);
            }
        }

        /*
         * Delete files
         */
        for (String rel : deleteRelPaths.values())
        {
            String abs = this.rel2abs(rel);
            File fp = new File(abs).getCanonicalFile();
            if (DryRun)
            {
                String msg = "DRY RUN: not deleting file " + fp.getCanonicalPath();
                this.supertrace(msg);
            }
            else
            {
                String msg = "Deleting file " + fp.getCanonicalPath();
                this.supertrace(msg);
                txLog.writeLineSafe(msg);
                if (fp.exists())
                    fp.delete();
            }
        }

        /*
         * Delete empty directories
         */
        Map<String, String> dir_lc2ac = new HashMap<>();

        for (String fp : Util.enumerateDirectories(linksDir))
        {
            fp = linksDir + File.separator + fp;
            dir_lc2ac.put(fp.toLowerCase(), fp);
        }

        supertrace("  >>> Deleting empty directories for user " + Config.User);
        deleteEmptyFolders(dir_lc2ac.values());
        supertrace("  >>> Deleted empty directories for user " + Config.User);
    }

    /* ===================================================================================================== */

    @Override
    protected void trace(String msg) throws Exception
    {
        // errorMessageLog.add(msg);
        // Util.err(msg);
        traceWriter.write(msg + nl);
        traceWriter.flush();
    }

    @SuppressWarnings("unused")
    private static void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }

    private static void throwException(String msg, Exception ex) throws Exception
    {
        throw new Exception(msg, ex);
    }
}