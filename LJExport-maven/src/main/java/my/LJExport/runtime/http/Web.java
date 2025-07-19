package my.LJExport.runtime.http;

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.NoHttpResponseException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.Util;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.max;

// GZIP: http://stackoverflow.com/questions/1063004/how-to-decompress-http-response

public class Web
{
    private static CloseableHttpClient httpClient;
    private static CloseableHttpClient httpImageClient;
    private static CloseableHttpClient httpRedirectClient;
    private static CookieStore cookieStore;
    private static ThreadLocal<String> lastURL;
    private static PoolingHttpClientConnectionManager connManager;

    public static final int BINARY = (1 << 0);
    public static final int PROGRESS = (1 << 1);

    public static class Response
    {
        public int code;
        public String reason;
        public String body = new String("");
        public byte[] binaryBody;
        /* final URL after redirects */
        public String finalUrl;
        public String redirectLocation;
        public String contentType;
        public Charset charset;
        public String actualCharset;
        public Header[] headers;

        private void setFinalUrl(HttpUriRequest request, HttpClientContext context, String url) throws Exception
        {
            if (Util.False)
            {
                HttpRequest finalRequest = context.getRequest();
                if (finalRequest instanceof HttpUriRequest)
                {
                    URI finalUrl = ((HttpUriRequest) finalRequest).getURI();
                    this.finalUrl = finalUrl.toString();
                    /* URI only without host */
                }
                else
                {
                    Main.err("Cannot determine final URI from request type: " + finalRequest.getClass());
                    this.finalUrl = url;
                }
            }
            else
            {
                HttpHost target = context.getTargetHost();
                List<URI> redirects = context.getRedirectLocations();

                URI finalUrl;
                if (redirects != null && !redirects.isEmpty())
                {
                    finalUrl = URIUtils.resolve(request.getURI(), target, redirects);
                }
                else
                {
                    finalUrl = request.getURI();
                }
                this.finalUrl = finalUrl.toString();
            }
        }

        public String getHeader(String name)
        {
            if (headers != null)
            {
                for (Header h : headers)
                {
                    if (h.getName().equalsIgnoreCase(name))
                        return h.getValue();
                }
            }

            return null;
        }

        public Charset extractCharset(boolean defaultToUTF8) throws Exception
        {
            String cs = null;

            if (contentType != null)
                cs = Web.extractCharsetFromContentType(contentType);

            if (cs == null)
                cs = getHeader("x-archive-guessed-charset");

            if (cs != null)
            {
                this.actualCharset = cs;

                if (cs.equalsIgnoreCase("binary"))
                    return null;

                if (cs.equalsIgnoreCase("utf-8-sig"))
                    return null;

                if (!Charset.isSupported(cs))
                {
                    if (finalUrl != null && !finalUrl.isEmpty())
                        throw new Exception("Unsupported encoding " + cs + " in " + finalUrl);
                    else
                        throw new Exception("Unsupported encoding " + cs);
                }

                return Charset.forName(cs);
            }

            if (defaultToUTF8)
                return StandardCharsets.UTF_8;
            else
                return null;
        }

    }

    public static void init() throws Exception
    {
        if (Config.TrustAnySSLCertificate)
            TrustAnySSL.trustAnySSL();

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

            HttpHost proxy = new HttpHost(host, port, "http");
            routePlanner = new DefaultProxyRoutePlanner(proxy);
        }

        if (Config.TrustAnySSLCertificate)
        {
            connManager = new PoolingHttpClientConnectionManager(TrustAnySSL.trustAnySSLSocketFactoryRegistry());
        }
        else
        {
            connManager = new PoolingHttpClientConnectionManager();
        }

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

        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("avatars.dzeninfra.ru", 80, "http")),
                max(15, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("avatars.dzeninfra.ru", 443, "https")),
                max(15, Config.MaxConnectionsPerRoute));

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

        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD) // or .setCookieSpec(CookieSpecs.NETSCAPE)
                .setConnectTimeout(Config.WebConnectTimeout) // Time to establish TCP connection
                .setSocketTimeout(Config.WebPageReadingSocketTimeout) // Time waiting for data read on the socket after connection established
                .setConnectionRequestTimeout(0) // Time to get connection from pool (infinite)
                /* 
                 * Some LJ URLs such as 
                 * https://<user>.livejournal.com/profile/?socconns=friends&mode_full_socconns=1&comms=cfriends&admins=subscribersof 
                 * do redirects that appear circular, but for a limited number
                 */
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(20)
                .build();

        RequestConfig requestConfigRedirect = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD) // or .setCookieSpec(CookieSpecs.NETSCAPE)
                .setConnectTimeout(Config.WebConnectTimeout) // Time to establish TCP connection
                .setSocketTimeout(Config.WebImageReadingSocketTimeout) // Time waiting for data read on the socket after connection established
                .setConnectionRequestTimeout(0) // Time to get connection from pool (infinite)
                .setRedirectsEnabled(false) // Prevent automatic redirect following
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(20)
                .build();

        RequestConfig requestConfigImage = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD) // or .setCookieSpec(CookieSpecs.NETSCAPE)
                .setConnectTimeout(Config.WebConnectTimeout) // Time to establish TCP connection
                .setSocketTimeout(Config.WebImageReadingSocketTimeout) // Time waiting for data read on the socket after connection established
                .setConnectionRequestTimeout(0) // Time to get connection from pool (infinite)
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(20)
                .build();

        HttpClientBuilder hcb = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)) // Retry 3 times on IOException
                .setDefaultCookieStore(cookieStore);

        HttpClientBuilder hcbRedirect = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfigRedirect)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)) // Retry 3 times on IOException
                .setDefaultCookieStore(cookieStore);

        HttpClientBuilder hcbImage = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfigImage)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)) // Retry 3 times on IOException
                .setDefaultCookieStore(cookieStore);

        if (routePlanner != null)
        {
            hcb = hcb.setRoutePlanner(routePlanner);
            hcbRedirect = hcbRedirect.setRoutePlanner(routePlanner);
            hcbImage = hcbImage.setRoutePlanner(routePlanner);
        }

        if (Config.TrustAnySSLCertificate)
        {
            SSLConnectionSocketFactory sslSocketFactory = TrustAnySSL.trustAnySSLConnectionSocketFactory();
            hcb = hcb.setSSLSocketFactory(sslSocketFactory);
            hcbRedirect = hcbRedirect.setSSLSocketFactory(sslSocketFactory);
            hcbImage = hcbImage.setSSLSocketFactory(sslSocketFactory);
        }

        httpClient = hcb.build();
        httpRedirectClient = hcbRedirect.build();
        httpImageClient = hcbImage.build();
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

        if (httpImageClient != null)
        {
            httpImageClient.close();
            httpImageClient = null;
            // let Apache HttpClient to settle
            Thread.sleep(1500);
        }

        if (httpRedirectClient != null)
        {
            httpRedirectClient.close();
            httpRedirectClient = null;
            // let Apache HttpClient to settle
            Thread.sleep(1500);
        }

        if (connManager != null)
        {
            connManager.shutdown();
            connManager = null;
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

    public static Response get(String url, Map<String, String> headers) throws Exception
    {
        return get(url, 0, headers, null);
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

        if (ex instanceof RetryableException)
            return true;

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
        final boolean binary = 0 != (flags & BINARY);
        final boolean progress = 0 != (flags & PROGRESS);

        boolean isRequest_LJPage = false;

        CloseableHttpClient client = null;

        if (isLivejournalPicture(url))
        {
            RateLimiter.LJ_IMAGES.limitRate();
            client = httpImageClient;
        }
        else if (shouldLimitRate(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
            client = httpClient;
            isRequest_LJPage = true;
        }
        else
        {
            client = httpImageClient;
        }

        lastURL.set(url);
        Response r = new Response();

        HttpGet request = new HttpGet(url);
        setCommon(request, headers);
        if (headers != null)
        {
            for (String key : headers.keySet())
                request.setHeader(key, headers.get(key));
        }

        ActivityCounters.startedWebRequest();
        if (isRequest_LJPage)
            ActivityCounters.startedLJPageWebRequest();

        HttpClientContext context = HttpClientContext.create();
        CloseableHttpResponse response = client.execute(request, context);

        try
        {
            r.code = response.getStatusLine().getStatusCode();
            r.reason = response.getStatusLine().getReasonPhrase();
            r.setFinalUrl(request, context, url);
            if (response.containsHeader("Location"))
                r.redirectLocation = response.getFirstHeader("Location").getValue();
            if (response.containsHeader("Content-Type"))
                r.contentType = response.getFirstHeader("Content-Type").getValue();
            r.headers = response.getAllHeaders();
            r.charset = r.extractCharset(false);

            if (shouldLoadBody == null || shouldLoadBody.test(r.code))
            {
                HttpEntity entity = response.getEntity();

                if (entity != null)
                {
                    InputStream entityStream = null;
                    BufferedReader brd = null;

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
                            r.binaryBody = toByteArray(entityStream);
                            r.body = textBodyFromBinaryBody(r);
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

    private static String textBodyFromBinaryBody(Response r) throws Exception
    {
        Charset charset = r.extractCharset(false);

        if (charset != null)
            return new String(r.binaryBody, charset);

        if (r.actualCharset.equalsIgnoreCase("utf-8-sig"))
            return decodeUtf8Sig(r.binaryBody);

        return new String(r.binaryBody, StandardCharsets.UTF_8);
    }

    /**
     * Decodes a byte array that may begin with the UTF-8 BOM (EF BB BF).
     *
     * @param bytes the data, possibly starting with a BOM
     * @return the decoded {@link String}
     */
    private static String decodeUtf8Sig(byte[] bytes)
    {
        int offset = 0;
        int length = bytes.length;

        // Check for EF BB BF at the start
        if (length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF)
        {
            offset = 3; // skip the BOM
            length -= 3;
        }

        return new String(bytes, offset, length, StandardCharsets.UTF_8);
    }

    public static Response post(String url, String body) throws Exception
    {
        if (shouldLimitRate(url))
            RateLimiter.LJ_PAGES.limitRate();

        lastURL.set(url);
        Response r = new Response();

        HttpPost request = new HttpPost(url);
        setCommon(request, null);
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        ActivityCounters.startedWebRequest();
        HttpClientContext context = HttpClientContext.create();
        CloseableHttpResponse response = httpClient.execute(request, context);

        try
        {
            r.code = response.getStatusLine().getStatusCode();
            r.reason = response.getStatusLine().getReasonPhrase();
            r.setFinalUrl(request, context, url);
            if (response.containsHeader("Location"))
                r.redirectLocation = response.getFirstHeader("Location").getValue();
            if (response.containsHeader("Content-Type"))
                r.contentType = response.getFirstHeader("Content-Type").getValue();
            r.headers = response.getAllHeaders();
            r.charset = r.extractCharset(false);

            HttpEntity entity = response.getEntity();

            if (entity != null)
            {
                InputStream entityStream = null;
                BufferedReader brd = null;

                try
                {
                    entityStream = entity.getContent();
                    r.binaryBody = toByteArray(entityStream);
                    r.body = textBodyFromBinaryBody(r);
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

    private static void setCommon(HttpRequestBase request, Map<String, String> headers) throws Exception
    {
        setHeader(request, headers, "User-Agent", Config.UserAgent);
        setHeader(request, headers, "Accept", Config.UserAgentAccept);
        setHeader(request, headers, "Accept-Encoding", Config.UserAgentAcceptEncoding);
        setHeader(request, headers, "Cache-Control", "no-cache");
        setHeader(request, headers, "Pragma", "no-cache");
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

    public static String getRedirectLocation(String url, Map<String, String> headers) throws Exception
    {
        final int maxpasses = 3;

        for (int pass = 1;; pass++)
        {
            try
            {
                return retry_getRedirectLocation(url, headers, pass > maxpasses);
            }
            catch (RedirectLocationException ex)
            {
                throw ex;
            }
            catch (Exception ex)
            {
                if (pass <= maxpasses && isRetriable(ex))
                {
                    retryDelay(pass);
                    continue;
                }
                else if (ex instanceof IllegalArgumentException && ex.getCause() instanceof URISyntaxException)
                {
                    return null;
                }
                else
                {
                    throw ex;
                }
            }
        }
    }

    private static String retry_getRedirectLocation(String url, Map<String, String> headers, boolean lastPass) throws Exception
    {
        if (isLivejournalPicture(url))
        {
            RateLimiter.LJ_IMAGES.limitRate();
        }
        else if (shouldLimitRate(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
        }

        lastURL.set(url);

        HttpGet request = new HttpGet(url);

        setCommon(request, headers);
        if (headers != null)
        {
            for (String key : headers.keySet())
                request.setHeader(key, headers.get(key));
        }

        ActivityCounters.startedWebRequest();

        String threadName = Thread.currentThread().getName();

        try
        {
            Thread.currentThread().setName(threadName + " mapping " + url);
            CloseableHttpResponse response = httpRedirectClient.execute(request);

            try
            {
                int statusCode = response.getStatusLine().getStatusCode();

                switch (statusCode)
                {
                case 301:
                case 302:
                case 307:
                case 308:
                    if (response.containsHeader("Location"))
                        return response.getFirstHeader("Location").getValue();
                    // sometimes imgproxy responds with no Location header and XML reply body
                    // throw new RedirectLocationException("Redirect response received, but no Location header present");
                    return null;

                case 200:
                case 426:
                    /* will transfer directly */
                    return null;

                case 404:
                case 410:
                    /* imgprx no longer has mapping */
                    return null;

                case 403:
                    /* ??? */
                    return null;

                case 303:
                case 400:
                case 402:
                case 405:
                case 406:
                case 409:
                case 415:
                case 422:
                case 423:
                case 429:
                case 451:
                case 507:
                case 508:
                case 530:
                    /* cannot map */
                    return null;

                case 500:
                case 502:
                case 503:
                case 504:
                case 520:
                case 521:
                case 523:
                    if (lastPass)
                        return null;
                    throw new RetryableException("Not a redirect (status " + statusCode + ")");

                case 412:
                    if (url.startsWith("https://imgprx.livejournal.net/") || url.startsWith("http://imgprx.livejournal.net/"))
                        return null;
                    else
                        throw new RedirectLocationException("Not a redirect (status " + statusCode + ") for " + url);

                default:
                    throw new RedirectLocationException("Not a redirect (status " + statusCode + ") for " + url);
                }
            }
            finally
            {
                response.close();
            }
        }
        catch (SocketTimeoutException ex)
        {
            if (lastPass)
                return null;
            else
                throw new RetryableException("Socket timeout");
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }
    }

    public static class RetryableException extends Exception
    {
        private static final long serialVersionUID = 1L;

        public RetryableException(String s)
        {
            super(s);
        }
    }

    public static class RedirectLocationException extends Exception
    {
        private static final long serialVersionUID = 1L;

        public RedirectLocationException(String s)
        {
            super(s);
        }
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

    public static boolean isLivejournalImgPrx(String url) throws Exception
    {
        String lc = url.toLowerCase();

        if (lc.startsWith("http://imgprx.livejournal.net/") || lc.startsWith("https://imgprx.livejournal.net/"))
            return true;

        return false;
    }

    private static boolean shouldLimitRate(String url) throws Exception
    {
        String host = (new URL(url)).getHost();
        host = host.toLowerCase();

        if (host.equals(Config.Site) || host.endsWith("." + Config.Site))
            return true;

        if (host.equals("livejournal.net") || host.endsWith(".livejournal.net"))
            return true;
        if (host.equals("livejournal.com") || host.endsWith(".livejournal.com"))
            return true;
        if (host.equals("olegmakarenko.ru") || host.endsWith(".olegmakarenko.ru"))
            return true;
        if (host.equals("lj.rossia.org"))
            return true;
        if (host.equals("dreamwidth.org") || host.endsWith(".dreamwidth.org"))
            return true;
        if (host.equals("krylov.cc") || host.endsWith(".krylov.cc"))
            return true;

        return false;
    }

    private static byte[] toByteArray(InputStream input) throws IOException
    {
        if (input == null)
            throw new IllegalArgumentException("Input stream must not be null");

        if (Util.False)
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

    /**
     * Extracts character encoding from content attribute of meta tag.
     *
     * @param content
     *            the content attribute, e.g. "text/html; charset=windows-1251"
     * @return the charset (e.g. "windows-1251", "utf-8"), or null if not found
     */
    public static String extractCharsetFromContentType(String content)
    {
        if (content == null)
            return null;

        // Match charset=..., utf-8=..., or similar
        Pattern pattern = Pattern.compile("(?i)(?:charset|utf-8)\\s*=\\s*([\\w\\-]+)");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find())
        {
            String charset = matcher.group(1).toLowerCase();
            if (charset != null && charset.equalsIgnoreCase("win-1251"))
                charset = "windows-1251";
            if (charset != null && charset.equalsIgnoreCase("cp-1251"))
                charset = "windows-1251";
            return charset;
        }

        return null;
    }
}