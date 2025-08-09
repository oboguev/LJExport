package my.LJExport.runtime.links;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpException;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.http.NetErrors;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.WebArchiveOrg.ArchiveOrgUrl;

public class LinkRedownloader
{
    private final String linksDir;

    private static Set<String> failedSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public LinkRedownloader(String linksDir)
    {
        this.linksDir = linksDir;
    }

    public boolean redownload(boolean image, String url, String unixRelFilePath, String referer) throws Exception
    {
        String fullFilePath = linksDir + File.separator + unixRelFilePath.replace("/", File.separator);
        Web.Response r = null;

        URL xurl = new URL(url);
        String host = xurl.getHost().toLowerCase();
        String threadName = Thread.currentThread().getName();
        String threadNameStem = threadName;

        try
        {
            r = redownload(image, url, referer);

            setThreadName(threadNameStem, "downloading " + url);

            String urlPathExt = LinkFilepath.getMediaFileExtension(xurl.getPath());
            String contentExt = FileTypeDetector.fileExtensionFromActualFileContent(r.binaryBody, urlPathExt);

            if (contentExt == null)
            {
                String headerExt = null;
                if (r.contentType != null)
                    headerExt = FileTypeDetector.fileExtensionFromMimeType(Util.despace(r.contentType).toLowerCase());
                contentExt = headerExt;
            }

            if (image || FileTypeDetector.isImageExtension(urlPathExt))
            {
                /* expected image but content is not image */
                if (!FileTypeDetector.isImageExtension(contentExt))
                    return false;
            }

            if (contentExt != null)
            {
                switch (contentExt)
                {
                case "html":
                case "xhtml":
                case "php":
                    return false;

                case "txt":
                    if (urlPathExt != null && urlPathExt.equalsIgnoreCase("txt"))
                        break;
                    else
                        return false;

                default:
                    break;
                }
            }

            File fp = new File(fullFilePath).getCanonicalFile();
            if (!fp.getParentFile().exists())
                fp.getParentFile().mkdirs();

            Util.writeToFileSafe(fullFilePath, r.binaryBody);

            return true;
        }
        catch (Exception ex)
        {
            // error is not network-related, such as NullPointerException
            if (!NetErrors.isNetworkException(ex))
                throw ex;

            LinkDownloader.examineException(host, r, ex);
            return false;
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }
    }

    public static Web.Response redownload(boolean image, String url, String referer) throws Exception
    {
        Map<String, String> headers = new HashMap<>();

        if (image)
        {
            headers.put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            LinkDownloader.addImageHeaders(headers);
        }
        else
        {
            headers.put("Accept", Config.UserAgentAccept_Download);
            LinkDownloader.addDocumentHeaders(headers);
        }

        return redownload(image, headers, url, referer);
    }

    public static Web.Response redownload_json(String url, String referer) throws Exception
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        // headers.put("Accept", Config.UserAgentAccept_Download);
        LinkDownloader.addDocumentHeaders(headers);
        return redownload(false, headers, url, referer);
    }

    public static Web.Response redownload(boolean image, Map<String, String> headers, String url, String referer) throws Exception
    {
        if (ArchiveOrgUrl.isArchiveOrgSimpleTimestampUrl(url))
            url = ArchiveOrgUrl.toDirectDownloadUrl(url, false, image);
        String url_noanchor = Util.stripAnchor(url);

        if (failedSet.contains(url_noanchor))
            return null;

        URL xurl = new URL(url_noanchor);
        String host = xurl.getHost().toLowerCase();

        if (referer != null && referer.length() != 0)
        {
            headers = new HashMap<>(headers);
            headers.put("Referer", referer);
        }

        String threadName = Thread.currentThread().getName();
        String threadNameStem = threadName;

        Web.Response r = null;

        try
        {
            setThreadName(threadNameStem, "downloading " + url_noanchor);

            r = Web.get(url_noanchor, Web.BINARY | Web.PROGRESS, headers, (code) ->
            {
                return code >= 200 && code <= 299 && code != 204;
            });

            if (r.code < 200 || r.code >= 300 || r.code == 204)
                throw new HttpException("HTTP code " + r.code + ", reason: " + r.reason);

            return r;
        }
        catch (Exception ex)
        {
            // error is not network-related, such as NullPointerException
            if (!NetErrors.isNetworkException(ex))
                throw ex;

            LinkDownloader.examineException(host, r, ex);
            failedSet.add(url_noanchor);

            return null;
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }
    }

    private static void setThreadName(String threadNameStem, String msg)
    {
        String tn = threadNameStem;
        if (tn.length() != 0 && msg.length() != 0)
            tn = tn + " ";
        Thread.currentThread().setName(tn + msg);
    }
}
