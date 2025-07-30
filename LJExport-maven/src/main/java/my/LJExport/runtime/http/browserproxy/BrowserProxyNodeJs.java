package my.LJExport.runtime.http.browserproxy;

import org.apache.http.message.BasicHeader;
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
    public BrowserProxy.Response executePostRequest(String url, List<BasicHeader> headers, byte[] body) throws Exception
    {
        return sendRequest("POST", url, headers, body);
    }

    @Override
    public BrowserProxy.Response executeGetRequest(String url, List<BasicHeader> headers) throws Exception
    {
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

        byte[] jsonBody = mapper.writeValueAsBytes(requestMap);
        try (OutputStream os = connection.getOutputStream())
        {
            os.write(jsonBody);
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
}