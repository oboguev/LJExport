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

    private static FileBackedMap href2file = new FileBackedMap();

    public static void init(String linksDir) throws Exception
    {
        href2file.close();
        href2file.init(linksDir + File.separator + "map-href-file.txt");
    }

    public static String download(String linksDir, String href, String referer)
    {
        AtomicReference<Web.Response> response = new AtomicReference<>(null);
        AtomicReference<String> filename = new AtomicReference<>(null);

        String threadName = Thread.currentThread().getName();
        if (threadName == null)
            threadName = "(unnamed)";

        try
        {
            // avoid HTTPS certificate problem
            href = https2http(href, "l-stat.livejournal.net");
            href = https2http(href, "ic.pics.livejournal.com");

            String href_noanchor = Util.stripAnchor(href);
            filename.set(buildFilePath(linksDir, href));

            // Main.out(">>> Downloading: " + href + " -> " + filename.get());

            final String final_href = href;
            final String final_threadName = threadName;

            Thread.currentThread().setName(threadName + " downloading " + href + " namelock wait");

            NamedLocks.interlock(href_noanchor, () ->
            {
                Thread.currentThread().setName(final_threadName + " downloading " + final_href + " prepare");

                String actual_filename = filename.get();
                String afn = href2file.get(href_noanchor);
                if (afn != null)
                    actual_filename = afn;

                File f = new File(actual_filename);
                if (f.exists())
                {
                    filename.set(actual_filename);
                    synchronized (href2file)
                    {
                        if (null == href2file.get(href_noanchor))
                            href2file.put(href_noanchor, actual_filename);
                    }
                }
                else
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

                    if (r.code < 200 || r.code >= 300 || r.code == 204)
                    {
                        response.set(r);
                        throw new Exception("HTTP code " + r.code + ", reason: " + r.reason);
                    }

                    try
                    {
                        Util.mkdir(f.getAbsoluteFile().getParent());
                        Util.writeToFileSafe(actual_filename, r.binaryBody);
                        synchronized (href2file)
                        {
                            if (null == href2file.get(href_noanchor))
                                href2file.put(href_noanchor, actual_filename);
                        }
                        filename.set(actual_filename);
                    }
                    catch (Exception ex)
                    {
                        Util.noop();
                        throw ex;
                    }
                }
            });

            Thread.currentThread().setName(threadName);

            String newref = filename.get().substring((linksDir + File.separator).length());
            newref = newref.replace(File.separator, "/");
            newref = "../../../links/" + encodePathCopmonents(newref);
            return newref;
        }
        catch (Exception ex)
        {
            String host = extractHostSafe(href);
            Web.Response r = response.get();

            if (host != null && r != null && r.code != 204)
            {
                if (host.contains("imgprx.livejournal.net") && r.code != 404)
                {
                    Util.noop();
                }

                if (host.contains("l-stat.livejournal.net") && r.code != 404)
                {
                    Util.noop();
                }

                if (host.contains("ic.pics.livejournal.com") && r.code != 404 && r.code != 412)
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

            // sergeytsvetkov has plenty of duplicate book cover images in avatars.dzeninfra.ru
            if (Config.User.equals("sergeytsvetkov") && host != null && host.equals("avatars.dzeninfra.ru"))
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

    private static String buildFilePath(String linksDir, String href) throws Exception
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

        return path.toString();
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
         * Encode reserved characters to URL representation
         */
        fn = URLCodec.encodeFilename(fn);

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
