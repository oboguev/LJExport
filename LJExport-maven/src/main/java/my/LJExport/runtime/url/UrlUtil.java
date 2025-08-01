package my.LJExport.runtime.url;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.LJExport.runtime.Util;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;

public class UrlUtil
{
    /*
     * Decode link taken from A.HREF or IMG.SRC or LINK.HREF
     */
    public static String decodeHtmlAttrLink(String htmlUrl)
    {
        if (htmlUrl == null)
            return null;

        return decodeUrl(htmlUrl);
    }

    public static String decodeUrl(String encodedUrl)
    {
        /*
         * URLDecoder decodes + as space, which is correct only for application/x-www-form-urlencoded (form data).
         * But in URLs like <a href=...> and <img src=...>, a literal + should stay +.
         * Thus temporarily replace '+' with its percent-encoded version
         */
        return URLDecoder.decode(encodedUrl.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    public static String decodeUrlForm(String encodedUrl)
    {
        /*
         * URLDecoder decodes + as space, which is correct only for application/x-www-form-urlencoded (form data).
         */
        return URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);
    }

    /* ================================================================================================== */

    public static String encodeSegment(String segment)
    {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /*
     * Encode / slash separated segments but not slashes themselves
     */
    public static String encodeSegments(String segments)
    {
        StringBuilder sb = new StringBuilder();
        for (String pc : segments.split("/"))
        {
            if (sb.length() != 0)
                sb.append("/");
            sb.append(encodeSegment(pc));
        }

        String result = sb.toString();
        if (segments.startsWith("/") && !result.startsWith("/"))
            result = "/" + result;

        return result;
    }

    /*
     * Encode link before putting in into A.HREF or IMG.SRC or LINK.HREF
     */
    public static String encodeUrlForHtmlAttr(String url) throws Exception
    {
        if (url == null)
            return null;

        // Parse manually instead of using new URI(url)
        int schemeEnd = url.indexOf("://");
        if (schemeEnd <= 0)
            throw new IllegalArgumentException("Invalid URL: no scheme");

        String scheme = url.substring(0, schemeEnd);
        String rest = url.substring(schemeEnd + 3); // skip "://"

        int pathStart = rest.indexOf('/');
        String authority, pathAndAfter;
        if (pathStart >= 0)
        {
            authority = rest.substring(0, pathStart);
            pathAndAfter = rest.substring(pathStart);
        }
        else
        {
            authority = rest;
            pathAndAfter = "";
        }

        String path, query = null, fragment = null;

        int queryIndex = pathAndAfter.indexOf('?');
        int fragmentIndex = pathAndAfter.indexOf('#');

        if (queryIndex >= 0)
        {
            path = pathAndAfter.substring(0, queryIndex);

            if (fragmentIndex >= 0 && fragmentIndex > queryIndex)
            {
                query = pathAndAfter.substring(queryIndex + 1, fragmentIndex);
                fragment = pathAndAfter.substring(fragmentIndex + 1);
            }
            else
            {
                query = pathAndAfter.substring(queryIndex + 1);
            }
        }
        else if (fragmentIndex >= 0)
        {
            path = pathAndAfter.substring(0, fragmentIndex);
            fragment = pathAndAfter.substring(fragmentIndex + 1);
        }
        else
        {
            path = pathAndAfter;
        }

        // Now encode components
        String encodedPath = encodePath(path);
        String encodedQuery = encodeQuery(query);
        String encodedFragment = encodeFragment(fragment);

        // Rebuild the URL
        StringBuilder result = new StringBuilder();
        result.append(scheme).append("://").append(authority).append(encodedPath);
        if (encodedQuery != null)
            result.append('?').append(encodedQuery);
        if (encodedFragment != null)
            result.append('#').append(encodedFragment);

        return result.toString();
    }

    // Encode path, preserving `/`
    private static String encodePath(String path)
    {
        if (path == null)
            return "";
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++)
        {
            parts[i] = encodeSegment(parts[i]);
        }
        return String.join("/", parts);
    }

    // Encode query (key=value&key2=value2)
    private static String encodeQuery(String query)
    {
        if (query == null)
            return null;
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++)
        {
            int eq = pairs[i].indexOf('=');
            if (eq >= 0)
            {
                String key = encodeSegment(pairs[i].substring(0, eq));
                String val = encodeSegment(pairs[i].substring(eq + 1));
                pairs[i] = key + "=" + val;
            }
            else
            {
                pairs[i] = encodeSegment(pairs[i]);
            }
        }
        return String.join("&", pairs);
    }

    // Encode fragment
    private static String encodeFragment(String fragment)
    {
        if (fragment == null)
            return null;
        else
            return encodeSegment(fragment);
    }

    /* ================================================================================================== */

    /*
     * Encode link before putting in into A.HREF or IMG.SRC or LINK.HREF.
     * If "safe" is true, preserves existing %xx without encoding it as %25xx
     * 
     * For example:
     * 
     *     https://web.archive.org/cdx/search/cdx?output=json&fl=timestamp,original,statuscode&filter=statuscode:200&matchType=exact&limit=1&url=http%3A%2F%2F1.bp.blogspot.com%2F_h_hLztz7W0s%2FSq0s6CwFrJI%2FAAAAAAAADX4%2FxfV04qkGa1A%2Fs1600-h%2FCheKa.JPG
     *     
     * should be transformed into
     * 
     *     https://web.archive.org/cdx/search/cdx?output=json&fl=timestamp%2Coriginal%2Cstatuscode&filter=statuscode%3A200&matchType=exact&limit=1&url=http%3A%2F%2F1.bp.blogspot.com%2F_h_hLztz7W0s%2FSq0s6CwFrJI%2FAAAAAAAADX4%2FxfV04qkGa1A%2Fs1600-h%2FCheKa.JPG
     *     
     * but not into
     * 
     *     https://web.archive.org/cdx/search/cdx?output=json&fl=timestamp%2Coriginal%2Cstatuscode&filter=statuscode%3A200&matchType=exact&limit=1&url=http%253A%252F%252F1.bp.blogspot.com%252F_h_hLztz7W0s%252FSq0s6CwFrJI%252FAAAAAAAADX4%252FxfV04qkGa1A%252Fs1600-h%252FCheKa.JPG
     * 
     */
    public static String encodeUrlForHtmlAttr(String url, boolean safe) throws Exception
    {
        if (url == null)
            return null;

        // Parse manually instead of using new URI(url)
        int schemeEnd = url.indexOf("://");
        if (schemeEnd <= 0)
            throw new IllegalArgumentException("Invalid URL: no scheme");

        String scheme = url.substring(0, schemeEnd);
        String rest = url.substring(schemeEnd + 3); // skip "://"

        int pathStart = rest.indexOf('/');
        String authority, pathAndAfter;
        if (pathStart >= 0)
        {
            authority = rest.substring(0, pathStart);
            pathAndAfter = rest.substring(pathStart);
        }
        else
        {
            authority = rest;
            pathAndAfter = "";
        }

        String path, query = null, fragment = null;

        int queryIndex = pathAndAfter.indexOf('?');
        int fragmentIndex = pathAndAfter.indexOf('#');

        if (queryIndex >= 0)
        {
            path = pathAndAfter.substring(0, queryIndex);

            if (fragmentIndex >= 0 && fragmentIndex > queryIndex)
            {
                query = pathAndAfter.substring(queryIndex + 1, fragmentIndex);
                fragment = pathAndAfter.substring(fragmentIndex + 1);
            }
            else
            {
                query = pathAndAfter.substring(queryIndex + 1);
            }
        }
        else if (fragmentIndex >= 0)
        {
            path = pathAndAfter.substring(0, fragmentIndex);
            fragment = pathAndAfter.substring(fragmentIndex + 1);
        }
        else
        {
            path = pathAndAfter;
        }

        // Now encode components
        String encodedPath = encodePath(path, safe);
        String encodedQuery = encodeQuery(query, safe);
        String encodedFragment = encodeFragment(fragment, safe);

        // Rebuild the URL
        StringBuilder result = new StringBuilder();
        result.append(scheme).append("://").append(authority).append(encodedPath);
        if (encodedQuery != null)
            result.append('?').append(encodedQuery);
        if (encodedFragment != null)
            result.append('#').append(encodedFragment);

        return result.toString();
    }

    // Encode path, preserving `/`
    private static String encodePath(String path, boolean safe)
    {
        if (path == null)
            return "";
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++)
        {
            parts[i] = encodeSegment(parts[i], safe);
        }
        return String.join("/", parts);
    }

    // Encode query (key=value&key2=value2)
    private static String encodeQuery(String query, boolean safe)
    {
        if (query == null)
            return null;
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++)
        {
            int eq = pairs[i].indexOf('=');
            if (eq >= 0)
            {
                String key = encodeSegment(pairs[i].substring(0, eq), safe);
                String val = encodeSegment(pairs[i].substring(eq + 1), safe);
                pairs[i] = key + "=" + val;
            }
            else
            {
                pairs[i] = encodeSegment(pairs[i], safe);
            }
        }
        return String.join("&", pairs);
    }

    // Encode fragment
    private static String encodeFragment(String fragment, boolean safe)
    {
        if (fragment == null)
            return null;
        else
            return encodeSegment(fragment, safe);
    }

    public static String encodeSegment(String segment, boolean safe)
    {
        if (segment == null)
            return "";

        if (!safe)
            return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");

        /*
         * "safe" encoding:
         */
        StringBuilder sb = new StringBuilder();
        int length = segment.length();
        for (int i = 0; i < length;)
        {
            char ch = segment.charAt(i);

            if (safe && ch == '%' && i + 2 < length &&
                    isHexDigit(segment.charAt(i + 1)) && isHexDigit(segment.charAt(i + 2)))
            {
                // Valid %xx sequence, preserve as-is
                sb.append(segment, i, i + 3);
                i += 3;
            }
            else if (isUnreserved(ch))
            {
                sb.append(ch);
                i++;
            }
            else
            {
                // Percent-encode using UTF-8 bytes
                int codePoint = Character.codePointAt(segment, i);
                byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes)
                {
                    sb.append('%');
                    sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                    sb.append(Character.forDigit(b & 0xF, 16));
                }
                i += Character.charCount(codePoint);
            }
        }

        return sb.toString();
    }

    private static boolean isHexDigit(char c)
    {
        return (c >= '0' && c <= '9') ||
                (c >= 'A' && c <= 'F') ||
                (c >= 'a' && c <= 'f');
    }

    /**
     * Unreserved characters as per RFC 3986 section 2.3 ALPHA / DIGIT / "-" / "." / "_" / "~"
     */
    private static boolean isUnreserved(char ch)
    {
        return (ch >= 'A' && ch <= 'Z') ||
                (ch >= 'a' && ch <= 'z') ||
                (ch >= '0' && ch <= '9') ||
                ch == '-' || ch == '.' || ch == '_' || ch == '~';
    }

    /* ================================================================================================== */

    /*
     * Encode URL link for use by Apache Http Client
     */
    public static String encodeUrlForApacheWire(String url) throws Exception
    {
        url = encodeUrlForHtmlAttr(url, true);
        url = Util.stripAnchor(url);
        url = normalizeSchemeHostPort(url);
        return url;
    }

    private static final Pattern schemeHostPortPattern = Pattern.compile("^(https?)://([^/:?#]+)(:\\d+)?(?=[/?#]|$)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Normalizes the scheme, host, and port part of a URL using String and Pattern operations.
     * 
     * <ul>
     * <li>Lowercases the scheme (e.g. "HTTPS" → "https")</li>
     * <li>Lowercases the host (e.g. "Example.COM" → "example.com")</li>
     * <li>Strips default ports (:80 for HTTP, :443 for HTTPS)</li>
     * <li>Leaves path, query, and fragment untouched</li>
     * <li>Does not perform any encoding or decoding</li>
     * </ul>
     *
     * <b>Example:</b>
     * 
     * <pre>{@code
     * String input = "HTTPS://Example.COM:443/Путь/Ресурс?q=значение#anchor";
     * String normalized = normalizeSchemeHostPort(input);
     * System.out.println(normalized);
     * // Output: https://example.com/Путь/Ресурс?q=значение#anchor
     * }</pre>
     *
     * @param url
     *            the input URL string
     * @return the normalized URL with lowercase scheme/host and default port removed
     */
    public static String normalizeSchemeHostPort(String url)
    {
        if (url == null)
            return null;

        Matcher matcher = schemeHostPortPattern.matcher(url);

        if (!matcher.find())
            return url; // No match — return unchanged

        String scheme = matcher.group(1).toLowerCase(); // http / https
        String host = matcher.group(2).toLowerCase(); // domain (lowercased)
        String port = matcher.group(3); // e.g., ":443" or ":8080"

        boolean isDefaultPort = (scheme.equals("http") && ":80".equals(port)) ||
                (scheme.equals("https") && ":443".equals(port));

        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (port != null && !isDefaultPort)
            sb.append(port);

        sb.append(url.substring(matcher.end())); // preserve path, query, fragment

        return sb.toString();
    }

    /* ================================================================================================== */

    private static final Pattern VALID_HOST_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    public static String extractHost(String href) throws Exception
    {
        if (href == null || href.trim().isEmpty())
            return null;

        String input = href.trim();

        // If it already has http or https, use URL directly
        if (input.matches("(?i)^https?://.*"))
        {
            URL url = new URL(input);
            String host = url.getHost();
            if (host == null || host.isEmpty())
                return null;
            return host;
        }

        // No scheme — treat as relative or scheme-less absolute
        int slash = input.indexOf('/');
        String firstComponent = (slash >= 0) ? input.substring(0, slash) : input;

        // Check if it looks like a valid domain name (must include dot)
        if (VALID_HOST_PATTERN.matcher(firstComponent).matches())
            return firstComponent;
        else
            return null;
    }

    /* ================================================================================================== */

    public static String extractQueryParameter(String url, String parameterName) throws Exception
    {
        URI uri = new URI(url);
        String query = uri.getRawQuery();
        if (query == null)
            return null;

        String[] pairs = query.split("&");
        for (String pair : pairs)
        {
            int idx = pair.indexOf('=');
            if (idx >= 0)
            {
                String rawKey = pair.substring(0, idx);
                String rawValue = pair.substring(idx + 1);

                String key = UrlUtil.decodeUrl(rawKey);
                if (key.equals(parameterName))
                {
                    return UrlUtil.decodeUrl(rawValue);
                }
            }
        }

        return null;
    }

    /* ================================================================================================== */

    /*
     * Encode only those characters not allowed in URL
     */
    public static String encodeMinimal(String input)
    {
        StringBuilder sb = new StringBuilder();

        for (char c : input.toCharArray())
        {
            if (isIllegalForURI(c))
            {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes)
                {
                    sb.append(String.format("%%%02X", b));
                }
            }
            else
            {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static boolean isIllegalForURI(char c)
    {
        return c <= 0x20 || c >= 0x7F || "\"<>\\^`{}|[]".indexOf(c) >= 0;
    }

    /* ================================================================================================== */

    private static final Pattern DEFAULT_PORT_PATTERN = Pattern.compile(
            "^(?i)(https?)(://[^/:]+):((?:80|443))(/.*|$)");

    /**
     * Removes the default port from an HTTP or HTTPS URL, if present.
     * <p>
     * Specifically:
     * <ul>
     * <li>Removes port 80 from HTTP URLs (e.g., {@code http://example.com:80/path} → {@code http://example.com/path})</li>
     * <li>Removes port 443 from HTTPS URLs (e.g., {@code https://example.com:443/path} → {@code https://example.com/path})</li>
     * <li>The comparison is case-insensitive (e.g., {@code HTTP://}, {@code Https://} are supported)</li>
     * <li>Returns the original URL unchanged if no default port is found or the URL doesn't match the expected pattern</li>
     * </ul>
     *
     * @param url
     *            the input URL string
     * @return the URL with the default port removed if applicable, or the original URL if not applicable
     */
    public static String stripDefaultPort(String url)
    {
        if (url == null)
            return null;

        Matcher m = DEFAULT_PORT_PATTERN.matcher(url);
        if (!m.find())
            return url;

        String scheme = m.group(1).toLowerCase();
        String host = m.group(2);
        String port = m.group(3);
        String rest = m.group(4);

        // Remove port only if it matches the default for the scheme
        if ((scheme.equals("http") && port.equals("80")) ||
                (scheme.equals("https") && port.equals("443")))
        {
            return scheme + host + rest;
        }

        return url;
    }

    public static String stripTrailingSlash(String url)
    {
        while (Util.lastChar(url) == '/')
        {
            String lc = url.toLowerCase();
            if (lc.equals("http://") || lc.equals("https://"))
                break;
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    /**
     * Returns the given path with its first segment lowercased. A "segment" is the part of the path up to the first '/'. If there
     * is no '/', the entire path is lowercased.
     *
     * Examples: "AAA/BBB" => "aaa/BBB" 
     *           "ZZZ" => "zzz" 
     *           "Mixed/Case/Path" => "mixed/Case/Path"
     *
     * @param path
     *            the original path string
     * @return the path with first segment lowercased
     */
    public static String lowercaseFirstSegment(String path)
    {
        if (path == null || path.isEmpty())
            return path;

        int slashIndex = path.indexOf('/');
        if (slashIndex == -1)
        {
            return path.toLowerCase(); // single segment
        }

        String firstSegment = path.substring(0, slashIndex).toLowerCase();
        String rest = path.substring(slashIndex); // keep '/' and rest as-is
        return firstSegment + rest;
    }
}
