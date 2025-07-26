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
     * or:
     * 
     *   https://external.xx.fbcdn.net/safe_image.php?d=AQCGFA0tZwkf8pbu&w=130&h=130&url=http://gotoroad.ru/img/map-index-life.jpg&cfs=1&_nc_hash=AQC6OxfHDMHpoNRy
     *   https://external.xx.fbcdn.net/safe_image.php?d=AQCGFA0tZwkf8pbu&w=130&h=130&url=http%3A%2F%2Fgotoroad.ru%2Fimg%2Fmap-index-life.jpg&cfs=1&_nc_hash=AQC6OxfHDMHpoNRy
     * 
     * If urls is empty collection, returns null.
     * If urls contains only one element, returns this element.
     * If urls contains multiple elements of different shapes of the same URL (protocol, encodings), tries to consolidate them to one canonic form and return it.
     * If consolidation is impossible, returns null.
     */

    /**
     * Consolidates a collection of URLs into a canonical form.
     * Prefers HTTPS and less double-encoding (e.g., %2520).
     * Accepts non-conforming input URLs.
     *
     * @param urls            Input URLs
     * @param ignorePathCase  Whether to treat path case-insensitively
     * @return A preferred canonical URL (from the input set), or null if conflict
     */
    public static String consolidateUrlVariants(Collection<String> urls, boolean ignorePathCase)
    {
        if (urls == null || urls.isEmpty())
            return null;
        if (urls.size() == 1)
            return polishToRfcSafe(urls.iterator().next());

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

        String best = selectBestVariant(normalizedToOriginals.values().iterator().next());
        return polishToRfcSafe(best);
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
            String query = normalizeQuery(url.getQuery());
            String fragment = url.getRef();

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
        List<String> encoded = new ArrayList<>();
        for (String part : parts)
        {
            try
            {
                String decoded = fullyDecode(part);
                if (ignoreCase)
                    decoded = decoded.toLowerCase(Locale.ROOT);
                String encodedPart = URLEncoder.encode(decoded, StandardCharsets.UTF_8).replace("+", "%20");
                encoded.add(encodedPart);
            }
            catch (Exception e)
            {
                encoded.add(part);
            }
        }
        return "/" + String.join("/", encoded);
    }

    private static String normalizeQuery(String rawQuery)
    {
        if (rawQuery == null || rawQuery.isEmpty())
            return null;

        StringBuilder result = new StringBuilder();
        String[] pairs = rawQuery.split("&");

        for (int i = 0; i < pairs.length; i++)
        {
            String[] kv = pairs[i].split("=", 2);
            String rawKey = kv[0];
            String rawVal = (kv.length > 1) ? kv[1] : "";

            String decodedKey = fullyDecode(rawKey);
            String decodedVal = fullyDecode(rawVal);

            // Try to polish all values that appear to be URLs
            String polished = polishToRfcSafe(decodedVal);
            if (polished != null && (decodedVal.startsWith("http://") || decodedVal.startsWith("https://")))
            {
                decodedVal = polished;
            }

            String encKey = URLEncoder.encode(decodedKey, StandardCharsets.UTF_8).replace("+", "%20");
            String encVal = URLEncoder.encode(decodedVal, StandardCharsets.UTF_8).replace("+", "%20");

            if (i > 0)
                result.append("&");
            result.append(encKey).append("=").append(encVal);
        }

        return result.toString();
    }

    private static String fullyDecode(String input)
    {
        String prev;
        String current = input;
        int maxDepth = 5;
        int count = 0;
        do
        {
            prev = current;
            current = URLDecoder.decode(prev, StandardCharsets.UTF_8);
            count++;
        }
        while (!prev.equals(current) && count < maxDepth);
        return current;
    }

    private static String selectBestVariant(List<String> variants)
    {
        String best = null;
        int bestScore = Integer.MAX_VALUE;

        for (String v : variants)
        {
            int score = countOccurrences(v, "%25");
            if (v.toLowerCase(Locale.ROOT).startsWith("https://"))
                score -= 10;
            if (score < bestScore)
            {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    private static String polishToRfcSafe(String rawUrl)
    {
        try
        {
            URL url = new URL(rawUrl);
            String scheme = url.getProtocol().toLowerCase(Locale.ROOT);
            String host = url.getHost().toLowerCase(Locale.ROOT);
            int port = url.getPort();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
            {
                port = -1;
            }

            String path = normalizeAndReencodePath(url.getPath(), false);
            String query = normalizeQuery(url.getQuery());
            String fragment = url.getRef();

            URI polished = new URI(
                    scheme,
                    null,
                    host,
                    port,
                    path,
                    query,
                    fragment);

            return polished.toASCIIString();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static int countOccurrences(String s, String sub)
    {
        int count = 0, idx = 0;
        while ((idx = s.indexOf(sub, idx)) != -1)
        {
            count++;
            idx += sub.length();
        }
        return count;
    }
}