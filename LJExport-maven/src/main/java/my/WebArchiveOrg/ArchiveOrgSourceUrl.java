package my.WebArchiveOrg;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ArchiveOrgSourceUrl
{
    /*
     * Generate possible variants of source site URL, as may be stored in archive.org
     */
    public static List<String> variants(String url) throws Exception
    {
        return variants(new URL(url));
    }

    public static List<String> variants(URL url) throws Exception
    {
        url = lowercaseHostPort(url);

        List<String> variants = new ArrayList<>();
        variants.add(url.toString());

        int port = url.getPort();
        if (port >= 0)
        {
            addVariant(variants, url, "http", port);
            addVariant(variants, url, "https", port);
        }

        addVariant(variants, url, "http", -1);
        addVariant(variants, url, "http", 80);
        addVariant(variants, url, "https", -1);
        addVariant(variants, url, "https", 443);

        return variants;
    }

    /*
     * Lowercase host and port
     */
    private static URL lowercaseHostPort(URL original) throws MalformedURLException
    {
        String protocol = original.getProtocol(); // already lowercase
        String host = original.getHost(); // already lowercase in practice, but let's be explicit
        int port = original.getPort();
        String file = original.getFile(); // path + query
        String ref = original.getRef(); // fragment

        /* archive.org storage URIs use no anchors */
        ref = null;

        return new URL(protocol.toLowerCase(), host.toLowerCase(), port, file + (ref != null ? "#" + ref : ""));
    }

    private static void addVariant(List<String> variants, URL url, String protocol, int port) throws Exception
    {
        url = applyProtocolPort(url, protocol, port);
        String sv = url.toString();
        if (!variants.contains(sv))
            variants.add(sv);
    }

    private static URL applyProtocolPort(URL original, String protocol, int port) throws MalformedURLException
    {
        String host = original.getHost(); // already lowercase in practice, but let's be explicit
        String file = original.getFile(); // path + query
        String ref = original.getRef(); // fragment

        /* archive.org storage URIs use no anchors */
        ref = null;

        return new URL(protocol.toLowerCase(), host.toLowerCase(), port, file + (ref != null ? "#" + ref : ""));
    }
}
