package my.LJExport.runtime.http.browserproxy;

import org.apache.http.message.BasicHeader;
import org.apache.http.impl.cookie.BasicClientCookie;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

class BrowserProxyNodeJs implements BrowserProxy
{
    private final String proxyEndpointUrl;
    private static final ObjectMapper mapper = new ObjectMapper();

    public BrowserProxyNodeJs(String proxyEndpointUrl)
    {
        this.proxyEndpointUrl = proxyEndpointUrl;
    }

    @Override
    public BrowserProxy.Response executePostRequest(String url, List<BasicHeader> headers, byte[] body, boolean followRedirects)
            throws Exception
    {
        if (!followRedirects)
            throw new Exception("BrowserProxyNodeJs does not support interception of redirects");

        return sendRequest("POST", url, headers, body);
    }

    @Override
    public BrowserProxy.Response executeGetRequest(String url, List<BasicHeader> headers, boolean followRedirects) throws Exception
    {
        if (!followRedirects)
            throw new Exception("BrowserProxyNodeJs does not support interception of redirects");

        return sendRequest("GET", url, headers, null);
    }

    private BrowserProxy.Response sendRequest(String method, String url, List<BasicHeader> headers, byte[] body) throws Exception
    {
        URL endpoint = new URL(proxyEndpointUrl + "/fetch");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Build request body
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("url", url);
        requestMap.put("method", method);

        Map<String, String> headerMap = new HashMap<>();
        if (headers != null)
        {
            for (BasicHeader h : headers)
                headerMap.put(h.getName(), h.getValue());
        }
        requestMap.put("headers", headerMap);

        if (body != null)
            requestMap.put("body", new String(body, StandardCharsets.UTF_8));

        byte[] jsonBodyAsBytes = mapper.writeValueAsBytes(requestMap);
        String jsonBodyAsString = mapper.writeValueAsString(requestMap);
        try (OutputStream os = connection.getOutputStream())
        {
            os.write(jsonBodyAsBytes);
        }

        int status = connection.getResponseCode();
        InputStream is = (status < 400) ? connection.getInputStream() : connection.getErrorStream();

        byte[] responseBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1)
            {
                baos.write(buffer, 0, read);
            }
            responseBytes = baos.toByteArray();
        }

        Map<String, Object> result = mapper.readValue(responseBytes, new TypeReference<Map<String, Object>>()
        {
        });

        BrowserProxy.Response response = new BrowserProxy.Response();
        response.status = (int) result.getOrDefault("status", status);

        List<BasicHeader> responseHeaders = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Map<String, String> headersMap = (Map<String, String>) result.get("headers");
        if (headersMap != null)
        {
            for (Map.Entry<String, String> e : headersMap.entrySet())
                responseHeaders.add(new BasicHeader(e.getKey(), e.getValue()));
        }
        response.headers = responseHeaders;

        String bodyStr = (String) result.get("body");
        response.body = (bodyStr != null) ? Base64.getDecoder().decode(bodyStr) : new byte[0];

        return response;
    }

    @Override
    public boolean canInterceptRedirection()
    {
        return false;
    }

    @Override
    public String getUserAgent() throws Exception
    {
        URL url = new URL(proxyEndpointUrl + "/useragent");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (InputStream is = conn.getInputStream())
        {
            Map<String, Object> map = mapper.readValue(is, new TypeReference<Map<String, Object>>()
            {
            });
            return (String) map.get("userAgent");
        }
    }

    @Override
    public String getSecChUa() throws Exception
    {
        URL url = new URL(proxyEndpointUrl + "/sec-ch-ua");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (InputStream is = conn.getInputStream())
        {
            Map<String, Object> map = mapper.readValue(is, new TypeReference<Map<String, Object>>()
            {
            });
            return (String) map.get("sec-ch-ua");
        }
    }

    @Override
    public List<BasicClientCookie> getCookies() throws Exception
    {
        URL url = new URL(proxyEndpointUrl + "/cookies");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (InputStream is = conn.getInputStream())
        {
            Map<String, Object> map = mapper.readValue(is, new TypeReference<Map<String, Object>>()
            {
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cookieList = (List<Map<String, Object>>) map.get("cookies");
            List<BasicClientCookie> result = new ArrayList<>();
            for (Map<String, Object> c : cookieList)
            {
                BasicClientCookie cookie = new BasicClientCookie((String) c.get("name"), (String) c.get("value"));
                cookie.setDomain((String) c.get("domain"));
                cookie.setPath((String) c.get("path"));
                if (c.containsKey("expiry") && c.get("expiry") != null)
                {
                    cookie.setExpiryDate(new java.util.Date(((Number) c.get("expiry")).longValue() * 1000));
                }
                result.add(cookie);
            }
            return result;
        }
    }
}