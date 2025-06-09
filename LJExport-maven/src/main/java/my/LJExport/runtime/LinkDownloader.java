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

        if (href.equals(
                "https://www.hse.ru/data/2012/06/17/1255493485/%D0%9A%D1%83%D0%BB%D0%B8%D0%BA%D0%BE%D0%B2%20%D0%A1.%D0%92.%20%D0%98%D0%BC%D0%BF%D0%B5%D1%80%D0%B0%D1%82%D0%BE%D1%80%20%D0%9D%D0%B8%D0%BA%D0%BE%D0%BB%D0%B0%D0%B9%20II%20%D0%BA%D0%B0%D0%BA%20%D1%80%D0%B5%D1%84%D0%BE%D1%80%D0%BC%D0%B0%D1%82%D0%BE%D1%80_%D0%BA%20%D0%BF%D0%BE%D1%81%D1%82%D0%B0%D0%BD%D0%BE%D0%B2%D0%BA%D0%B5%20%D0%BF%D1%80%D0%BE%D0%B1%D0%BB%D0%B5%D0%BC.pdf"))
        {
            Util.noop();
        }

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
                    Util.mkdir(f.getParent());

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
                        throw new Exception("HTTP code " + r.code + ", reason: " + r.reason);

                    Util.writeToFileSafe(final_sb.toString(), r.binaryBody);
                }
            });

            Thread.currentThread().setName(threadName);

            String newref = sb.toString().substring((linksDir + File.separator).length());
            newref = newref.replace(File.separator, "/");
            newref = "../../../links/" + newref;
            return newref;
        }
        catch (Exception ex)
        {
            String host = extractHostSafe(href);
            Web.Response r = response.get();

            if (host != null && r != null)
            {
                if (host.contains("l-stat.livejournal.net"))
                {
                    Util.noop();
                }

                if (host.contains("ic.pics.livejournal.com") && r.code != 404)
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

    private static String https2http(String href, String host)
    {
        final String key = "https://" + host + "/";
        final String key_change = "http://" + host + "/";
        if (href.startsWith(key))
            href = key_change + href.substring(key.length());
        return href;
    }

    public static boolean shouldDownload(String href) throws Exception
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

            String path = url.getPath();
            if (path == null)
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

        for (String pc : Util.asList(url.getPath(), "/"))
        {
            if (pc.length() != 0)
            {
                sb = new StringBuilder(pc);
                list.add(sb);
            }
        }

        String query = url.getQuery();
        if (query != null && query.length() != 0)
            sb.append("?" + query);

        sb = new StringBuilder();
        for (StringBuilder sbx : list)
        {
            if (sb.length() == 0)
            {
                sb.append(sbx.toString());
            }
            else
            {
                sb.append(File.separator);
                sb.append(makeSanePathComponent(sbx.toString()));
            }
        }

        return sb;
    }

    private static String makeSanePathComponent(String component) throws Exception
    {
        component = unicodePathComponent(component);
        if (isSanePathComponent(component))
            return component;
        else
            return URLCodec.encode(component);
    }

    private static String unicodePathComponent(String component)
    {
        try
        {
            if (component.contains("%"))
            {
                String decoded = URLCodec.decode(component);
                String encoded = URLCodec.encode(decoded);
                if (encoded.equals(component))
                    return decoded;
                else
                    return component;
            }
            else
            {
                return component;
            }
        }
        catch (Exception ex)
        {
            return component;
        }
    }
    
    private static boolean isSanePathComponent(String component)
    {
        if (File.separatorChar == '/')
            return isSaneLinuxPathComponent(component);
        else
            return isSaneWindowsPathComponent(component);
    }

    /* ======================================================================== */

    // Illegal characters in Windows file names
    private static final char[] WINDOWS_ILLEGAL_CHARS = {
            '\\', '/', ':', '*', '?', '"', '<', '>', '|'
    };

    // Reserved device names in Windows (case-insensitive)
    private static final String[] WINDOWS_RESERVED_NAMES = {
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    private static boolean isSaneWindowsPathComponent(String name)
    {
        // null or empty is not a valid filename
        if (name == null || name.isEmpty())
            return false;

        // Check for illegal characters
        for (char c : name.toCharArray())
        {
            if (c == '\'' || c == '/')
                return false;

            for (char bad : WINDOWS_ILLEGAL_CHARS)
            {
                if (c == bad)
                    return false;
            }

            // Control character
            if (c < 32 || c == 255)
                return false;
        }

        // Check for reserved device names (case-insensitive)
        for (String reserved : WINDOWS_RESERVED_NAMES)
        {
            if (name.equalsIgnoreCase(reserved))
                return false;
        }

        // Check for leading/trailing space
        if (name.startsWith(" ") || name.endsWith(" "))
            return false;

        return true;
    }

    /* ======================================================================== */

    // Characters that are problematic even if technically allowed
    private static final char[] LINUX_DISCOURAGED_CHARS = {
            '*', '?', '|', '>', '<', ':', '"', '\\'
    };

    private static boolean isSaneLinuxPathComponent(String name)
    {
        // null or empty is not a valid filename
        if (name == null || name.isEmpty())
            return false;

        for (char c : name.toCharArray())
        {
            if (c == '\'' || c == '/')
                return false;

            for (char bad : LINUX_DISCOURAGED_CHARS)
            {
                if (c == bad)
                    return false;
            }

            // Control character
            if (c < 32 || c == 255)
                return false;
        }

        return true;
    }
}
