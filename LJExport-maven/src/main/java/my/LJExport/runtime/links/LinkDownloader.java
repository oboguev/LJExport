package my.LJExport.runtime.links;

import java.io.File;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.conn.HttpHostConnectException;

import my.LJExport.Config;
import my.LJExport.runtime.ContentProvider;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Util.UnableCreateDirectoryException;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FilePath;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.file.ServerContent;
import my.LJExport.runtime.file.ServerContent.Decision;
import my.LJExport.runtime.http.NetErrors;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.util.DownloadSource;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.synch.NamedLocks;
import my.LJExport.runtime.url.AwayLink;
import my.LJExport.runtime.url.UrlUtil;
import my.WebArchiveOrg.ArchiveOrgUrl;

public class LinkDownloader
{
    /*
     * References to linked files from
     *    - post pages
     *    - monthly pages
     */
    public static final String LINK_REFERENCE_PREFIX_PAGES = "../../../links/";
    public static final String LINK_REFERENCE_PREFIX_MONTHLY_PAGES = "../../links/";
    public static final String LINK_REFERENCE_PREFIX_PROFILE = "../links/";
    public static final String LINK_REFERENCE_PREFIX_PROFILE_DOWN_1 = "../../links/";
    public static final String LINK_REFERENCE_PREFIX_PROFILE_DOWN_2 = "../../../links/";

    private FileBackedMap href2file = new FileBackedMap();
    private Set<String> failedSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private String linksDir;
    private final NamedLocks urlLocks = new NamedLocks();

    private boolean useSmartDownloader = false;
    private boolean alwaysAcceptContent = false;

    public static final String LinkMapFileName = "map-href-file.txt";

    public synchronized void init(String linksDir) throws Exception
    {
        close();
        href2file.close();
        href2file.init(linksDir + File.separator + LinkMapFileName);
        failedSet.clear();
        this.linksDir = linksDir;
    }

    public synchronized void close() throws Exception
    {
        linksDir = null;
        href2file.close();
        failedSet.clear();
    }

    public boolean isInitialized()
    {
        return linksDir != null;
    }

    public String getLinksDir()
    {
        return linksDir;
    }

    public void setAlwaysAcceptContent(boolean alwaysAcceptContent)
    {
        this.alwaysAcceptContent = alwaysAcceptContent;
    }

    public void setUseSmartDownloader()
    {
        useSmartDownloader = true;
    }

    public boolean isOnlineOnly()
    {
        return !useSmartDownloader;
    }

    public String rel2abs(String rel) throws Exception
    {
        return linksDir + File.separator + rel.replace("/", File.separator);
    }

    public String abs2rel(String abs) throws Exception
    {
        String prefix = linksDir + File.separator;
        if (!abs.startsWith(prefix))
            throw new Exception("File path is not within a repository: " + abs);
        String rel = Util.stripStart(abs, prefix);
        return rel.replace(File.separator, "/");
    }

    public String download(boolean image, String href, String referer, String linkReferencePrefix)
    {
        return download(image, href, href, referer, linkReferencePrefix);
    }

    /*
     * download_href is used for actual downloading
     * name_href is used for storing/naming the resource in links directory
     * 
     * Usually they are the same.
     * However when downloading happens from archive.org,
     * download_href is archived url such as https://web.archive.org/web/20160528141306/http:/nationalism.org/library/science/politics/golosov/golosov-cpcs-2002.pdf
     * and name_href is http://nationalism.org/library/science/politics/golosov/golosov-cpcs-2002.pdf
     */
    public String download(boolean image, String name_href, String download_href, String referer, String linkReferencePrefix)
    {
        return download(image, name_href, download_href, referer, linkReferencePrefix, null);
    }

    public String adviseFileName(String name_href) throws Exception
    {
        String href = name_href;
        String href_noanchor = Util.stripAnchor(href);
        String fn = LinkFilepath.buildFilePath(linksDir, href_noanchor);
        String afn = href2file.getAnyUrlProtocol(href_noanchor);
        if (afn != null)
            fn = afn;
        return fn;
    }

    public String download(boolean image, String name_href, String download_href, String referer,
            String linkReferencePrefix, DownloadSource downloadSource)
    {
        AtomicReference<Web.Response> response = new AtomicReference<>(null);
        AtomicReference<String> filename = new AtomicReference<>(null);
        String name_href_noanchor = null;
        String download_href_noanchor = null;

        String threadName = Thread.currentThread().getName();
        if (threadName == null)
            threadName = "(unnamed)";

        try
        {
            // normalize name
            name_href = UrlUtil.stripDefaultPort(name_href);
            name_href = UrlUtil.stripTrailingSlash(name_href);

            // avoid HTTPS certificate problem
            download_href = https2http(download_href, "l-stat.livejournal.net");
            download_href = https2http(download_href, "l-userpic.livejournal.com");

            // LJ responds with HTTP code 412 if picture is marked 18+ and request is not HTTPS
            download_href = http2https(download_href, "pics.livejournal.com");
            download_href = http2https(download_href, "ic.pics.livejournal.com");

            // map washingtonpost image resizer url
            download_href = map_washpost_imr(download_href);

            if (ArchiveOrgUrl.isArchiveOrgSimpleTimestampUrl(download_href))
                download_href = ArchiveOrgUrl.toDirectDownloadUrl(download_href, false, image);

            name_href_noanchor = Util.stripAnchor(name_href);
            download_href_noanchor = Util.stripAnchor(download_href);

            String name_href_noanchor_away = Util.stripAnchor(AwayLink.unwrapAwayLinkDecoded(name_href_noanchor));
            String download_href_noanchor_away = Util.stripAnchor(AwayLink.unwrapAwayLinkDecoded(download_href_noanchor));

            if (failedSet.contains(download_href_noanchor))
                return null;

            filename.set(LinkFilepath.buildFilePath(linksDir, name_href_noanchor));

            // Main.out(">>> Downloading: " + href + " -> " + filename.get());

            // final String final_name_href = name_href;
            final String final_name_href_noanchor = name_href_noanchor;
            // final String final_download_href = download_href;
            final String final_download_href_noanchor = download_href_noanchor;
            final String final_threadName = threadName;

            Thread.currentThread().setName(threadName + " downloading " + name_href + " namelock wait");

            urlLocks.interlock(name_href_noanchor_away.toLowerCase(), () ->
            {
                while_interlocked(
                        response,
                        image,
                        final_name_href_noanchor,
                        final_download_href_noanchor,
                        referer,
                        name_href_noanchor_away,
                        download_href_noanchor_away,
                        filename,
                        downloadSource,
                        final_threadName);
            });

            Thread.currentThread().setName(threadName);

            /* server replied with error page */
            if (filename.get() == null)
                return null;

            String newref = abs2rel(filename.get());
            newref = linkReferencePrefix + LinkFilepath.encodePathComponents(newref);
            return newref;
        }
        catch (Exception ex)
        {
            String host = extractHostSafe(download_href);
            Web.Response r = response.get();

            if (ex instanceof AlreadyFailedException)
            {
                // ignore
            }
            else if (!NetErrors.isNetworkException(ex))
            {
                // error is not network-related, such as NullPointerException
                throw new RuntimeException(ex.getLocalizedMessage(), ex);
            }
            else
            {
                examineException(host, r, ex);
            }

            // Main.err("Unable to download external link " + download_href, ex);
            if (download_href_noanchor != null)
                failedSet.add(download_href_noanchor);

            return null;
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }
    }

    private void while_interlocked(
            AtomicReference<Web.Response> response,
            boolean image,
            String name_href_noanchor,
            String download_href_noanchor,
            String referer,
            String name_href_noanchor_away,
            String download_href_noanchor_away,
            AtomicReference<String> filename,
            DownloadSource downloadSource,
            String final_threadName)
            throws Exception
    {
        if (failedSet.contains(download_href_noanchor))
            throw new AlreadyFailedException();

        Thread.currentThread().setName(final_threadName + " downloading " + download_href_noanchor + " prepare");

        String actual_filename = filename.get();
        if (alreadyHaveFileForHref(null, name_href_noanchor, filename) ||
            alreadyHaveFileForHref(null, download_href_noanchor, filename) ||
            alreadyHaveFileForHref(actual_filename, name_href_noanchor, filename))
        {
            return;
        }

        /*
         * Redirect to unwrapped links
         */
        if (this.useSmartDownloader)
        {
            name_href_noanchor = name_href_noanchor_away;
            // leave download_href_noanchor as-is
        }
        else
        {
            name_href_noanchor = name_href_noanchor_away;
            download_href_noanchor = download_href_noanchor_away;
        }

        if (alreadyHaveFileForHref(null, name_href_noanchor, filename) ||
            alreadyHaveFileForHref(null, download_href_noanchor, filename))
        {
            return;
        }

        filename.set(actual_filename = LinkFilepath.buildFilePath(linksDir, name_href_noanchor));
        if (alreadyHaveFileForHref(actual_filename, name_href_noanchor, filename))
            return;

        /*
         * Prepare HTTP headers
         */
        Map<String, String> headers = new HashMap<>();

        if (referer != null && referer.length() != 0)
            headers.put("Referer", referer);

        if (image)
        {
            headers.put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            addImageHeaders(headers);
        }
        else
        {
            headers.put("Accept", Config.UserAgentAccept_Download);
            addDocumentHeaders(headers);
        }

        Web.Response r = null;

        /*
         * Try to download from an override source
         */
        if (downloadSource != null)
        {
            byte[] binaryBody = downloadSource.load(name_href_noanchor, abs2rel(actual_filename));
            if (binaryBody != null)
            {
                r = new Web.Response();
                r.code = 200;
                r.binaryBody = binaryBody;
            }
        }

        /*
         * Actual web load
         */
        if (r == null)
        {
            try
            {
                Thread.currentThread().setName(final_threadName + " downloading " + download_href_noanchor);
                r = Web.get(download_href_noanchor, Web.BINARY | Web.PROGRESS, headers, (code) ->
                {
                    return code >= 200 && code <= 299 && code != 204;
                });
            }
            catch (Exception ex)
            {
                if (download_href_noanchor != null)
                    failedSet.add(download_href_noanchor);
                throw ex;
            }

            if (r.code < 200 || r.code >= 300 || r.code == 204)
            {
                response.set(r);
                if (download_href_noanchor != null)
                    failedSet.add(download_href_noanchor);
                throw new HttpException("HTTP code " + r.code + ", reason: " + r.reason);
            }
        }

        try
        {
            /*
             * Adjust filename extension based on file actual content and Content-Type header.
             * Will return null if detected error response page such as HTML or PHP.
             */
            actual_filename = adjustExtension(image, name_href_noanchor, actual_filename, r);
            filename.set(actual_filename);

            /*
             * Store file. Take care of collisions with existing file.
             * May update filename.
             */
            if (actual_filename != null)
            {
                /*
                 * If path cannot be created, redirect to catch-all directory
                 */
                try
                {
                    File f = new File(actual_filename).getCanonicalFile();
                    Util.mkdir(f.getAbsoluteFile().getParent());
                }
                catch (UnableCreateDirectoryException dex)
                {
                    actual_filename = LinkFilepath.fallbackFilepath(linksDir, name_href_noanchor,
                            actual_filename);
                    filename.set(actual_filename);
                    File f = new File(actual_filename).getCanonicalFile();
                    Util.mkdir(f.getAbsoluteFile().getParent());
                }

                Web.Response final_r = r;
                final String final_name_href_noanchor = name_href_noanchor;
                Util.NamedFileLocks.interlock(actual_filename.toLowerCase(), () ->
                {
                    storeFile(filename, final_r, final_name_href_noanchor);
                });
            }
        }
        catch (Exception ex)
        {
            Util.noop();
            if (download_href_noanchor != null)
                failedSet.add(download_href_noanchor);
            throw ex;
        }
    }

    private boolean alreadyHaveFileForHref(String actual_filename, String href, AtomicReference<String> filename) throws Exception
    {
        if (actual_filename == null)
        {
            actual_filename = href2file.getAnyUrlProtocol(href);
            if (actual_filename == null)
                return false;
        }

        File f = null;
        try
        {
            f = new File(actual_filename).getCanonicalFile();
        }
        catch (Exception ex)
        {
            throw ex;
        }

        /*
         * If file already exists
         */
        if (f.exists() && !f.isDirectory())
        {
            actual_filename = FilePath.getFilePathActualCase(actual_filename);
            filename.set(actual_filename);
            synchronized (href2file)
            {
                if (null == href2file.getAnyUrlProtocol(href))
                    href2file.put(href, actual_filename);
            }

            return true;
        }
        else
        {
            return false;
        }
    }

    /* ======================================================================== */

    private void storeFile(AtomicReference<String> filename, Web.Response r, String href_noanchor) throws Exception
    {
        boolean dowrite = true;

        String actual_filename = filename.get();
        File fp = new File(actual_filename).getCanonicalFile();

        if (fp.exists())
        {
            if (fp.isDirectory() || fp.isFile() && !isSameContent(fp.getCanonicalPath(), r.binaryBody))
            {
                // change actual_filename to old-path\x-uuid.ext
                String ext = LinkFilepath.getMediaFileExtension(fp.getName());
                String fn = "x-" + Util.uuid();
                if (ext != null)
                    fn += "." + ext;
                fp = new File(fp.getParentFile(), fn).getCanonicalFile();
                actual_filename = fp.getCanonicalPath();
            }
            else
            {
                // file already exists and is a regular file with identical content
                actual_filename = FilePath.getFilePathActualCase(actual_filename);
                dowrite = false;
            }
        }

        filename.set(actual_filename);

        if (dowrite)
            Util.writeToFileSafe(actual_filename, r.binaryBody);

        synchronized (href2file)
        {
            if (null == href2file.getAnyUrlProtocol(href_noanchor))
                href2file.put(href_noanchor, actual_filename);
        }
    }

    private boolean isSameContent(String filepath, byte[] content) throws Exception
    {
        byte[] ba = Util.readFileAsByteArray(filepath);
        return Arrays.equals(ba, content);
    }

    /* ======================================================================== */

    static void examineException(String host, Web.Response r, Exception ex)
    {
        if (host != null && r != null && r.code != 204 && r.code != 404)
        {
            if (host.contains("imgprx.livejournal.net"))
            {
                Util.noop();
            }

            if (host.contains("l-stat.livejournal.net"))
            {
                Util.noop();
            }

            if (host.contains("ic.pics.livejournal.com") && r.code != 403 && r.code != 412 && r.code != 415)
            {
                if (r.code == 504)
                {
                    Util.noop();
                }
                else
                {
                    Util.noop();
                }
            }

            if (host.equals("pics.livejournal.com") && r.code != 415)
            {
                if (r.code == 504)
                {
                    Util.noop();
                }
                else
                {
                    Util.noop();
                }
            }

            if (host.contains("archive.org"))
            {
                Util.noop();
            }
        }
        else if (host != null & r == null)
        {
            if (host.contains("l-stat.livejournal.net"))
            {
                Util.noop();
            }

            if (host.contains("imgprx.livejournal.net"))
            {
                Util.noop();
            }

            if (host.contains("ic.pics.livejournal.com"))
            {
                if (isCircularRedirect(ex))
                {
                    Util.noop();
                }
                else
                {
                    Util.noop();
                }
            }

            if (host.equals("pics.livejournal.com"))
            {
                if (isCircularRedirect(ex))
                {
                    Util.noop();
                }
                else
                {
                    Util.noop();
                }
            }

            if (host.equals("l-userpic.livejournal.com"))
            {
                Util.noop();
            }

            if (host.endsWith(".us.archive.org") && ex instanceof HttpHostConnectException)
            {
                // ignore
            }
            else if (host.endsWith(".us.archive.org") && ex instanceof UnknownHostException)
            {
                // ignore
            }
            else if (host.contains("archive.org"))
            {
                Util.noop();
            }
            else if (ex instanceof SocketTimeoutException)
            {
                Util.noop();
            }
            else if (isCircularRedirect(ex))
            {
                Util.noop();
            }
            else
            {
                Util.noop();
            }
        }
    }

    private static boolean isCircularRedirect(Exception ex)
    {
        return ex instanceof ClientProtocolException && ex.getCause() instanceof CircularRedirectException;
    }

    private String https2http(String href, String host)
    {
        final String key = "https://" + host + "/";
        final String key_change = "http://" + host + "/";
        if (href.startsWith(key))
            href = key_change + href.substring(key.length());
        return href;
    }

    private String http2https(String href, String host)
    {
        final String key = "http://" + host + "/";
        final String key_change = "https://" + host + "/";
        if (href.startsWith(key))
            href = key_change + href.substring(key.length());
        return href;
    }

    private String map_washpost_imr(String href) throws Exception
    {
        final String prefix = "https://img.washingtonpost.com/wp-apps/imrs.php?src=https://img.washingtonpost.com/";
        final String postfix = "&w=1484";

        if (href.startsWith(prefix) && href.endsWith(postfix))
        {
            href = Util.stripTail(href, postfix);
            href = "https://img.washingtonpost.com/" + href.substring(prefix.length());
        }
        return href;
    }

    private static String extractHost(String href) throws Exception
    {
        String host = (new URL(href)).getHost();
        host = host.toLowerCase();
        return host;
    }

    static String extractHostSafe(String href)
    {
        try
        {
            return extractHost(href);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    /* ======================================================================================= */

    /*
     * Adjust filename extension based on file actual content
     * and Content-Type header.
     * 
     * Return null if server replied with error pages such as HTML/XHTML/PHP or TXT.
     */
    private String adjustExtension(boolean image, String href, String filepath, Web.Response r) throws Exception
    {
        String fnExt = LinkFilepath.getMediaFileExtension(filepath);

        String contentExt = FileTypeDetector.fileExtensionFromActualFileContent(r.binaryBody, fnExt);

        String headerExt = null;
        if (r.contentType != null)
            headerExt = FileTypeDetector.fileExtensionFromMimeType(Util.despace(r.contentType).toLowerCase());

        String finalExt = contentExt;
        if (finalExt == null)
            finalExt = headerExt;

        String serverExt = finalExt;

        if (finalExt == null)
            finalExt = fnExt;

        if (finalExt != null && fnExt != null && FileTypeDetector.isEquivalentExtensions(fnExt, finalExt))
            return filepath;

        Decision decision = ServerContent.acceptContent(href, serverExt, headerExt, fnExt, new ContentProvider(r.binaryBody), r);
        if (decision.isReject())
        {
            return null;
        }
        else if (decision.isAccept())
        {
            if (decision.finalExtension == null)
                return filepath;

            finalExt = decision.finalExtension;
            if (fnExt != null && FileTypeDetector.isEquivalentExtensions(fnExt, finalExt))
                return filepath;
        }
        // if (decision.isNeutral())
        else if (serverExt != null)
        {
            switch (serverExt.toLowerCase())
            {
            case "html":
            case "xhtml":
            case "php":
                // server responded with error page
                if (!alwaysAcceptContent)
                    return null;
                break;

            case "txt":
                // if txt file was explicitly requested, isEquivalentExtensions above already returned it 
                // otherwise (non-txt URL) server responded with error page
                if (!alwaysAcceptContent)
                    return null;
                break;

            default:
                break;
            }
        }

        URL xurl = new URL(href);
        String urlPathExt = LinkFilepath.getMediaFileExtension(xurl.getPath());
        if (image || FileTypeDetector.isImageExtension(urlPathExt))
        {
            /* expected image but content is not image */
            if (!FileTypeDetector.isImageExtension(contentExt) && !alwaysAcceptContent)
                return null;
        }

        if (finalExt != null && finalExt.length() != 0)
        {
            /* same */
            if (fnExt != null && fnExt.equalsIgnoreCase(finalExt))
                return filepath;

            /* appends to any possibly existing extension, e.g. aaa.txt.html or aaa.jpg.png */
            return filepath + "." + finalExt;
        }
        else
        {
            return filepath;
        }
    }

    public static Map<String, String> getImageHeaders()
    {
        Map<String, String> headers = new HashMap<>();
        addImageHeaders(headers);
        return headers;
    }

    public static Map<String, String> getDocumentHeaders()
    {
        Map<String, String> headers = new HashMap<>();
        addDocumentHeaders(headers);
        return headers;
    }

    public static void addImageHeaders(Map<String, String> headers)
    {
        headers.put("Sec-Fetch-Dest", "image");
        headers.put("Sec-Fetch-Mode", "no-cors");
        headers.put("Sec-Fetch-Site", "cross-site");
        headers.put("Sec-Fetch-User", null);
    }

    public static void addDocumentHeaders(Map<String, String> headers)
    {
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");
    }

    public static class AlreadyFailedException extends Exception
    {
        private static final long serialVersionUID = 1L;
    }
}