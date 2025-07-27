package my.LJExport.runtime.url;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.URI;
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
     * Encode link before putting in into A.HREF or IMG.SRC or LINK.HREF
     */
    public static String encodeUrlForHtmlAttr(String url) throws Exception
    {
        if (url == null)
            return null;

        URI uri = new URI(url); // parsed into components

        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority(); // includes host[:port]
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        String fragment = uri.getRawFragment();

        // Encode each component properly
        String encodedPath = encodePath(path);
        String encodedQuery = encodeQuery(query);
        String encodedFragment = encodeFragment(fragment);

        // Rebuild the URL
        StringBuilder result = new StringBuilder();

        if (scheme != null)
        {
            result.append(scheme).append(":");
        }

        if (authority != null)
        {
            result.append("//").append(authority);
        }

        result.append(encodedPath);

        if (encodedQuery != null)
        {
            result.append('?').append(encodedQuery);
        }

        if (encodedFragment != null)
        {
            result.append('#').append(encodedFragment);
        }

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
}
