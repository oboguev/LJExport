package my.LJExport.runtime.http;

import java.util.ArrayList;
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
    public static void setCommon(String url, HttpAccessMode httpAccessMode, HttpRequestBase request, Map<String, String> headers)
            throws Exception
    {
        String site = Sites.which(url);

        setHeader(request, headers, "User-Agent", Config.UserAgent);
        setHeader(request, headers, "Accept", Config.UserAgentAccept);
        // setHeader(request, headers, "Accept-Encoding", Config.UserAgentAcceptEncoding);
        setHeader(request, headers, "Accept-Language", "en-US,en;q=0.5");

        if (site.equals(Sites.Livejournal))
            setHeader(request, headers, "Accept-Encoding", "gzip, deflate, br, zstd");
        else
            setHeader(request, headers, "Accept-Encoding", "gzip, deflate");

        // setHeader(request, headers, "Cache-Control", "no-cache");
        // setHeader(request, headers, "Pragma", "no-cache");
        if (httpAccessMode != HttpAccessMode.DIRECT_VIA_HTTP)
            setHeader(request, headers, "Upgrade-Insecure-Requests", "1");
        setHeader(request, headers, "Priority", "u=0, i");
        setHeader(request, headers, "Sec-GPC", "1");
        setHeader(request, headers, "Connection", "keep-alive");

        setHeader(request, headers, "Sec-Fetch-Dest", "document");
        setHeader(request, headers, "Sec-Fetch-Mode", "navigate");
        setHeader(request, headers, "Sec-Fetch-Site", "none");
        setHeader(request, headers, "Sec-Fetch-User", "?1");

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

    private static void setHeader(HttpRequestBase request, Map<String, String> headers, String key, String value) throws Exception
    {
        if (headers != null)
        {
            for (String k : headers.keySet())
            {
                if (k.equalsIgnoreCase(key))
                    return;
            }
        }

        request.setHeader(key, value);
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
