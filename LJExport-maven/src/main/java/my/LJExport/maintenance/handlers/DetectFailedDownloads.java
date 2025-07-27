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
import my.LJExport.runtime.lj.LJUtil;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeWorkContext;
import my.LJExport.runtime.url.UrlConsolidator;

/*
 * Detect linked files pointed by IMG.SRC and A.HREF that contain HTML/XHTML/PHP/TXT content -- 
 * error pages saying that files was unavailable.
 * List them in failed-link-downloads.txt. 
 */
public class DetectFailedDownloads extends MaintenanceHandler
{
    private static boolean DryRun = true; // ###
    // private static final Safety safety = Safety.UNSAFE;

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
            prefillFileContentExtensionMap();
            loadFileContentTypeInformation();

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

            for (FailedLinkInfo fli : failedLinkInfo.values())
            {
                fli.prepare(relpath2entry);

                if (fli.urls.size() == 1)
                {
                    String url = fli.urls.get(0);

                    if (fli.image)
                        url = "image:" + url;
                    else
                        url = "document:" + url;

                    list.add(new KVEntry(url, fli.relpath));
                }
            }

            if (!DryRun)
            {
                new KVFile(linksDirSep + "failed-link-downloads.txt").save(list);
                trace("Stored failed-link-downloads.txt for user " + Config.User);
                Util.out("Stored failed-link-downloads.txt for user " + Config.User);
            }

            if (needUpdateMissingOriginalLinks)
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
                updated |= processUpdateMissingOriginalLinks(fullHtmlFilePath, n, tag, attr);
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
            byte[] content = Util.readFileAsByteArray(linkInfo.linkFullFilePath);
            contentExtension = FileTypeDetector.fileExtensionFromActualFileContent(content, fnExt);
            fileContentExtensionMap.put(linkInfo.linkFullFilePath.toLowerCase(), contentExtension);
        }
        
        if (contentExtension == null || contentExtension.length() == 0)
        {
            String relpath = this.abs2rel(linkInfo.linkFullFilePath);
            String contentType = this.fileContentTypeInformation.contentTypeForLcUnixRelpath(relpath);
            contentExtension = FileTypeDetector.fileExtensionFromMimeType(contentType);
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
            url = Util.stripAnchor(url);

            try
            {
                url = LJUtil.decodeImgPrxStLink(url);
            }
            catch (Exception ex)
            {
                Util.noop();
            }

            url = MiscUrls.unwrapImagesGoogleCom(url);

            return url;
        }

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
        String href_original = getLinkOriginalAttribute(n, "original-" + attr);
        if (href_original != null)
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
        JSOUP.setAttribute(n, "original-" + attr, url);
        return true;
    }

    /* ===================================================================================================== */

    private void trace(String msg) throws Exception
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