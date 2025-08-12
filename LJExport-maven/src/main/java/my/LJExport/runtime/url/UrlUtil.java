package my.LJExport.runtime.url;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.LJExport.runtime.Util;

import java.net.URI;
import java.net.URISyntaxException;
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

    public static String decodeOrNullHtmlAttrLink(String htmlUrl)
    {
        try
        {
            return decodeHtmlAttrLink(htmlUrl);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    public static String decodeUrl(String encodedUrl)
    {
        encodedUrl = Util.trimWithNBSP(encodedUrl);
        
        // convert any legacy %uXXXX to %XX
        encodedUrl = LegacyPercentUEncoding.normalizeEncodedSafe(encodedUrl);

        // if it is already decoded
        if (!containsPercentEncoding(encodedUrl))
            return encodedUrl;

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

    // Precompiled regex: % followed by exactly two hex digits
    private static final Pattern PERCENT_ENCODING = Pattern.compile("%[0-9A-Fa-f]{2}");

    /**
     * Checks whether the given string contains at least one valid %XX percent-encoding sequence.
     * @param url the string to check
     * @return true if a valid percent-encoding sequence is found, false otherwise
     */
    public static boolean containsPercentEncoding(String url)
    {
        if (url == null || url.isEmpty())
            return false;
        return PERCENT_ENCODING.matcher(url).find();
    }

    /* ================================================================================================== */

    public static String encodeSegment(String segment)
    {
        /* 
         * We should encode spaces as %20 in path or queries,
         * or as + in query 
         */
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
        /*
         * We do not need to encode Unicode host name since 
         * Apache automatically encodes it to punycode.
         */
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

    public static String extractHostLowercase(String href)
    {
        String host = extractHost(href);
        if (host != null)
            host = host.toLowerCase();
        return host;
    }

    // RFC-ish domain (punycode ok), requires at least one dot
    private static final Pattern DOMAIN_PATTERN = Pattern
            .compile("(?i)^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])\\.?$");

    // More permissive: allow underscores, and allow hyphens anywhere
    private static final Pattern DOMAIN_PATTERN_RELAXED = Pattern
            .compile("(?i)^(?=.{1,253}$)(?:[a-z0-9_-]{1,63}\\.)+[a-z0-9_-]{1,63}\\.?$");

    // IPv4 like 1.2.3.4
    private static final Pattern IPV4_PATTERN = Pattern
            .compile("^(?:(25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$");

    // Very loose check for IPv6 literal content (without brackets)
    private static final Pattern LOOSE_IPV6_CONTENT = Pattern.compile("^[0-9a-fA-F:.]+$");

    /**
     * Extract host from possibly-malformed URL-ish string.
     * Returns null if a plausible host cannot be found.
     */
    public static String extractHost(String href)
    {
        if (href == null)
            return null;

        String s = Util.trimWithNBSP(href);
        if (s.isEmpty())
            return null;

        s = Util.stripPrefixesIgnoreCase(s, false, "http://", "https://", "//");
        if (s.isEmpty())
            return null;

        String authority = sliceUntilAny(s, 0, '/', '?', '#');

        if (authority == null || authority.isEmpty())
            return null;

        // If IPv6 literal: [::1]:443
        if (authority.startsWith("["))
        {
            int rb = authority.indexOf(']');
            if (rb > 0)
            {
                String v6 = authority.substring(1, rb);
                if (!v6.isEmpty() && LOOSE_IPV6_CONTENT.matcher(v6).matches())
                {
                    return v6.toLowerCase(); // return IPv6 content without brackets
                }
                else
                {
                    return null;
                }
            }
            else
            {
                return null; // malformed bracketed literal
            }
        }

        // Strip :port if present (only first ':', since hostnames don’t contain it)
        int colon = authority.indexOf(':');
        String host = (colon >= 0) ? authority.substring(0, colon) : authority;

        // Drop trailing dot (FQDN) for comparison; keep empty-check
        if (host.endsWith("."))
            host = host.substring(0, host.length() - 1);

        if (host.isEmpty())
            return null;

        // Validate: IPv4 or domain
        if (IPV4_PATTERN.matcher(host).matches())
            return host;

        // Normalize case for domains
        String lower = host.toLowerCase();

        if (DOMAIN_PATTERN_RELAXED.matcher(lower).matches())
            return lower;

        Util.unused(DOMAIN_PATTERN);

        // Optional: treat "localhost" as valid. Comment out if you strictly require a dot.
        if (Util.False && "localhost".equalsIgnoreCase(lower))
            return "localhost";

        return null;
    }

    private static String sliceUntilAny(String s, int start, char... stops)
    {
        int end = s.length();

        for (int i = start; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            for (char stop : stops)
            {
                if (ch == stop)
                {
                    end = i;
                    return s.substring(start, end);
                }
            }
        }

        return (start <= s.length()) ? s.substring(start) : null;
    }

    /* ================================================================================================== */

    private static final Pattern VALID_HOST_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    public static String old_extractHost(String href) throws Exception
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
     * Encode only those characters not allowed in URL.
     * Do not double-encode %.
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

    /* ================================================================================================== */

    public static URI URLtoURI(URL url) throws Exception
    {
        String encodedQuery = encodeSegment(url.getQuery());

        URI uri = new URI(
                url.getProtocol(),
                url.getAuthority(), // host + port
                url.getPath(),
                encodedQuery,
                url.getRef() // fragment
        );

        return uri;
    }

    /* ================================================================================================== */

    private static final Pattern HOST_PATH_PATTERN = Pattern.compile(
                                                                     "^" +
                                                                     // Hostname: one or more domain labels separated by dots
                                                                             "([a-zA-Z0-9\\-]+\\.)+[a-zA-Z]{2,}" +
                                                                             // Optional port
                                                                             "(?::\\d{1,5})?" +
                                                                             // Followed by at least one slash and path component
                                                                             "(/[^\\s]*)?$");

    public static boolean looksLikeUrlWithoutScheme(String input)
    {
        if (input == null)
            return false;

        input = input.trim();
        if (input.isEmpty() || !input.contains("/"))
            return false;

        // Must not already start with scheme
        if (input.matches("^(?i)(https?|ftp)://.*"))
            return false;

        Matcher matcher = HOST_PATH_PATTERN.matcher(input);
        return matcher.find();
    }

    /* ================================================================================================== */

    public static boolean isAbsoluteURL(String url)
    {
        if (url == null || url.trim().isEmpty())
            return false;

        try
        {
            // First parse using URL (more lenient)
            // http://alexey_ivanov.users.photofile.ru/photo/alexey_ivanov/96510278/xlarge/119913775.jpg
            URL parsedUrl = new URL(url.replace("_", "-"));

            // Reconstruct the URI safely
            String scheme = parsedUrl.getProtocol();
            String host = parsedUrl.getHost();
            int port = parsedUrl.getPort();
            String path = parsedUrl.getPath();
            String query = parsedUrl.getQuery();

            if (scheme == null || host == null || host.trim().isEmpty())
                return false;

            String schemeLower = scheme.toLowerCase();
            if (!schemeLower.equals("http") && !schemeLower.equals("https"))
                throw new RuntimeException("Unexpected URL scheme " + scheme + "://");

            // Encode the path
            String[] parts = path.split("/");
            StringBuilder encodedPath = new StringBuilder();
            for (String part : parts)
            {
                if (!part.isEmpty())
                {
                    encodedPath.append('/').append(UrlUtil.encodeSegment(part));
                }
            }
            if (path.endsWith("/"))
                encodedPath.append('/');

            URI safeUri = new URI(
                    scheme,
                    null,
                    host,
                    port,
                    encodedPath.toString(),
                    query,
                    null);

            // Final validation on path
            String finalPath = safeUri.getPath();
            return finalPath == null || finalPath.trim().isEmpty() || finalPath.startsWith("/");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /* ================================================================================================== */

    public static boolean isSameURL(String url1, String url2)
    {
        try
        {
            URI uri1 = new URI(url1);
            URI uri2 = new URI(url2);

            return isSameURL(uri1, uri2);
        }
        catch (URISyntaxException e)
        {
            return false;
        }
    }

    public static boolean isSameURL(URI uri1, URI uri2)
    {
        // Compare scheme and host case-insensitively
        if (!equalsIgnoreCase(uri1.getScheme(), uri2.getScheme()))
            return false;

        if (!equalsIgnoreCase(uri1.getHost(), uri2.getHost()))
            return false;

        // Compare port (default ports need normalization if desired)
        if (uri1.getPort() != uri2.getPort())
            return false;

        // Compare path, query, and fragment case-sensitively
        if (!Objects.equals(uri1.getPath(), uri2.getPath()))
            return false;

        if (!Objects.equals(uri1.getQuery(), uri2.getQuery()))
            return false;

        if (!Objects.equals(uri1.getFragment(), uri2.getFragment()))
            return false;

        return true;
    }

    private static boolean equalsIgnoreCase(String a, String b)
    {
        return (a == null && b == null) || (a != null && a.equalsIgnoreCase(b));
    }

    /* ================================================================================================== */

    public static String resolveURL(String baseURL, String relativeURL) throws Exception
    {
        if (baseURL != null)
            baseURL = baseURL.trim();

        if (relativeURL != null)
            relativeURL = relativeURL.trim();

        if (relativeURL != null)
        {
            if (relativeURL.startsWith("data:"))
                return relativeURL;

            relativeURL = encodeFragmentInUrl(relativeURL);
            /* Windows backslashes in some old HTML files, e.g. ..\index.html */
            relativeURL = relativeURL.replace("\\", "/");
        }

        if (baseURL == null || baseURL.isEmpty())
            return relativeURL;

        // Handle protocol-relative URLs (e.g., //cdn.example.com/script.js)
        if (relativeURL != null && relativeURL.startsWith("//"))
        {
            if (baseURL != null && !baseURL.isEmpty())
            {
                URI baseUri = new URI(baseURL);
                String scheme = baseUri.getScheme();
                if (scheme != null && !scheme.isEmpty())
                    return scheme + ":" + relativeURL;
            }

            // If baseURL is missing or lacks scheme, fallback (e.g., assume "https:")
            return "https:" + relativeURL;
        }

        // Handle archive.org URLs with embedded full URLs in the path
        final String archive_org_https_web = "https://web.archive.org/web/";
        if (baseURL.startsWith(archive_org_https_web))
        {
            int schemeIndex = baseURL.indexOf("http://", archive_org_https_web.length());
            if (schemeIndex == -1)
                schemeIndex = baseURL.indexOf("https://", archive_org_https_web.length());

            if (schemeIndex != -1)
            {
                String archivePrefix = baseURL.substring(0, schemeIndex);
                String archivedURL = baseURL.substring(schemeIndex);

                URI archivedBase = new URI(archivedURL);
                URI resolved = archivedBase.resolve(relativeURL);

                return archivePrefix + resolved.toString();
            }
        }

        // Default case
        try
        {
            URI base = new URI(baseURL);
            URI resolved = base.resolve(relativeURL);
            return resolved.toString();
        }
        catch (Exception ex)
        {
            throw ex;
        }
    }

    private static String encodeFragmentInUrl(String url) throws Exception
    {
        int hashIndex = url.indexOf('#');
        if (hashIndex == -1)
            return url;

        String beforeFragment = url.substring(0, hashIndex);
        String fragment = url.substring(hashIndex + 1);

        // Encode using UTF-8 and percent-encode
        String encodedFragment = UrlUtil.encodeSegment(fragment);
        return beforeFragment + "#" + encodedFragment;
    }
}
