package my.LJExport.runtime.url;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class UrlConsolidator
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

        Map<String, List<String>> normalizedToOriginals = new LinkedHashMap<>();

        for (String url : urls)
        {
            String normalized = normalizeUrlForComparison(url, ignorePathCase);
            if (normalized != null)
            {
                normalizedToOriginals.computeIfAbsent(normalized, k -> new ArrayList<>()).add(url);
            }
        }

        if (normalizedToOriginals.size() != 1)
            return null;

        List<String> originalVariants = normalizedToOriginals.values().iterator().next();
        String best = originalVariants.stream()
                .min(Comparator.comparingInt(UrlConsolidator::scoreUrlVariant))
                .orElse(originalVariants.get(0));
        return encodeIllegalCharacters(polishUrl(best));
    }

    private static int scoreUrlVariant(String url)
    {
        int score = 0;
        if (url.startsWith("https://"))
            score -= 10;
        if (url.contains("%25"))
            score += 10;
        if (url.contains("%20"))
            score += 5;
        return score + url.length() / 100;
    }

    private static String normalizeUrlForComparison(String url, boolean ignorePathCase)
    {
        try
        {
            int fragmentIndex = url.indexOf('#');
            if (fragmentIndex >= 0)
                url = url.substring(0, fragmentIndex);

            url = url.replace(" ", "%20");
            url = decodeRecursive(url);
            url = sanitizeEmbeddedUrlsInQuery(url);
            url = encodeIllegalCharacters(url);

            URL rawUrl = new URL(url);
            String scheme = rawUrl.getProtocol();
            String host = rawUrl.getHost();
            int port = rawUrl.getPort();
            String path = rawUrl.getPath();
            String query = rawUrl.getQuery();

            if (ignorePathCase && path != null)
                path = path.toLowerCase(Locale.ROOT);

            String encodedPath = encodePath(path);
            String encodedQuery = (query != null) ? encodeQuery(query) : null;

            URI uri = new URI(scheme, null, host, port, encodedPath, encodedQuery, null);
            return uri.toASCIIString();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String polishUrl(String url)
    {
        try
        {
            String stripped = url.replace(" ", "%20");
            stripped = decodeRecursive(stripped);
            URI uri = new URI(stripped);

            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? uri.getAuthority() : uri.getHost();
            if (host != null)
                host = host.toLowerCase();

            String path = uri.getRawPath();
            if (path == null)
                path = "";

            String query = uri.getRawQuery();
            URI rebuilt = new URI(scheme, host, path, query, null);
            return rebuilt.toASCIIString();
        }
        catch (Exception e)
        {
            return url;
        }
    }

    private static String decodeRecursive(String input)
    {
        String prev;
        String current = input;
        do
        {
            prev = current;
            current = URLDecoder.decode(prev, StandardCharsets.UTF_8);
        }
        while (!current.equals(prev));
        return current.replace("%0A", "");
    }

    private static String sanitizeEmbeddedUrlsInQuery(String url)
    {
        try
        {
            int qPos = url.indexOf('?');
            if (qPos < 0)
                return url;

            String prefix = url.substring(0, qPos);
            String query = url.substring(qPos + 1);

            StringBuilder newQuery = new StringBuilder();
            String[] parts = query.split("&");

            for (int i = 0; i < parts.length; i++)
            {
                if (i > 0)
                    newQuery.append('&');
                String[] kv = parts[i].split("=", 2);
                String key = kv[0];
                String value = (kv.length > 1) ? kv[1] : "";

                String decodedOnce = decodeOnce(value);
                String stripped = decodedOnce.replaceAll("(%0A|%0D|\\r|\\n)", "");
                String decoded = decodeRecursive(stripped);

                if (looksLikeBase64(decoded))
                {
                    String canon = URLEncoder.encode(decoded, StandardCharsets.UTF_8.name()).replace("+", "%20");
                    newQuery.append(key).append('=').append(canon);
                }
                else
                {
                    newQuery.append(parts[i]);
                }
            }

            return prefix + "?" + newQuery;
        }
        catch (Exception e)
        {
            return url;
        }
    }

    private static boolean looksLikeBase64(String s)
    {
        return s.length() >= 20 && s.matches("^[A-Za-z0-9+/=\\r\\n]+$");
    }

    private static String decodeOnce(String s)
    {
        try
        {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        }
        catch (Exception e)
        {
            return s;
        }
    }

    private static String encodePath(String path)
    {
        return Arrays.stream(path.split("/"))
                .map(UrlConsolidator::safeEncodeComponent)
                .collect(Collectors.joining("/"));
    }

    private static String encodeQuery(String query)
    {
        return Arrays.stream(query.split("&"))
                .map(UrlConsolidator::encodeQueryParam)
                .collect(Collectors.joining("&"));
    }

    private static String encodeQueryParam(String param)
    {
        int eq = param.indexOf('=');
        if (eq < 0)
            return safeEncodeComponent(param);
        return safeEncodeComponent(param.substring(0, eq)) + "=" + safeEncodeComponent(param.substring(eq + 1));
    }

    private static String safeEncodeComponent(String s)
    {
        try
        {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static String encodeIllegalCharacters(String url)
    {
        try
        {
            URL u = new URL(url);
            String protocol = u.getProtocol();
            String host = u.getHost();
            int port = u.getPort();
            String path = u.getPath();
            String query = u.getQuery();

            StringBuilder result = new StringBuilder();
            result.append(protocol).append("://").append(host);
            if (port != -1)
                result.append(":").append(port);

            for (String segment : path.split("/"))
            {
                if (!segment.isEmpty())
                {
                    result.append('/');
                    result.append(URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                            .replace("+", "%20")
                            .replace("%2F", "/"));
                }
            }
            if (path.endsWith("/"))
                result.append('/');

            if (query != null)
            {
                result.append('?');
                String[] params = query.split("&");
                for (int i = 0; i < params.length; i++)
                {
                    if (i > 0)
                        result.append('&');
                    int eq = params[i].indexOf('=');
                    if (eq >= 0)
                    {
                        result.append(URLEncoder.encode(params[i].substring(0, eq), StandardCharsets.UTF_8.name()))
                                .append('=')
                                .append(URLEncoder.encode(params[i].substring(eq + 1), StandardCharsets.UTF_8.name()));
                    }
                    else
                    {
                        result.append(URLEncoder.encode(params[i], StandardCharsets.UTF_8.name()));
                    }
                }
            }

            return result.toString();
        }
        catch (Exception e)
        {
            return url;
        }
    }
}