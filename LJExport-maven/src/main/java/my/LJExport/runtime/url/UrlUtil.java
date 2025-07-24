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

    /**
     * Consolidates a collection of URLs into a canonical form.
     * Prefers HTTPS and less double-encoding (e.g., %2520).
     *
     * @param urls            Input URLs
     * @param ignorePathCase  Whether to treat path case-insensitively
     * @return A preferred canonical URL (from the input set), or null if conflict
     */

    /**
     * Consolidates a collection of URLs into a canonical RFC-conforming form.
     * Accepts non-conforming input URLs.
     *
     * @param urls            Input URLs
     * @param ignorePathCase  Whether to compare paths case-insensitively
     * @return RFC-compliant canonical URL, or null if conflicting
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

        List<String> variants = normalizedToOriginals.values().iterator().next();
        String best = selectBestVariant(variants);
        return polishToRfcSafe(best);
    }

    /**
     * Normalize a URL for equivalence comparison.
     */
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

            URI normalized = new URI(
                    "scheme", // placeholder to unify http/https
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

    /**
     * Repeatedly decode encoded segments (handles double-encoding like %2520).
     */
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

    /**
     * Re-encode path components to strict percent-encoded format.
     */
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
                {
                    decoded = decoded.toLowerCase(Locale.ROOT);
                }
                String encodedPart = URLEncoder.encode(decoded, StandardCharsets.UTF_8)
                        .replace("+", "%20");
                encoded.add(encodedPart);
            }
            catch (Exception e)
            {
                encoded.add(part); // fallback
            }
        }
        return "/" + String.join("/", encoded);
    }

    /**
     * Selects the best variant among URL strings:
     * - Prefer fewer %25 (less double-encoding)
     * - Prefer HTTPS
     */
    private static String selectBestVariant(List<String> variants)
    {
        String best = null;
        int bestScore = Integer.MAX_VALUE;

        for (String v : variants)
        {
            int score = countOccurrences(v, "%25");
            if (v.toLowerCase(Locale.ROOT).startsWith("https://"))
            {
                score -= 10;
            }
            if (score < bestScore)
            {
                best = v;
                bestScore = score;
            }
        }

        return best;
    }

    /**
     * Convert possibly non-conforming URL to polished, RFC-compliant form.
     * Applies encoding, removes default ports, normalizes scheme and host.
     */
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
            String query = url.getQuery();
            String fragment = url.getRef();

            URI polished = new URI(
                    scheme,
                    null,
                    host,
                    port,
                    path,
                    query,
                    fragment);

            return polished.toASCIIString(); // return encoded, RFC-compliant URI string
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Count occurrences of substring in string.
     */
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