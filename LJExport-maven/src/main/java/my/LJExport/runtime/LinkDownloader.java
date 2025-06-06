package my.LJExport.runtime;

import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import my.LJExport.Config;

public class LinkDownloader
{
    public static String download(String linksDir, String href, String referer)
    {
        String threadName = Thread.currentThread().getName();
        if (threadName == null)
            threadName = "(unnamed)";

        try
        {
            // avoid HTTPS certificate problem
            href = https2http(href, "l-stat.livejournal.net");
            href = https2http(href, "ic.pics.livejournal.com");
            
            href = Util.stripAnchor(href);
            URL url = new URL(href);
            StringBuffer sb = new StringBuffer(linksDir + File.separator);
            sb.append(url.getHost());
            int port = url.getPort();
            if (port > 0 && port != 80 && port != 443)
                sb.append("__" + port);

            for (String pc : Util.asList(url.getPath(), "/"))
            {
                if (pc.length() == 0)
                    continue;
                sb.append(File.separator);
                sb.append(URLEncoder.encode(pc, "UTF-8"));
            }

            String query = url.getQuery();
            if (query != null && query.length() != 0)
                sb.append(URLEncoder.encode("?" + query, "UTF-8"));

            // Main.out(">>> Downloading: " + href + " -> " + sb.toString());

            File f = new File(sb.toString());
            final String final_href = href;
            final String final_threadName = threadName;

            NamedLocks.interlock(sb.toString(), () ->
            {
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

                    Util.writeToFileSafe(sb.toString(), r.binaryBody);
                }
            });

            String newref = sb.toString().substring((linksDir + File.separator).length());
            newref = newref.replace(File.separator, "/");
            newref = "../../../links/" + newref;
            return newref;
        }
        catch (Exception ex)
        {
            Util.noop();
            // Main.err("Unable to download external link " + href, ex);
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

    public static boolean shouldDownload(String href)
    {
        try
        {
            if (href == null || href.length() == 0)
                return false;
            href = Util.stripAnchor(href);
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
}
