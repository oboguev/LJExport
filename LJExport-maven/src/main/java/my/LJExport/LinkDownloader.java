package my.LJExport;

import java.io.File;
import java.net.URL;
import java.net.URLEncoder;

public class LinkDownloader
{
    public static String download(String linksDir, String href)
    {
        try
        {
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
            if (f.exists())
                return null;
            Util.mkdir(f.getParent());

            Web.Response r = Web.get(href, true);

            if (r.code < 200 || r.code >= 300)
                throw new Exception("HTTP code " + r.code + ", reason: " + r.reason);

            Util.writeToFile(sb.toString(), r.binaryBody);

            String newref = sb.toString().substring((linksDir + File.separator).length());
            newref = newref.replace(File.separator, "/");
            newref = "../../../links/" + newref;
            return newref;
        }
        catch (Exception ex)
        {
            // Main.err("Unable to download external link " + href, ex);
        }

        return null;
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
