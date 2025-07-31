package my.LJExport.runtime.links;

import java.net.URI;
import java.net.URL;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.links.util.URLClassifier;
import my.LJExport.runtime.url.UrlPatternMatcher;

public class ShouldDownload
{
    private static UrlPatternMatcher dontDownload;
    private static UrlPatternMatcher knownImageHosting;

    public static synchronized void init() throws Exception
    {
        if (dontDownload == null)
            dontDownload = UrlPatternMatcher.fromResource("dont-download.txt");

        if (knownImageHosting == null)
            knownImageHosting = UrlPatternMatcher.fromResource("known-image-hosting.txt");
    }

    public static boolean shouldDownload(boolean image, String href) throws Exception
    {
        init();

        return image ? shouldDownloadImage(href) : shouldDownloadDocument(href);
    }

    public static boolean shouldDownloadImage(String href) throws Exception
    {
        init();

        if (href == null || href.length() == 0)
            return false;
        href = Util.stripAnchor(href);

        if (dontDownload.contains(href))
            return false;

        URI url = new URI(href);

        String path = url.getPath();
        if (path == null)
            return false;
        path = path.trim();
        if (path.length() == 0 || path.equals("/"))
            return false;

        String query = url.getRawQuery();
        if (query != null && query.trim().length() == 0)
            query = null;

        String ext = LinkFilepath.getMediaFileExtension(path);
        if (ext != null && ext.trim().length() == 0)
            ext = null;

        if (!knownImageHosting.contains(href))
        {
            // dont't download if no extension in path and no query
            if (ext == null && query == null)
                return false;

            // dont't download if not an image extension and no query
            if (!FileTypeDetector.isImageExtension(ext) && query == null)
                return false;
        }

        return true;
    }

    public static boolean shouldDownloadDocument(String href) throws Exception
    {
        try
        {
            if (href == null || href.length() == 0)
                return false;
            href = Util.stripAnchor(href);

            if (dontDownload.contains(href))
                return false;

            URI url = new URI(href);

            String protocol = url.getScheme();
            if (protocol == null)
                return false;
            if (!(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")))
                return false;

            String host = url.getHost();

            if (Util.False)
            {
                /*
                 * Now handled by DontDownload
                 */

                // counters
                // https://xc3.services.livejournal.com/ljcounter
                if (host != null && host.equalsIgnoreCase("xc3.services.livejournal.com"))
                    return false;

                // permanently stuck hosts
                if (host != null && host.equalsIgnoreCase("im0-tub-ru.yandex.net"))
                    return false;
                if (host != null && host.equalsIgnoreCase("im1-tub-ru.yandex.net"))
                    return false;
                if (href.startsWith("http://cs606728.vk.me/v606728377/"))
                    return false;
            }

            // sergeytsvetkov has plenty of duplicate book cover images in avatars.dzeninfra.ru
            if (Util.False && Config.User.equals("sergeytsvetkov") && host != null && host.equals("avatars.dzeninfra.ru"))
                return false;

            String path = url.getPath();
            if (path == null)
                return false;

            // HTML pages with document-looking URLs like https://en.wikipedia.org/wiki/File:FD2.png
            if (URLClassifier.isNonDocumentURL(url))
                return false;

            for (String ext : Config.DownloadFileTypes)
            {
                if (path.toLowerCase().endsWith("." + ext))
                    return true;
            }

            return false;
        }
        catch (Exception ex)
        {
            return false;
        }
    }
}
