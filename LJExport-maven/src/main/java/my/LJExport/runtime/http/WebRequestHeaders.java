package my.LJExport.runtime.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpRequestBase;

import my.LJExport.Config;
import my.LJExport.runtime.lj.Sites;

public class WebRequestHeaders
{
    public static void setRequestHeaders(String url, HttpAccessMode httpAccessMode, HttpRequestBase request, Map<String, String> app_headers)
            throws Exception
    {
        String site = Sites.which(url);
        
        Map<String, String> headers = new HashMap<>();

        setHeader(headers, "User-Agent", Config.UserAgent);
        setHeader(headers, "Accept", Config.UserAgentAccept);
        setHeader(headers, "Accept-Language", "en-US,en;q=0.5");

        // setHeader(request, headers, "Accept-Encoding", Config.UserAgentAcceptEncoding);
        if (site.equals(Sites.Livejournal))
            setHeader(headers, "Accept-Encoding", "gzip, deflate, br, zstd");
        else
            setHeader(headers, "Accept-Encoding", "gzip, deflate");

        // setHeader(request, headers, "Cache-Control", "no-cache");
        // setHeader(request, headers, "Pragma", "no-cache");
        
        if (httpAccessMode != HttpAccessMode.DIRECT_VIA_HTTP)
            setHeader(headers, "Upgrade-Insecure-Requests", "1");

        setHeader(headers, "Priority", "u=0, i");
        setHeader(headers, "Sec-GPC", "1");
        setHeader(headers, "Connection", "keep-alive");

        setHeader(headers, "Sec-Fetch-Dest", "document");
        setHeader(headers, "Sec-Fetch-Mode", "navigate");
        setHeader(headers, "Sec-Fetch-Site", "none");
        setHeader(headers, "Sec-Fetch-User", "?1");
        
        // ### clear all ... and set
        // ### request.setHeader(key, value);

        if (headers != null)
        {
            for (String key : headers.keySet())
            {
                String value = headers.get(key);
                if (value != null)
                    request.setHeader(key, value);
                else
                    request.removeHeaders(key);
            }
        }

        orderHeaders(request,
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
    }
    
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
    /* ============================================================================== */

    private static void orderHeaders(HttpRequestBase request, String... preferredOrder)
    {
        if (request == null || preferredOrder == null)
            return;

        // Step 1: Extract all headers from the request
        Header[] allHeaders = request.getAllHeaders();
        Map<String, List<Header>> headerMap = new LinkedHashMap<>();

        for (Header h : allHeaders)
        {
            String name = h.getName();
            headerMap.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>()).add(h);
        }

        // Step 2: Clear all headers from request
        request.removeHeaders("*"); // doesn't remove all; do it manually
        for (Header h : allHeaders)
        {
            request.removeHeader(h);
        }

        Set<String> added = new HashSet<>();

        // Step 3: Re-add headers in preferred order
        for (String name : preferredOrder)
        {
            List<Header> headers = headerMap.get(name.toLowerCase());
            if (headers != null)
            {
                for (Header h : headers)
                {
                    request.addHeader(h);
                }
                added.add(name.toLowerCase());
            }
        }

        // Step 4: Append any extra headers not listed in preferredOrder
        for (Header h : allHeaders)
        {
            String lname = h.getName().toLowerCase();
            if (!added.contains(lname))
            {
                request.addHeader(h);
            }
        }
    }
}
