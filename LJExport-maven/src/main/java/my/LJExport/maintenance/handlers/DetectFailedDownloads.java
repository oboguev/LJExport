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
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.InferOriginalURL;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.filetype.FiletypeWorkContext;

public class DetectFailedDownloads extends MaintenanceHandler
{
    private static boolean DryRun = false; // ###
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

    @Override
    protected void beginUser() throws Exception
    {
        /* clear for new user */
        file_lc2ac = new HashMap<>();
        fileContentExtensionMap = new HashMap<>();
        failedLinkInfo = new HashMap<>();

        txLog.writeLine("Starting user " + Config.User);
        super.beginUser();
        build_lc2ac();
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
            fli.prepare();
        
            if (fli.urls.size() == 1)
                list.add(new KVEntry(fli.urls.get(0), fli.relpath));
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

            if (tag.equalsIgnoreCase("img") || tag.equalsIgnoreCase("a"))
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
                    String relpath = this.abs2rel(linkInfo.linkFullFilePath);
                    FailedLinkInfo fli = failedLinkInfo.get(relpath.toLowerCase());
                    if (fli == null)
                        failedLinkInfo.put(relpath.toLowerCase(), fli = new FailedLinkInfo(relpath));
                    if (href_original != null && href_original.trim().length() != 0 && Util.isAbsoluteURL(href_original))
                    {
                        if (!fli.urls.contains(href_original))
                            fli.urls.add(href_original);
                    }
                    break;

                default:
                    break;
                }
            }
        }

        return updated;
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

        public void prepare() throws Exception
        {
            if (urls.size() > 1)
            {
                String msg = "Multiple URLs for link file: " + relpath;
                Util.err(msg);
                throwException(msg);
                Util.noop();
            }

            if (urls.size() == 1)
            {
                if (urls.get(0).contains("%"))
                {
                    throwException("Review link URL: " + urls.get(0));
                }

                if (Util.False && relpath.contains("%"))
                {
                    Util.out(urls.get(0));
                    Util.out("        " + relpath);
                    Util.out("");
                }
                return;
            }

            /* infer URL from relpath */
            String url = InferOriginalURL.infer(relpath);
            if (url != null)
                urls.add(url);
        }
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