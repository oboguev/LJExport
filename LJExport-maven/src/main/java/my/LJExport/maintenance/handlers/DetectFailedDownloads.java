package my.LJExport.maintenance.handlers;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.util.InferOriginalUrl;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeWorkContext;
import my.LJExport.runtime.url.UrlUtil;

/*
 * Detect linked files pointed by IMG.SRC and A.HREF that contain HTML/XHTML/PHP/TXT content -- 
 * error pages saying that files was unavailable.
 * List them in failed-link-downloads.txt. 
 */
public class DetectFailedDownloads extends MaintenanceHandler
{
    private static boolean DryRun = true; // ###
    // private static final Safety safety = Safety.UNSAFE;

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

    @Override
    protected void beginUser() throws Exception
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

        trace("");
        trace("");
        trace("================================= Beginning user " + Config.User);
        trace("");
        trace("");
    }

    @Override
    protected void endUser() throws Exception
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
            new KVFile(linkDirSep + "failed-link-downloads.txt").save(list);
            trace("Stored failed-link-downloads.txt for user " + Config.User);
            Util.out("Stored failed-link-downloads.txt for user " + Config.User);
        }

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

        if (updated && !DryRun && Util.False)
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
            String href_original = getLinkAttribute(n, "original-" + attr);

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
                String msg = String.format("Link file/dir [%s] is not present in the repository map, href=[%s], filepath=[%s]",
                        Config.User, href, linkInfo.linkFullFilePath);

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
            String fnExt = LinkFilepath.getMediaFileExtension(linkInfo.linkFullFilePath);

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

            Decision decision = serverAcceptedContent(href_original, linkInfo.linkFullFilePath, contentExtension, fnExt);

            if (decision.isAccept())
                continue;

            boolean reject = decision.isReject();

            if (decision.isNeutral())
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
            }
        }

        return updated;
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

            if (urls.size() > 1)
            {
                String url = UrlUtil.consolidateUrlVariants(urls, false);
                if (url == null)
                    url = UrlUtil.consolidateUrlVariants(urls, true);
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
                throwException(msg);
                Util.noop();
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
            if (!urls.contains(url))
                urls.add(url);
        }
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