package my.LJExport.runtime.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.LJExport.runtime.url.UrlUtil;

public class MiscUrls
{
    public static String unwrapImagesGoogleCom(String url)
    {
        try
        {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();

            if (host == null || path == null)
                return url;

            // Normalize host
            String lowerHost = host.toLowerCase();
            if (!(lowerHost.equals("images.google.com") || lowerHost.endsWith(".images.google.com")))
                return url;

            if (!path.equals("/imgres"))
                return url;

            String query = uri.getRawQuery(); // use rawQuery to preserve % encoding
            if (query == null)
                return url;

            // Find imgurl parameter
            Pattern p = Pattern.compile("(^|&)" + "imgurl=([^&]+)");
            Matcher m = p.matcher(query);
            if (m.find())
            {
                String encodedImgUrl = m.group(2);
                return UrlUtil.decodeUrl(encodedImgUrl);
            }

            return url;
        }
        catch (Exception e)
        {
            return url;
        }
    }

    /* ===================================================================================== */

    /*
     * Leave only one of
     * 
     *     http://l.yimg.com/ea/img/-/110610/princephillip9_16v3b27-16v3b2r.jpg?x=400
     *     http://l.yimg.com/ea/img/-/110610/princephillip9_16v3b27-16v3b2r.jpg?x=400&q=80&n=1&sig=IVIUwUocou1mAO_5dcfh5Q--
     */
    public static void uniqYimgCom(List<String> urls)
    {
        Set<String> seenNormalizedPaths = new HashSet<>();
        String firstMatchingKey = null;
        ListIterator<String> iter = urls.listIterator();

        while (iter.hasNext())
        {
            String url = iter.next();
            URI uri;
            try
            {
                uri = new URI(url);
            }
            catch (URISyntaxException e)
            {
                continue; // leave unparseable URLs
            }

            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            String query = uri.getRawQuery();

            if (scheme == null || host == null || path == null || query == null)
            {
                continue;
            }

            // Normalize scheme and host
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            boolean isYimg = host.endsWith(".yimg.com");
            if (!isYimg)
                continue;

            // Normalize scheme: http and https are equivalent
            String key = "yimg://" + host + path;

            // Check for "x=400" in query
            Map<String, List<String>> queryMap = parseQuery(query);
            if (!queryMap.containsKey("x") || !queryMap.get("x").contains("400"))
            {
                continue;
            }

            if (firstMatchingKey == null)
            {
                firstMatchingKey = key;
                seenNormalizedPaths.add(key);
            }
            else if (key.equals(firstMatchingKey))
            {
                // Already recorded one — this is a duplicate under same path, remove
                iter.remove();
            }
        }
    }

    private static Map<String, List<String>> parseQuery(String query)
    {
        Map<String, List<String>> map = new HashMap<>();
        String[] pairs = query.split("&");
        
        for (String pair : pairs)
        {
            int idx = pair.indexOf('=');
            String k = (idx > 0) ? pair.substring(0, idx) : pair;
            String v = (idx > 0 && idx + 1 < pair.length()) ? pair.substring(idx + 1) : "";
            map.computeIfAbsent(k, kk -> new ArrayList<>()).add(v);
        }
        
        return map;
    }
}