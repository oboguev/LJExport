package my.LJExport.runtime.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpRequestBase;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.browsers.BrowserVersion;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.lj.Sites;

public class WebRequestHeaders
{
    private static final String UserAgentAcceptFirefox = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String UserAgentAcceptFirefox43 = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    private static final String UserAgentAcceptChrome = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";
    private static final String UserAgentAcceptChrome109 = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9";

    public static List<KVEntry> defineRequestHeaders(String url, HttpAccessMode httpAccessMode, Map<String, String> appHeaders)
            throws Exception
    {
        if (appHeaders == null)
            appHeaders = Map.of();

        String userAgent = appHeaders.get("User-Agent");
        if (userAgent == null)
            userAgent = Config.UserAgent;

        BrowserVersion v = BrowserVersion.parse(userAgent);

        if (v.brand.equals("Firefox") && v.version[0] <= 43)
            return defineFirefox43RequestHeaders(url, httpAccessMode, appHeaders, v);

        if (v.brand.equals("Chrome") && v.version[0] <= 109)
            return defineChrome109RequestHeaders(url, httpAccessMode, appHeaders, v);

        if (v.brand.equals("Firefox"))
            return defineFirefoxRequestHeaders(url, httpAccessMode, appHeaders, v);

        if (v.brand.equals("Chrome"))
            return defineChromeRequestHeaders(url, httpAccessMode, appHeaders, v);

        throw new Exception("Unsupported user agent: " + userAgent);
    }

    /* ============================================================================== */

    public static List<KVEntry> defineFirefox43RequestHeaders(String url, HttpAccessMode httpAccessMode,
            Map<String, String> appHeaders, BrowserVersion browserVersion)
            throws Exception
    {
        String host = new URL(url).getHost().toLowerCase();

        Map<String, String> headerMap = new HashMap<>();

        setHeader(headerMap, "Host", host);
        setHeader(headerMap, "User-Agent", Config.UserAgent);
        setHeader(headerMap, "Accept", UserAgentAcceptFirefox43);
        setHeader(headerMap, "Accept-Language", "en-US,en;q=0.5");
        setHeader(headerMap, "Accept-Encoding", "gzip, deflate");
        setHeader(headerMap, "Connection", "keep-alive");

        for (String key : appHeaders.keySet())
            setHeader(headerMap, key, appHeaders.get(key));
        
        headerMap.remove("Origin");

        List<KVEntry> headers = orderHeaders(headerMap,
                "Host",
                "User-Agent",
                "Accept",
                "Accept-Language",
                "Accept-Encoding",
                "Referer",
                // "Cookie"
                "Connection",
                "Content-Type"
        // "Content-Length"
        );

        return headers;
    }

    /* ============================================================================== */

    public static List<KVEntry> defineFirefoxRequestHeaders(String url, HttpAccessMode httpAccessMode,
            Map<String, String> appHeaders, BrowserVersion browserVersion)
            throws Exception
    {
        String site = Sites.which(url);
        String host = new URL(url).getHost().toLowerCase();

        Map<String, String> headerMap = new HashMap<>();

        setHeader(headerMap, "Host", host);
        setHeader(headerMap, "User-Agent", Config.UserAgent);
        setHeader(headerMap, "Accept", UserAgentAcceptFirefox);
        setHeader(headerMap, "Accept-Language", "en-US,en;q=0.5");

        // setHeader(request, headers, "Accept-Encoding", Config.UserAgentAcceptEncoding);
        if (site.equals(Sites.Livejournal) && Util.False)
            setHeader(headerMap, "Accept-Encoding", "gzip, deflate, br, zstd");
        else
            setHeader(headerMap, "Accept-Encoding", "gzip, deflate");

        // setHeader(request, headers, "Cache-Control", "no-cache");
        // setHeader(request, headers, "Pragma", "no-cache");

        if (httpAccessMode != HttpAccessMode.DIRECT_VIA_HTTP)
            setHeader(headerMap, "Upgrade-Insecure-Requests", "1");

        setHeader(headerMap, "Priority", "u=0, i");
        setHeader(headerMap, "Sec-GPC", "1");
        setHeader(headerMap, "Connection", "keep-alive");

        setHeader(headerMap, "Sec-Fetch-Dest", "document");
        setHeader(headerMap, "Sec-Fetch-Mode", "navigate");

        String secFetchSite = secFetchSite(url, appHeaders.get("Referer"));
        setHeader(headerMap, "Sec-Fetch-Site", secFetchSite);

        setHeader(headerMap, "Sec-Fetch-User", "?1");

        for (String key : appHeaders.keySet())
            setHeader(headerMap, key, appHeaders.get(key));

        List<KVEntry> headers = orderHeaders(headerMap,
                "Host",
                "User-Agent",
                "Accept",
                "Accept-Language",
                "Accept-Encoding",
                "Referer",
                "Content-Type",
                "X-Requested-With",
                "Content-Length",
                "Origin",
                "DNT",
                "Sec-GPC",
                "Connection",
                "Cookie",
                "Upgrade-Insecure-Requests",
                "Sec-Fetch-Dest",
                "Sec-Fetch-Mode",
                "Sec-Fetch-Site",
                "Sec-Fetch-User",
                "Priority");

        return headers;
    }

    /* ============================================================================== */

    public static List<KVEntry> defineChrome109RequestHeaders(String url, HttpAccessMode httpAccessMode,
            Map<String, String> appHeaders, BrowserVersion browserVersion)
            throws Exception
    {
        String site = Sites.which(url);
        String host = new URL(url).getHost().toLowerCase();

        Map<String, String> headerMap = new HashMap<>();

        setHeader(headerMap, "Host", host);
        setHeader(headerMap, "User-Agent", Config.UserAgent);
        setHeader(headerMap, "Accept", UserAgentAcceptChrome109);
        setHeader(headerMap, "Accept-Language", "en-US,en;q=0.9");

        // setHeader(request, headers, "Accept-Encoding", Config.UserAgentAcceptEncoding);
        if (site.equals(Sites.Livejournal))
            setHeader(headerMap, "Accept-Encoding", "gzip, deflate, br");
        else
            setHeader(headerMap, "Accept-Encoding", "gzip, deflate");

        // setHeader(request, headers, "Cache-Control", "no-cache");
        // setHeader(request, headers, "Pragma", "no-cache");

        if (httpAccessMode != HttpAccessMode.DIRECT_VIA_HTTP)
            setHeader(headerMap, "Upgrade-Insecure-Requests", "1");

        // setHeader(headerMap, "Priority", "u=0, i");
        // setHeader(headerMap, "Sec-GPC", "1");
        setHeader(headerMap, "Connection", "keep-alive");

        setHeader(headerMap, "Sec-Fetch-Dest", "document");
        setHeader(headerMap, "Sec-Fetch-Mode", "navigate");

        String secFetchSite = secFetchSite(url, appHeaders.get("Referer"));
        setHeader(headerMap, "Sec-Fetch-Site", secFetchSite);

        setHeader(headerMap, "Sec-Fetch-User", "?1");

        int majorVersion = browserVersion.version[0];
        String sec_ch_ua = String.format("\"Not)A;Brand\";v=\"99\", \"Chromium\";v=\"%d\", \"Google Chrome\";v=\"%d\"",
                majorVersion,
                majorVersion);
        setHeader(headerMap, "sec-ch-ua", sec_ch_ua);

        setHeader(headerMap, "sec-ch-ua-mobile", "?0");
        setHeader(headerMap, "sec-ch-ua-platform", "\"Windows\"");

        for (String key : appHeaders.keySet())
            setHeader(headerMap, key, appHeaders.get(key));

        List<KVEntry> headers = orderHeaders(headerMap,
                "Host",
                "Connection",
                "Content-Length",
                "Cache-Control",
                "sec-ch-ua",
                "sec-ch-ua-mobile",
                "sec-ch-ua-platform",
                "Upgrade-Insecure-Requests",
                "Origin",
                "Content-Type",
                "User-Agent",
                "Accept",
                "Sec-Fetch-Site",
                "Sec-Fetch-Mode",
                "Sec-Fetch-User",
                "Sec-Fetch-Dest",
                "Referer",
                "Accept-Encoding",
                "Accept-Language"
        /* "Cookie" */);

        return headers;
    }

    /* ============================================================================== */

    public static List<KVEntry> defineChromeRequestHeaders(String url, HttpAccessMode httpAccessMode,
            Map<String, String> appHeaders, BrowserVersion browserVersion)
            throws Exception
    {
        String site = Sites.which(url);
        String host = new URL(url).getHost().toLowerCase();

        Map<String, String> headerMap = new HashMap<>();

        setHeader(headerMap, "Host", host);
        setHeader(headerMap, "User-Agent", Config.UserAgent);
        setHeader(headerMap, "Accept", UserAgentAcceptChrome);
        setHeader(headerMap, "Accept-Language", "en-US,en;q=0.9");

        // setHeader(request, headers, "Accept-Encoding", Config.UserAgentAcceptEncoding);
        if (site.equals(Sites.Livejournal))
            setHeader(headerMap, "Accept-Encoding", "gzip, deflate, br, zstd");
        else
            setHeader(headerMap, "Accept-Encoding", "gzip, deflate");

        // setHeader(request, headers, "Cache-Control", "no-cache");
        // setHeader(request, headers, "Pragma", "no-cache");

        if (httpAccessMode != HttpAccessMode.DIRECT_VIA_HTTP)
            setHeader(headerMap, "Upgrade-Insecure-Requests", "1");

        // setHeader(headerMap, "Priority", "u=0, i");
        // setHeader(headerMap, "Sec-GPC", "1");
        setHeader(headerMap, "Connection", "keep-alive");

        setHeader(headerMap, "Sec-Fetch-Dest", "document");
        setHeader(headerMap, "Sec-Fetch-Mode", "navigate");

        String secFetchSite = secFetchSite(url, appHeaders.get("Referer"));
        setHeader(headerMap, "Sec-Fetch-Site", secFetchSite);

        setHeader(headerMap, "Sec-Fetch-User", "?1");

        int majorVersion = browserVersion.version[0];
        String sec_ch_ua = String.format("\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"%d\", \"Google Chrome\";v=\"%d\"",
                majorVersion,
                majorVersion);
        setHeader(headerMap, "sec-ch-ua", sec_ch_ua);

        setHeader(headerMap, "sec-ch-ua-mobile", "?0");
        setHeader(headerMap, "sec-ch-ua-platform", "\"Windows\"");

        for (String key : appHeaders.keySet())
            setHeader(headerMap, key, appHeaders.get(key));

        List<KVEntry> headers = orderHeaders(headerMap,
                "Host",
                "Connection",
                "Upgrade-Insecure-Requests",
                "User-Agent",
                "Accept",
                "Sec-Fetch-Site",
                "Sec-Fetch-Mode",
                "Sec-Fetch-User",
                "Sec-Fetch-Dest",
                "Referer",
                "sec-ch-ua",
                "sec-ch-ua-mobile",
                "sec-ch-ua-platform",
                "Accept-Encoding",
                "Accept-Language");

        return headers;
    }

    /* ============================================================================== */

    public static void setRequestHeaders(String url, HttpAccessMode httpAccessMode, HttpRequestBase request,
            Map<String, String> appHeaders)
            throws Exception
    {
        List<KVEntry> headers = defineRequestHeaders(url, httpAccessMode, appHeaders);
        setHeaders(request, headers);
    }

    /* ============================================================================== */

    private static void setHeader(Map<String, String> headers, String key, String value) throws Exception
    {
        Set<String> deleteKeys = new HashSet<>();

        for (String k : headers.keySet())
        {
            if (k.equalsIgnoreCase(key))
                deleteKeys.add(k);
        }

        for (String k : deleteKeys)
            headers.remove(k);

        headers.put(key, value);
    }

    private static List<KVEntry> orderHeaders(Map<String, String> headerMap, String... names)
    {
        List<KVEntry> list = new ArrayList<>();

        headerMap = new HashMap<>(headerMap);

        for (String key : names)
        {
            String value = headerMap.get(key);
            headerMap.remove(key);
            if (value != null)
                list.add(new KVEntry(key, value));
        }

        for (String key : headerMap.keySet())
        {
            String value = headerMap.get(key);
            if (value != null)
                list.add(new KVEntry(key, value));
        }

        return list;
    }

    private static void setHeaders(HttpRequestBase request, List<KVEntry> headers)
    {
        for (Header h : request.getAllHeaders().clone())
            request.removeHeader(h);

        for (KVEntry e : headers)
            request.setHeader(e.key, e.value);
    }

    /* ============================================================================== */

    public static void setOrigin(Map<String, String> headerMap)
    {
        String referer = headerMap.get("Referer");
        
        if (Config.LoginSite == null || Config.LoginSite.length() == 0)
            Util.noop();
        else if (referer == null || referer.length() == 0)
            headerMap.remove("Origin");
        else
            headerMap.put("Origin", String.format("https://www.%s", Config.LoginSite));
    }

    /* ============================================================================== */

    /**
     * Determines the appropriate Sec-Fetch-Site value based on target and referer.
     * 
     * @param targetUrl
     *            the URL of the requested resource
     * @param referer
     *            the URL of the document making the request (can be null)
     * @return "none", "same-origin", "same-site", or "cross-site"
     */
    public static String secFetchSite(String targetUrl, String referer)
    {
        if (referer == null || referer.isEmpty())
        {
            return "none";
        }

        try
        {
            URI target = new URI(targetUrl);
            URI source = new URI(referer);

            // Compare origin (scheme + host + port)
            if (sameOrigin(target, source))
            {
                return "same-origin";
            }

            // Compare site (registrable domain)
            if (sameSite(target.getHost(), source.getHost()))
            {
                return "same-site";
            }

            return "cross-site";

        }
        catch (URISyntaxException e)
        {
            return "none"; // fall back to safest option
        }
    }

    private static boolean sameOrigin(URI u1, URI u2)
    {
        return u1.getScheme().equalsIgnoreCase(u2.getScheme()) &&
                u1.getHost().equalsIgnoreCase(u2.getHost()) &&
                getPort(u1) == getPort(u2);
    }

    private static int getPort(URI uri)
    {
        int port = uri.getPort();
        if (port != -1)
            return port;
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    /**
     * Basic heuristic: compares eTLD+1 by stripping subdomains. For full accuracy, use a public suffix list (like via Google Guava
     * or Mozilla PSL).
     */
    private static boolean sameSite(String h1, String h2)
    {
        return getRegistrableDomain(h1).equalsIgnoreCase(getRegistrableDomain(h2));
    }

    /**
     * Very naive registrable domain extractor: takes last two labels. Replace with public suffix list logic for production-grade
     * use.
     */
    private static String getRegistrableDomain(String host)
    {
        if (host == null)
            return "";
        String[] parts = host.toLowerCase().split("\\.");
        if (parts.length < 2)
            return host;
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    // Test
    public static void main(String[] args)
    {
        System.out.println(secFetchSite("https://example.com/page", null)); // none
        System.out.println(secFetchSite("https://example.com/page", "https://example.com/start")); // same-origin
        System.out.println(secFetchSite("https://a.example.com/page", "https://b.example.com/start")); // same-site
        System.out.println(secFetchSite("https://example.com", "https://other.com")); // cross-site
    }
}
