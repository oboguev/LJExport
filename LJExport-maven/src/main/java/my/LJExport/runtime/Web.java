package my.LJExport.runtime;

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NoHttpResponseException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import my.LJExport.Config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;

import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.IntPredicate;
import static java.lang.Math.max;

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

        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        // Set max total connections
        connManager.setMaxTotal(200);
        // Set max connections per route (i.e., per host)
        connManager.setDefaultMaxPerRoute(Config.MaxConnectionsPerRoute);

        // higher limits for some routes
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("l-userpic.livejournal.com", 80, "http")),
                max(15, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("ic.pics.livejournal.com", 80, "http")),
                max(10, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("pics.livejournal.com", 80, "http")),
                max(10, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("pics.livejournal.com", 443, "https")),
                max(10, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("imgprx.livejournal.net", 80, "http")),
                max(10, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("imgprx.livejournal.net", 443, "https")),
                max(10, Config.MaxConnectionsPerRoute));

        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("lh3.googleusercontent.com", 443, "https")),
                max(20, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("lh4.googleusercontent.com", 443, "https")),
                max(20, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("lh5.googleusercontent.com", 443, "https")),
                max(20, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("lh6.googleusercontent.com", 443, "https")),
                max(20, Config.MaxConnectionsPerRoute));

        connManager.setDefaultSocketConfig(SocketConfig.custom()
                // .setTcpNoDelay(true)
                .setSndBufSize(65536) // 64KB send buffer
                .setRcvBufSize(65536) // 64KB receive buffer
                .build());

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setBufferSize(65536) // 64KB buffer
                .build();
        connManager.setDefaultConnectionConfig(connectionConfig);

        HttpClientBuilder hcb = HttpClients.custom().setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore)
                .setConnectionManager(connManager);

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
        return get(url, 0, null, null);
    }

    public static Response get(String url, int flags, Map<String, String> headers, IntPredicate shouldLoadBody) throws Exception
    {
        final int maxpasses = 3;

        for (int pass = 1;; pass++)
        {
            try
            {
                return get_retry(url, flags, headers, shouldLoadBody);
            }
            catch (Exception ex)
            {
                if (pass <= maxpasses && isRetriable(ex))
                {
                    retryDelay(pass);
                    continue;
                }
                else
                {
                    throw ex;
                }
            }
        }
    }

    private static boolean isRetriable(Exception ex)
    {
        String msg = ex.getLocalizedMessage();
        if (msg == null)
            msg = "";

        if (ex instanceof NoHttpResponseException)
            return true;

        if (ex instanceof SocketException
                && msg.contains("An established connection was aborted by the software in your host machine"))
            return true;

        return false;
    }

    private static void retryDelay(int pass)
    {
        try
        {
            Thread.sleep(500 + 1000 * (pass + 1));
        }
        catch (InterruptedException ex)
        {
            Util.noop();
        }
    }

    private static Response get_retry(String url, int flags, Map<String, String> headers, IntPredicate shouldLoadBody)
            throws Exception
    {
        boolean binary = 0 != (flags & BINARY);
        boolean progress = 0 != (flags & PROGRESS);

        if (isLivejournalPicture(url))
        {
            RateLimiter.LJ_IMAGES.limitRate();
        }
        else if (shouldLimitRate(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
        }

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

            if (shouldLoadBody == null || shouldLoadBody.test(r.code))
            {
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
                            r.binaryBody = toByteArray(entityStream);
                        }
                        else if (binary)
                        {
                            entityStream = entity.getContent();
                            r.binaryBody = toByteArray(entityStream);
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
        }
        finally
        {
            response.close();
        }

        return r;
    }

    public static Response post(String url, String body) throws Exception
    {
        if (shouldLimitRate(url))
            RateLimiter.LJ_PAGES.limitRate();

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

    private static boolean isLivejournalPicture(String url) throws Exception
    {
        String host = (new URL(url)).getHost();
        host = host.toLowerCase();

        if (host.equals("l-userpic.livejournal.com") || host.endsWith(".l-userpic.livejournal.com"))
            return true;

        if (host.equals("pics.livejournal.com") || host.equals("ic.pics.livejournal.com") || host.endsWith(".pics.livejournal.com"))
            return true;

        if (host.equals("imgprx.livejournal.net"))
            return true;

        return false;

    }

    private static boolean shouldLimitRate(String url) throws Exception
    {
        String host = (new URL(url)).getHost();
        host = host.toLowerCase();

        if (host.equals("livejournal.net") || host.endsWith(".livejournal.net"))
            return true;
        if (host.equals("livejournal.com") || host.endsWith(".livejournal.com"))
            return true;

        return false;
    }

    private static byte[] toByteArray(InputStream input) throws IOException
    {
        if (input == null)
            throw new IllegalArgumentException("Input stream must not be null");

        if (Config.False)
        {
            return IOUtils.toByteArray(input);

        }
        else
        {
            final int BUFFER_SIZE = 64 * 1024; // 64KB buffer

            try (ByteArrayOutputStream output = new ByteArrayOutputStream(2 * BUFFER_SIZE))
            {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1)
                    output.write(buffer, 0, bytesRead);

                return output.toByteArray();
            }
        }
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
            public int read(byte[] b) throws IOException
            {
                return read(b, 0, b.length);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException
            {
                int bytesReadThisTime = wrappedInputStream.read(b, off, len);
                if (bytesReadThisTime > 0)
                {
                    bytesRead += bytesReadThisTime;
                    displayProgress();
                }
                return bytesReadThisTime;
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
                    sb.append(String.format(" downloaded %,d bytes", bytesRead));
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