package my.LJExport.runtime.url;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UrlUtil
{
    /*
     * Generate unique canonical representation of URL variants such as:
     * 
     *   https://www.good-walls.ru/files/СНиП_II-3-79_Строительная%20теплотехника.pdf
     *   https://www.good-walls.ru/files/%D0%A1%D0%9D%D0%B8%D0%9F_II-3-79_%D0%A1%D1%82%D1%80%D0%BE%D0%B8%D1%82%D0%B5%D0%BB%D1%8C%D0%BD%D0%B0%D1%8F%20%D1%82%D0%B5%D0%BF%D0%BB%D0%BE%D1%82%D0%B5%D1%85%D0%BD%D0%B8%D0%BA%D0%B0.pdf
     *   https://www.good-walls.ru/files/СНиП_II-3-79_Строительная теплотехника.pdf
     * 
     * or:
     * 
     *   http://i.imgur.com/TWqVwm5.jpg
     *   https://i.imgur.com/TWqVwm5.jpg
     * 
     * in the latter case it is resolved to https://i.imgur.com/TWqVwm5.jpg
     * 
     * If urls is empty collection, returns null.
     * If urls contains only one element, returns this element.
     * If urls contains multiple elements of different shapes of the same URL (protocol, encodings), tries to consolidate them to one canonic form and return it.
     * If consolidation is impossible, returns null.
     */
    public static String consolidateUrlVariants(Collection<String> urls, boolean ignorePathCase)
    {
        if (urls == null || urls.isEmpty())
            return null;
        if (urls.size() == 1)
            return urls.iterator().next();

        Map<String, List<String>> normalizedToOriginals = new HashMap<>();

        for (String url : urls)
        {
            String normalized = normalizeUrlForComparison(url, ignorePathCase);
            if (normalized == null)
                return null;

            normalizedToOriginals
                    .computeIfAbsent(normalized, k -> new ArrayList<>())
                    .add(url);
        }

        if (normalizedToOriginals.size() != 1)
            return null;

        List<String> variants = normalizedToOriginals.values().iterator().next();

        for (String v : variants)
            if (v.toLowerCase(Locale.ROOT).startsWith("https://"))
                return v;

        return variants.get(0); // fallback
    }

    private static String normalizeUrlForComparison(String urlString, boolean ignorePathCase)
    {
        try
        {
            URL url = new URL(urlString.trim());

            String scheme = url.getProtocol().toLowerCase(Locale.ROOT);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            int port = url.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
            {
                port = -1;
            }

            String path = normalizeAndReencodePath(url.getPath(), ignorePathCase);
            String query = url.getQuery();
            String fragment = url.getRef();

            // Use "scheme" as placeholder to group http/https together
            URI normalized = new URI(
                    "scheme",
                    null,
                    host,
                    port,
                    path,
                    query,
                    fragment);

            return normalized.toString();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String normalizeAndReencodePath(String rawPath, boolean ignoreCase)
    {
        String[] parts = rawPath.split("/");
        List<String> reencodedParts = new ArrayList<>();
        for (String part : parts)
        {
            try
            {
                String decoded = URLDecoder.decode(part, StandardCharsets.UTF_8);
                if (ignoreCase)
                    decoded = decoded.toLowerCase(Locale.ROOT);
                String encoded = URLEncoder.encode(decoded, StandardCharsets.UTF_8)
                        .replace("+", "%20");
                reencodedParts.add(encoded);
            }
            catch (Exception e)
            {
                // fallback on error
                reencodedParts.add(part);
            }
        }
        return "/" + String.join("/", reencodedParts);
    }
}
