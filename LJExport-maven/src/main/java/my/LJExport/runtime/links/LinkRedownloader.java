package my.LJExport.runtime.links;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

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

        try
        {
            r = redownload(image, url, referer);

            setThreadName(threadName, "downloading " + url);

            String contentExt = FileTypeDetector.fileExtensionFromActualFileContent(r.binaryBody);

            if (contentExt == null)
            {
                String headerExt = null;
                if (r.contentType != null)
                    headerExt = FileTypeDetector.fileExtensionFromMimeType(Util.despace(r.contentType).toLowerCase());
                contentExt = headerExt;
            }

            String urlPathExt = LinkFilepath.getMediaFileExtension(xurl.getPath());
            
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

    public Web.Response redownload(boolean image, String url, String referer) throws Exception
    {
        if (ArchiveOrgUrl.isArchiveOrgSimpleTimestampUrl(url))
            url = ArchiveOrgUrl.toDirectDownloadUrl(url, false);
        String url_noanchor = Util.stripAnchor(url);

        URL xurl = new URL(url);
        String host = xurl.getHost().toLowerCase();

        Map<String, String> headers = new HashMap<>();

        if (referer != null && referer.length() != 0)
            headers.put("Referer", referer);

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

        String threadName = Thread.currentThread().getName();
        Web.Response r = null;

        try
        {
            setThreadName(threadName, "downloading " + url);

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
            return null;
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }
    }

    private static void setThreadName(String threadName, String msg)
    {
        String tn = threadName;
        if (tn.length() != 0)
            tn = tn + " ";
        Thread.currentThread().setName(tn + msg);
    }
}
