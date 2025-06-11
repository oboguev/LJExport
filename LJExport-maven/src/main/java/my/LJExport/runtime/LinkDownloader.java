package my.LJExport.runtime;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import my.LJExport.Config;

public class LinkDownloader
{
    private static Set<String> dontDownload;

    public static String download(String linksDir, String href, String referer)
    {
        AtomicReference<Web.Response> response = new AtomicReference<>(null);

        String threadName = Thread.currentThread().getName();
        if (threadName == null)
            threadName = "(unnamed)";

        try
        {
            // avoid HTTPS certificate problem
            href = https2http(href, "l-stat.livejournal.net");
            href = https2http(href, "ic.pics.livejournal.com");

            StringBuilder sb = buildFilePath(linksDir, href);

            // Main.out(">>> Downloading: " + href + " -> " + sb.toString());

            File f = new File(sb.toString());
            final String final_href = href;
            final String final_threadName = threadName;
            final StringBuilder final_sb = sb;

            Thread.currentThread().setName(threadName + " downloading " + final_href + " namelock wait");

            NamedLocks.interlock(sb.toString(), () ->
            {
                Thread.currentThread().setName(final_threadName + " downloading " + final_href + " prepare");

                if (!f.exists())
                {
                    String host = (new URL(final_href)).getHost();
                    host = host.toLowerCase();

                    Map<String, String> headers = new HashMap<>();

                    if (referer != null && referer.length() != 0)
                    {

                        if (host.equals("snag.gy") || host.endsWith(".snag.gy") ||
                                host.equals("snipboard.io") || host.endsWith(".snipboard.io"))
                        {
                            // use referer and accept
                            headers.put("Referer", referer);
                            headers.put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
                        }
                        else
                        {
                            // do not use referer
                        }
                    }

                    Thread.currentThread().setName(final_threadName + " downloading " + final_href);
                    Web.Response r = Web.get(final_href, Web.BINARY | Web.PROGRESS, headers);

                    if (r.code < 200 || r.code >= 300)
                    {
                        response.set(r);
                        throw new Exception("HTTP code " + r.code + ", reason: " + r.reason);
                    }

                    try
                    {
                        Util.mkdir(f.getParent());
                        Util.writeToFileSafe(final_sb.toString(), r.binaryBody);
                    }
                    catch (Exception ex)
                    {
                        Util.noop();
                        throw ex;
                    }
                }
            });

            Thread.currentThread().setName(threadName);

            String newref = sb.toString().substring((linksDir + File.separator).length());
            newref = newref.replace(File.separator, "/");
            newref = "../../../links/" + encodePathCopmonents(newref);
            return newref;
        }
        catch (Exception ex)
        {
            String host = extractHostSafe(href);
            Web.Response r = response.get();

            if (host != null && r != null)
            {
                if (host.contains("imgprx.livejournal.net") && r.code != 404)
                {
                    Util.noop();
                }

                if (host.contains("l-stat.livejournal.net") && r.code != 404)
                {
                    Util.noop();
                }

                if (host.contains("ic.pics.livejournal.com") && r.code != 404)
                {
                    Util.noop();
                }

                if (host.contains("archive.org") && r.code != 404)
                {
                    Util.noop();
                }
            }
            else if (host != null & r == null)
            {
                if (host.contains("imgprx.livejournal.net"))
                {
                    Util.noop();
                }

                if (host.contains("l-stat.livejournal.net"))
                {
                    Util.noop();
                }

                if (host.contains("ic.pics.livejournal.com"))
                {
                    Util.noop();
                }

                if (host.contains("archive.org"))
                {
                    Util.noop();
                }
            }

            // Main.err("Unable to download external link " + href, ex);
            Util.noop();
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }

        return null;
    }
    
    private static String encodePathCopmonents(String ref)
    {
        StringBuilder sb = new StringBuilder();
        
        for (String pc : Util.asList(ref, "/"))
        {
            if (pc.length() != 0)
            {
                if (sb.length() != 0)
                    sb.append("/");
                sb.append(URLCodec.encode(pc));
            }
        }
        
        return sb.toString();
    }

    private static String https2http(String href, String host)
    {
        final String key = "https://" + host + "/";
        final String key_change = "http://" + host + "/";
        if (href.startsWith(key))
            href = key_change + href.substring(key.length());
        return href;
    }

    public static boolean shouldDownload(String href, boolean filterDownloadFileTypes) throws Exception
    {
        synchronized (LinkDownloader.class)
        {
            if (dontDownload == null)
                dontDownload = Util.read_set("dont-download.txt");
        }

        try
        {
            if (href == null || href.length() == 0)
                return false;
            href = Util.stripAnchor(href);

            if (dontDownload.contains(href))
                return false;

            URL url = new URL(href);

            String protocol = url.getProtocol();
            if (protocol == null)
                return false;
            if (!(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")))
                return false;
            
            // https://xc3.services.livejournal.com/ljcounter
            String host = url.getHost();
            if (host != null && host.equalsIgnoreCase("xc3.services.livejournal.com"))
                return false;

            String path = url.getPath();
            if (path == null)
                return false;

            if (filterDownloadFileTypes)
            {
                for (String ext : Config.DownloadFileTypes)
                {
                    if (path.toLowerCase().endsWith("." + ext))
                        return true;
                }

                return false;
            }
            else
            {
                return true;
            }
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    private static String extractHost(String href) throws Exception
    {
        String host = (new URL(href)).getHost();
        host = host.toLowerCase();
        return host;
    }

    private static String extractHostSafe(String href)
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

    /* ======================================================================== */

    private static StringBuilder buildFilePath(String linksDir, String href) throws Exception
    {
        URL url = new URL(href);

        List<StringBuilder> list = new ArrayList<>();

        StringBuilder sb = new StringBuilder(linksDir + File.separator);
        sb.append(url.getHost());
        int port = url.getPort();
        if (port > 0 && port != 80 && port != 443)
            sb.append("__" + port);
        list.add(sb);
        sb = null;

        if (url.getHost().equals("imgprx.livejournal.net"))
        {
            for (String pc : Util.asList(url.getPath(), "/"))
            {
                if (pc.length() != 0)
                {
                    sb = new StringBuilder("x-" + Util.uuid());
                    list.add(sb);
                }
            }
        }
        else
        {
            for (String pc : Util.asList(url.getPath(), "/"))
            {
                if (pc.length() != 0)
                {
                    sb = new StringBuilder(pc);
                    list.add(sb);
                }
            }
            
            String ext = getFileExtension(sb.toString());

            String query = url.getQuery();
            if (query != null && query.length() != 0)
            {
                sb.append("?" + query);
                // reappend extenstion
                if (ext != null && ext.length() != 0 && ext.length() <= 4)
                    sb.append("." + ext);
            }    
        }

        StringBuilder path = new StringBuilder();
        for (StringBuilder x : list)
        {
            if (path.length() == 0)
            {
                path.append(x.toString());
            }
            else
            {
                path.append(File.separator);
                path.append(makeSanePathComponent(x.toString()));
            }
        }

        return path;
    }

    private static final int MaxPathComponentLength = 80;

    private static String makeSanePathComponent(String component) throws Exception
    {
        /*
         * Unpack %xx sequences -> unicode.
         * Somtimes it has double encoding.
         */
        String fn = URLCodec.decodeMixed(component);
        fn = URLCodec.decodeMixed(fn);

        /*
         * If reserved file name, mangle it
         */
        if (Util.isReservedFileName(fn))
            return "x-" + Util.uuid() + "_" + fn;

        /*
         * If name is too long
         */
        if (fn.length() > MaxPathComponentLength)
        {
            String ext = getFileExtension(fn);
            if (ext == null || ext.length() == 0 || ext.length() > 4)
                return "x-" + Util.uuid();
            else
                return "x-" + Util.uuid() + "." + ext;
        }

        /*
         * Encode reserved characters to URL representation
         */
        fn = URLCodec.encodeFilename(fn);

        return fn;
    }

    public static String getFileExtension(String fn)
    {
        int dotIndex = fn.lastIndexOf('.');
        // no extension or dot is at the end
        if (dotIndex == -1 || dotIndex == fn.length() - 1)
            return null;
        else
            return fn.substring(dotIndex + 1);
    }
}
