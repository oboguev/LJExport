package my.LJExport.runtime;

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;

import my.LJExport.Config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// GZIP: http://stackoverflow.com/questions/1063004/how-to-decompress-http-response

public class Web
{
    public static CloseableHttpClient httpClient;
    public static CookieStore cookieStore;
    private static ThreadLocal<String> lastURL;

    public static final int BINARY = (1 << 0);
    public static final int PROGRESS = (1 << 1);

    public static class Response
    {
        public int code;
        public String reason;
        public String body = new String("");
        public byte[] binaryBody;
    }

    public static void init() throws Exception
    {
        DefaultProxyRoutePlanner routePlanner = null;

        cookieStore = new BasicCookieStore();
        lastURL = new ThreadLocal<String>();

        if (Config.TrustStore != null)
        {
            System.setProperty("javax.net.ssl.trustStore", Config.TrustStore);
            if (Config.TrustStorePassword != null)
                System.setProperty("javax.net.ssl.trustStorePassword", Config.TrustStorePassword);
        }

        if (Config.Proxy != null)
        {
            URL url = new URL(Config.Proxy);
            String host = url.getHost();
            int port = url.getPort();

            // System.setProperty("proxyHost", host);
            // System.setProperty("proxyPort", "" + port);
            // System.setProperty("proxySet", "true");

            // System.setProperty("http.proxyHost", host);
            // System.setProperty("http.proxyPort", "" + port);
            // System.setProperty("http.proxySet", "true");

            // System.setProperty("https.proxyHost", host);
            // System.setProperty("https.proxyPort", "" + port);
            // System.setProperty("https.proxySet", "true");

            HttpHost proxy = new HttpHost(host, port);
            routePlanner = new DefaultProxyRoutePlanner(proxy);
        }

        // RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.NETSCAPE).build();
        RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build();

        HttpClientBuilder hcb = HttpClients.custom().setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore);

        if (routePlanner != null)
            hcb = hcb.setRoutePlanner(routePlanner);

        httpClient = hcb.build();
    }

    public static void shutdown() throws Exception
    {
        cookieStore = null;
        lastURL = null;

        if (httpClient != null)
        {
            httpClient.close();
            httpClient = null;
            // let Apache HttpClient to settle
            Thread.sleep(1500);
        }
    }

    public static CookieStore getCookieStore() throws Exception
    {
        return cookieStore;
    }

    public static Response get(String url) throws Exception
    {
        return get(url, 0, null);
    }

    public static Response get(String url, int flags, Map<String, String> headers) throws Exception
    {
        boolean binary = 0 != (flags & BINARY);
        boolean progress = 0 != (flags & PROGRESS);

        RateLimiter.limitRate();
        lastURL.set(url);
        Response r = new Response();

        HttpGet request = new HttpGet(url);
        setCommon(request);
        if (headers != null)
        {
            for (String key : headers.keySet())
                request.setHeader(key, headers.get(key));
        }

        ActivityCounters.startedWebRequest();
        CloseableHttpResponse response = httpClient.execute(request);

        try
        {
            r.code = response.getStatusLine().getStatusCode();
            r.reason = response.getStatusLine().getReasonPhrase();
            HttpEntity entity = response.getEntity();

            if (entity != null)
            {
                InputStream entityStream = null;
                BufferedReader brd = null;
                StringBuilder sb = new StringBuilder();
                String line;

                try
                {
                    if (binary && progress)
                    {
                        long totalBytes = entity.getContentLength();
                        ProgressHttpEntity progressEntity = new ProgressHttpEntity(entity, totalBytes);
                        entityStream = progressEntity.getContent();
                        r.binaryBody = IOUtils.toByteArray(entityStream);
                    }
                    else if (binary)
                    {
                        entityStream = entity.getContent();
                        r.binaryBody = IOUtils.toByteArray(entityStream);
                    }
                    else
                    {
                        entityStream = entity.getContent();
                        brd = new BufferedReader(new InputStreamReader(entityStream, StandardCharsets.UTF_8));
                        while ((line = brd.readLine()) != null)
                            sb.append(line + "\r\n");
                        r.body = sb.toString();
                    }
                }
                finally
                {
                    if (brd != null)
                        brd.close();
                    if (entityStream != null)
                        entityStream.close();
                }
            }
        }
        finally
        {
            response.close();
        }

        return r;
    }

    public static Response post(String url, String body) throws Exception
    {
        RateLimiter.limitRate();
        lastURL.set(url);
        Response r = new Response();

        HttpPost request = new HttpPost(url);
        setCommon(request);
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        ActivityCounters.startedWebRequest();
        CloseableHttpResponse response = httpClient.execute(request);

        try
        {
            r.code = response.getStatusLine().getStatusCode();
            r.reason = response.getStatusLine().getReasonPhrase();
            HttpEntity entity = response.getEntity();

            if (entity != null)
            {
                InputStream entityStream = null;
                BufferedReader brd = null;
                StringBuilder sb = new StringBuilder();
                String line;

                try
                {
                    entityStream = entity.getContent();
                    brd = new BufferedReader(new InputStreamReader(entityStream, StandardCharsets.UTF_8));
                    while ((line = brd.readLine()) != null)
                        sb.append(line + "\r\n");
                    r.body = sb.toString();
                }
                finally
                {
                    if (brd != null)
                        brd.close();
                    if (entityStream != null)
                        entityStream.close();
                }
            }
        }
        finally
        {
            response.close();
        }

        return r;
    }

    private static void setCommon(HttpRequestBase request) throws Exception
    {
        request.setHeader("User-Agent", Config.UserAgent);
        request.setHeader("Accept", Config.UserAgentAccept);
        request.setHeader("Accept-Encoding", Config.UserAgentAcceptEncoding);
        request.setHeader("Cache-Control", "no-cache");
        request.setHeader("Pragma", "no-cache");
    }

    public static String describe(int sc) throws Exception
    {
        return "HTTP status code " + sc;
    }

    public static String escape(String s) throws Exception
    {
        return StringEscapeUtils.escapeHtml4(s);
    }

    public static String unescape(String s) throws Exception
    {
        return StringEscapeUtils.unescapeHtml4(s);
    }

    public static String getLastURL() throws Exception
    {
        return lastURL.get();
    }

    /* ================================================================================= */

    // Custom HttpEntity to track download progress
    public static class ProgressHttpEntity extends HttpEntityWrapper
    {
        private long totalBytes;
        private long bytesRead = 0;

        public ProgressHttpEntity(HttpEntity entity, long totalBytes)
        {
            super(entity);
            this.totalBytes = totalBytes;
        }

        @Override
        public InputStream getContent() throws IOException
        {
            InputStream in = wrappedEntity.getContent();
            return new ProgressInputStream(in);
        }

        private class ProgressInputStream extends InputStream
        {
            private InputStream wrappedInputStream;
            private String threadNameBase = null;

            public ProgressInputStream(InputStream wrappedInputStream)
            {
                this.threadNameBase = Thread.currentThread().getName();
                this.wrappedInputStream = wrappedInputStream;
            }

            @Override
            public int read() throws IOException
            {
                int byteRead = wrappedInputStream.read();
                if (byteRead != -1)
                {
                    bytesRead++;
                    displayProgress();
                }
                return byteRead;
            }

            @Override
            public void close() throws IOException
            {
                Thread.currentThread().setName(this.threadNameBase);
                wrappedInputStream.close();
            }

            private void displayProgress()
            {
                if (this.threadNameBase != null)
                {
                    StringBuilder sb = new StringBuilder(this.threadNameBase + " "); 
                    sb.append(" downloaded " + bytesRead + " bytes");
                    if (totalBytes != -1)
                    {
                        int progress = (int) ((bytesRead * 100) / totalBytes);
                        sb.append(" (" + progress + "%)");
                    }
                    Thread.currentThread().setName(sb.toString());
                }
            }
        }
    }

    // Wrapper class for HttpEntity
    public static class HttpEntityWrapper implements HttpEntity
    {
        protected final HttpEntity wrappedEntity;

        public HttpEntityWrapper(HttpEntity entity)
        {
            this.wrappedEntity = entity;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void consumeContent() throws IOException
        {
            wrappedEntity.consumeContent();
        }

        @Override
        public InputStream getContent() throws IOException, UnsupportedOperationException
        {
            return wrappedEntity.getContent();
        }

        @Override
        public Header getContentEncoding()
        {
            return wrappedEntity.getContentEncoding();
        }

        @Override
        public long getContentLength()
        {
            return wrappedEntity.getContentLength();
        }

        @Override
        public boolean isChunked()
        {
            return wrappedEntity.isChunked();
        }

        @Override
        public boolean isRepeatable()
        {
            return wrappedEntity.isRepeatable();
        }

        @Override
        public boolean isStreaming()
        {
            return wrappedEntity.isStreaming();
        }

        @Override
        public void writeTo(OutputStream outstream) throws IOException
        {
            wrappedEntity.writeTo(outstream);
        }

        @Override
        public Header getContentType()
        {
            return wrappedEntity.getContentType();
        }
    }
}