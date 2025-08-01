package my.LJExport.runtime.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpRequestBase;

import my.LJExport.Config;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.lj.Sites;

public class WebRequestHeaders
{
    public static List<KVEntry> defineRequestHeaders(String url, HttpAccessMode httpAccessMode, Map<String, String> appHeaders)
            throws Exception
    {
        String site = Sites.which(url);

        Map<String, String> headerMap = new HashMap<>();

        setHeader(headerMap, "User-Agent", Config.UserAgent);
        setHeader(headerMap, "Accept", Config.UserAgentAccept);
        setHeader(headerMap, "Accept-Language", "en-US,en;q=0.5");

        // setHeader(request, headers, "Accept-Encoding", Config.UserAgentAcceptEncoding);
        if (site.equals(Sites.Livejournal))
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
        setHeader(headerMap, "Sec-Fetch-Site", "none");
        setHeader(headerMap, "Sec-Fetch-User", "?1");

        for (String key : appHeaders.keySet())
            setHeader(headerMap, key, appHeaders.get(key));

        List<KVEntry> headers = orderHeaders(headerMap,
                "User-Agent",
                "Accept",
                "Accept-Language",
                "Accept-Encoding",
                "Referer",
                "Sec-GPC",
                "Connection",
                "Upgrade-Insecure-Requests",
                "Sec-Fetch-Dest",
                "Sec-Fetch-Mode",
                "Sec-Fetch-Site",
                "Sec-Fetch-User",
                "Priority");

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
    
    private static List<KVEntry> orderHeaders(Map<String, String> headerMap, String ... names)
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
}
