package my.LJExport.runtime.http;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.NoHttpResponseException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.browserproxy.BrowserProxy;
import my.LJExport.runtime.http.browserproxy.BrowserProxyFactory;
import my.LJExport.runtime.http.webaction.WebActions;
import my.LJExport.runtime.lj.Sites;
import my.LJExport.runtime.url.UrlUtil;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.max;
import static java.lang.Math.min;

// GZIP: http://stackoverflow.com/questions/1063004/how-to-decompress-http-response

public class Web
{
    /* client for LJ page requests */
    private static CloseableHttpClient httpClientLJPages;

    /* client for LJ image requests */
    private static CloseableHttpClient httpClientLJImages;

    /* client for LJ non-LJ requests */
    private static CloseableHttpClient httpClientOther;

    /* client for LJ redirect requests */
    private static CloseableHttpClient httpClientRedirectLJ;

    /* client for non-LJ redirect requests */
    private static CloseableHttpClient httpClientRedirectOther;

    private static CookieStore cookieStore;
    private static PoolingHttpClientConnectionManager connManagerLJ;
    private static PoolingHttpClientConnectionManager connManagerOther;
    private static ThreadLocal<String> lastURL;
    private static CooloffMode cooloffArchiveOrg = new CooloffMode(Config.WebArchiveOrg_Http429_Cooloff_Interval);
    private static Semaphore semaphoreArchiveOrg;

    public static final int BINARY = (1 << 0);
    public static final int PROGRESS = (1 << 1);

    public static String[][] InitialActions;
    
    private static boolean initializing = false; 
    private static boolean initialized = false; 

    public static class Response
    {
        public int code;
        public String reason;
        private String body = null;
        public byte[] binaryBody;
        /* final URL after redirects */
        public String finalUrl;
        public String redirectLocation;
        public String contentType;
        public Charset charset;
        public String actualCharset;
        public Header[] headers;

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

                switch (cs.toLowerCase())
                {
                case "windows-1215":
                case "win-1251":
                    cs = "windows-1251";
                    break;

                case "binary":
                case "utf-8-sig":
                case "empty":
                    return null;
                }

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

        public String textBody() throws Exception
        {
            if (body == null)
                body = textBodyFromBinaryBody(this);

            if (body == null)
                return "";

            return body;
        }
    }

    public static synchronized void init() throws Exception
    {
        if (initialized || initializing)
            throw new Exception("Web is already initialized or partially initialized ");
        
        initializing = true;

        if (Config.TrustAnySSLCertificate)
            TrustAnySSL.trustAnySSL();

        cookieStore = new BasicCookieStore();
        WebActions.clearHistory();
        WebActions.execute(InitialActions);

        DefaultProxyRoutePlanner routePlanner = null;
        DefaultProxyRoutePlanner routePlannerLivejournal = null;

        lastURL = new ThreadLocal<String>();
        semaphoreArchiveOrg = new Semaphore(Config.WebArchiveOrg_ConcurrenRequests);

        RateLimiter.LJ_PAGES.aborting(false);
        RateLimiter.LJ_IMAGES.aborting(false);
        RateLimiter.WEB_ARCHIVE_ORG.aborting(false);

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

        // e.g. http://localhost:8888
        String ljproxy = System.getenv("LJEXPORT_LJPROXY");
        if (ljproxy != null)
            ljproxy = ljproxy.trim();
        if (ljproxy != null && ljproxy.length() != 0)
        {
            URL url = new URL(ljproxy);
            HttpHost proxy = new HttpHost(url.getHost(), url.getPort(), "http");
            routePlannerLivejournal = new DefaultProxyRoutePlanner(proxy);
        }

        if (routePlannerLivejournal == null && routePlanner != null)
            routePlannerLivejournal = routePlanner;

        /* ====================================================================================== */

        connManagerLJ = buildConnManager(true);
        connManagerOther = buildConnManager(false);

        /* ====================================================================================== */

        RequestConfig requestConfigLJPages = RequestConfig.custom()
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

        RequestConfig requestConfigLJImages = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD) // or .setCookieSpec(CookieSpecs.NETSCAPE)
                .setConnectTimeout(Config.WebConnectTimeout) // Time to establish TCP connection
                .setSocketTimeout(Config.WebImageReadingSocketTimeout) // Time waiting for data read on the socket after connection established
                .setConnectionRequestTimeout(0) // Time to get connection from pool (infinite)
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(20)
                .build();

        RequestConfig requestConfigOther = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD) // or .setCookieSpec(CookieSpecs.NETSCAPE)
                .setConnectTimeout(Config.WebConnectTimeout) // Time to establish TCP connection
                .setSocketTimeout(Config.WebImageReadingSocketTimeout) // Time waiting for data read on the socket after connection established
                .setConnectionRequestTimeout(0) // Time to get connection from pool (infinite)
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(20)
                .build();

        RequestConfig requestConfigRedirect = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD) // or .setCookieSpec(CookieSpecs.NETSCAPE)
                .setConnectTimeout(Config.WebConnectTimeout) // Time to establish TCP connection
                .setSocketTimeout(Config.WebImageReadingSocketTimeout) // Time waiting for data read on the socket after connection established
                .setConnectionRequestTimeout(0) // Time to get connection from pool (infinite)
                .setRedirectsEnabled(false) // Prevent automatic redirect following
                // .setCircularRedirectsAllowed(true)
                // .setMaxRedirects(20)
                .build();

        /* ====================================================================================== */

        HttpClientBuilder hcbClientLJPages = HttpClients.custom()
                .setConnectionManager(connManagerLJ)
                .setDefaultRequestConfig(requestConfigLJPages)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)) // Retry 3 times on IOException
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultCookieStore(cookieStore);

        HttpClientBuilder hcbClientLJImages = HttpClients.custom()
                .setConnectionManager(connManagerLJ)
                .setDefaultRequestConfig(requestConfigLJImages)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)) // Retry 3 times on IOException
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultCookieStore(cookieStore);

        HttpClientBuilder hcbClientOther = HttpClients.custom()
                .setConnectionManager(connManagerOther)
                .setDefaultRequestConfig(requestConfigOther)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)) // Retry 3 times on IOException
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultCookieStore(cookieStore);

        HttpClientBuilder hcbClientRedirectLJ = HttpClients.custom()
                .setConnectionManager(connManagerLJ)
                .setDefaultRequestConfig(requestConfigRedirect)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)) // Retry 3 times on IOException
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultCookieStore(cookieStore);

        HttpClientBuilder hcbClientRedirectOther = HttpClients.custom()
                .setConnectionManager(connManagerOther)
                .setDefaultRequestConfig(requestConfigRedirect)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true)) // Retry 3 times on IOException
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultCookieStore(cookieStore);

        /* ====================================================================================== */

        if (routePlanner != null)
        {
            hcbClientOther = hcbClientOther.setRoutePlanner(routePlanner);
            hcbClientRedirectOther = hcbClientRedirectOther.setRoutePlanner(routePlanner);
        }

        if (routePlannerLivejournal != null)
        {
            hcbClientLJPages = hcbClientLJPages.setRoutePlanner(routePlannerLivejournal);
            hcbClientLJImages = hcbClientLJImages.setRoutePlanner(routePlannerLivejournal);
            hcbClientRedirectLJ = hcbClientRedirectLJ.setRoutePlanner(routePlannerLivejournal);
        }

        if (Config.TrustAnySSLCertificate)
        {
            SSLConnectionSocketFactory sslSocketFactory = TrustAnySSL.trustAnySSLConnectionSocketFactory();
            hcbClientLJPages = hcbClientLJPages.setSSLSocketFactory(sslSocketFactory);
            hcbClientLJImages = hcbClientLJImages.setSSLSocketFactory(sslSocketFactory);
            hcbClientOther = hcbClientOther.setSSLSocketFactory(sslSocketFactory);
            hcbClientRedirectLJ = hcbClientRedirectLJ.setSSLSocketFactory(sslSocketFactory);
            hcbClientRedirectOther = hcbClientRedirectOther.setSSLSocketFactory(sslSocketFactory);
        }

        httpClientLJPages = hcbClientLJPages.build();
        httpClientLJImages = hcbClientLJImages.build();
        httpClientOther = hcbClientOther.build();
        httpClientRedirectLJ = hcbClientRedirectLJ.build();
        httpClientRedirectOther = hcbClientRedirectOther.build();
        
        initializing = false;
        initialized = true;
    }

    public static void aborting()
    {
        if (cooloffArchiveOrg != null)
            cooloffArchiveOrg.cancelCoolingOff();

        if (semaphoreArchiveOrg != null)
            semaphoreArchiveOrg.release(10000);

        RateLimiter.LJ_PAGES.aborting(true);
        RateLimiter.LJ_IMAGES.aborting(true);
        RateLimiter.WEB_ARCHIVE_ORG.aborting(true);
    }

    public static synchronized void shutdown() throws Exception
    {
        if (semaphoreArchiveOrg != null)
        {
            semaphoreArchiveOrg.release(10000);
            semaphoreArchiveOrg = null;
        }

        if (cooloffArchiveOrg != null)
            cooloffArchiveOrg.cancelCoolingOff();

        cookieStore = null;

        if (lastURL != null)
        {
            lastURL.remove();
            lastURL = null;
        }

        boolean settle = false;

        if (httpClientLJPages != null)
        {
            httpClientLJPages.close();
            httpClientLJPages = null;
            settle = true;
        }

        if (httpClientLJImages != null)
        {
            httpClientLJImages.close();
            httpClientLJImages = null;
            settle = true;
        }

        if (httpClientOther != null)
        {
            httpClientOther.close();
            httpClientOther = null;
            settle = true;
        }

        if (httpClientRedirectLJ != null)
        {
            httpClientRedirectLJ.close();
            httpClientRedirectLJ = null;
            settle = true;
        }

        if (httpClientRedirectOther != null)
        {
            httpClientRedirectOther.close();
            httpClientRedirectOther = null;
            settle = true;
        }

        if (settle)
        {
            // let Apache HttpClient to settle
            Thread.sleep(1500);
        }

        if (connManagerLJ != null)
        {
            connManagerLJ.shutdown();
            connManagerLJ = null;
        }

        if (connManagerOther != null)
        {
            connManagerOther.shutdown();
            connManagerOther = null;
        }

        WebActions.clearHistory();
        
        initializing = false;
        initialized = false;
    }
    
    public static synchronized void scheduleActions(String[][] actions) throws Exception
    {
        if (initializing)
            throw new Exception("Web is partially initialized ");
        
        if (initialized)
            WebActions.execute(actions);
        else
            InitialActions = actions;            
    }

    public static void threadExit()
    {
        if (lastURL != null)
            lastURL.remove();
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
        HttpAccessMode httpAccessMode = HttpAccessMode.forUrl(url);

        if (httpAccessMode == HttpAccessMode.DIRECT_VIA_HTTP)
            url = "http://" + Util.stripProtocol(url);

        url = UrlUtil.encodeUrlForApacheWire(url);

        final boolean binary = 0 != (flags & BINARY);
        final boolean progress = 0 != (flags & PROGRESS);

        boolean isRequest_LJPage = false;
        boolean isArchiveOrg = false;

        CloseableHttpClient client = httpClientOther;

        if (isArchiveOrg(url))
        {
            RateLimiter.WEB_ARCHIVE_ORG.limitRate();
            isArchiveOrg = true;
        }
        else if (isLivejournalPicture(url))
        {
            RateLimiter.LJ_IMAGES.limitRate();
            client = httpClientLJImages;
        }
        else if (isLivejournal(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
            client = httpClientLJPages;
            isRequest_LJPage = true;
        }
        else if (shouldLimitRate(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
        }

        lastURL.set(url);
        Response r = new Response();

        if (httpAccessMode == HttpAccessMode.NO_ACCESS)
        {
            r.code = 503;
            return r;
        }

        BrowserProxy browserProxy = BrowserProxyFactory.getBrowserProxy(httpAccessMode, url);

        HttpGet request = new HttpGet(url);
        WebRequestHeaders.setRequestHeaders(url, httpAccessMode, request, headers);
        HttpClientContext context = HttpClientContext.create();
        WebHttpResponse response = null;

        ActivityCounters.startedWebRequest();
        if (isRequest_LJPage)
            ActivityCounters.startedLJPageWebRequest();

        if (Main.isAborting())
            throw new Exception("Application is aborting");

        boolean acquiredArchiveOrgSemaphore = false;

        try
        {
            if (isArchiveOrg)
            {
                semaphoreArchiveOrg.acquire();
                acquiredArchiveOrgSemaphore = true;

                if (Main.isAborting())
                    throw new Exception("Application is aborting");

                cooloffArchiveOrg.waitIfCoolingOff();

                if (Main.isAborting())
                    throw new Exception("Application is aborting");
            }

            response = new WebHttpResponse(url, request, context, client.execute(request, context));
            r.code = response.getStatusCode();

            if (isArchiveOrg && r.code == 429)
            {
                do
                {
                    response.close();
                    response = null;

                    if (Main.isAborting())
                        throw new Exception("Application is aborting");

                    cooloffArchiveOrg.signalStart();
                    cooloffArchiveOrg.waitIfCoolingOff();

                    if (Main.isAborting())
                        throw new Exception("Application is aborting");

                    response = new WebHttpResponse(url, request, context, client.execute(request, context));
                    r.code = response.getStatusCode();
                }
                while (r.code == 429);
            }

            r.reason = response.getStatusReasonPhrase();
            r.finalUrl = response.getFinalUrl();
            if (response.containsHeader("Location"))
                r.redirectLocation = response.getFirstHeader("Location").getValue();
            if (response.containsHeader("Content-Type"))
                r.contentType = response.getFirstHeader("Content-Type").getValue();
            r.headers = response.getAllHeaders();
            r.charset = r.extractCharset(false);
            r.binaryBody = response.getBinaryBody(shouldLoadBody, progress);
            if (!binary)
                r.body = textBodyFromBinaryBody(r);

            return r;
        }
        finally
        {
            if (acquiredArchiveOrgSemaphore)
            {
                semaphoreArchiveOrg.release();
                acquiredArchiveOrgSemaphore = false;
            }

            if (response != null)
                response.close();
        }
    }

    private static String textBodyFromBinaryBody(Response r) throws Exception
    {
        if (r.binaryBody == null)
            return null;

        Charset charset = r.extractCharset(false);

        if (charset != null)
            return new String(r.binaryBody, charset);

        if (r.actualCharset != null && r.actualCharset.equalsIgnoreCase("utf-8-sig"))
            return decodeUtf8Sig(r.binaryBody);

        return new String(r.binaryBody, StandardCharsets.UTF_8);
    }

    /**
     * Decodes a byte array that may begin with the UTF-8 BOM (EF BB BF).
     *
     * @param bytes
     *            the data, possibly starting with a BOM
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

    public static Response post(String url, String body, Map<String, String> headers) throws Exception
    {
        final boolean binary = false;
        final boolean progress = false;
        final IntPredicate shouldLoadBody = null;

        HttpAccessMode httpAccessMode = HttpAccessMode.forUrl(url);

        if (httpAccessMode == HttpAccessMode.DIRECT_VIA_HTTP)
            url = "http://" + Util.stripProtocol(url);

        url = UrlUtil.encodeUrlForApacheWire(url);

        CloseableHttpClient client = httpClientOther;

        if (isArchiveOrg(url))
        {
            RateLimiter.WEB_ARCHIVE_ORG.limitRate();
        }
        else if (isLivejournalPicture(url))
        {
            RateLimiter.LJ_IMAGES.limitRate();
            client = httpClientLJImages;
        }
        else if (isLivejournal(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
            client = httpClientLJPages;
        }
        else if (shouldLimitRate(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
        }

        lastURL.set(url);
        Response r = new Response();

        if (httpAccessMode == HttpAccessMode.NO_ACCESS)
        {
            r.code = 503;
            return r;
        }

        BrowserProxy browserProxy = BrowserProxyFactory.getBrowserProxy(httpAccessMode, url);

        HttpPost request = new HttpPost(url);
        WebRequestHeaders.setRequestHeaders(url, httpAccessMode, request, headers);
        request.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        ActivityCounters.startedWebRequest();
        HttpClientContext context = HttpClientContext.create();
        WebHttpResponse response = null;

        if (Main.isAborting())
            throw new Exception("Application is aborting");

        try
        {
            response = new WebHttpResponse(url, request, context, client.execute(request, context));
            r.code = response.getStatusCode();
            r.reason = response.getStatusReasonPhrase();
            r.finalUrl = response.getFinalUrl();
            if (response.containsHeader("Location"))
                r.redirectLocation = response.getFirstHeader("Location").getValue();
            if (response.containsHeader("Content-Type"))
                r.contentType = response.getFirstHeader("Content-Type").getValue();
            r.headers = response.getAllHeaders();
            r.charset = r.extractCharset(false);

            r.binaryBody = response.getBinaryBody(shouldLoadBody, progress);
            if (!binary)
                r.body = textBodyFromBinaryBody(r);

            return r;
        }
        finally
        {
            if (response != null)
                response.close();
        }
    }

    public static String getRedirectLocation(String url, String referer, Map<String, String> headers) throws Exception
    {
        final int maxpasses = 3;

        for (int pass = 1;; pass++)
        {
            try
            {
                return retry_getRedirectLocation(url, referer, headers, pass > maxpasses);
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

    private static String retry_getRedirectLocation(String url, String referer, Map<String, String> headers, boolean lastPass)
            throws Exception
    {
        HttpAccessMode httpAccessMode = HttpAccessMode.forUrl(url);

        if (httpAccessMode == HttpAccessMode.DIRECT_VIA_HTTP)
            url = "http://" + Util.stripProtocol(url);

        url = UrlUtil.encodeUrlForApacheWire(url);

        if (referer != null)
        {
            if (headers == null)
                headers = new HashMap<>();
            else
                headers = new HashMap<>(headers);

            headers.put("Referer", referer);
        }

        CloseableHttpClient httpClient = httpClientRedirectOther;

        if (isArchiveOrg(url))
        {
            RateLimiter.WEB_ARCHIVE_ORG.limitRate();
        }
        else if (isLivejournalPicture(url))
        {
            RateLimiter.LJ_IMAGES.limitRate();
            httpClient = httpClientRedirectLJ;
        }
        else if (isLivejournal(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
            httpClient = httpClientRedirectLJ;
        }
        else if (shouldLimitRate(url))
        {
            RateLimiter.LJ_PAGES.limitRate();
        }

        lastURL.set(url);

        if (httpAccessMode == HttpAccessMode.NO_ACCESS)
        {
            Exception ex = new URISyntaxException(url, "No HTTP access to " + url);
            ex = new IllegalArgumentException(ex.getLocalizedMessage(), ex);
            throw ex;
            // return null;
        }

        BrowserProxy browserProxy = BrowserProxyFactory.getBrowserProxy(httpAccessMode, url);
        if (browserProxy != null && !browserProxy.canInterceptRedirection())
            return null;

        HttpGet request = new HttpGet(url);
        WebRequestHeaders.setRequestHeaders(url, httpAccessMode, request, headers);

        ActivityCounters.startedWebRequest();

        String threadName = Thread.currentThread().getName();

        try
        {
            Thread.currentThread().setName(threadName + " mapping " + url);
            CloseableHttpResponse response = httpClient.execute(request);

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
                    reportRedirectError(statusCode, referer);
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
                    reportRedirectError(statusCode, referer);
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
                    reportRedirectError(statusCode, referer);
                    return null;

                case 500:
                case 502:
                case 503:
                case 504:
                case 520:
                case 521:
                case 523:
                    reportRedirectError(statusCode, referer);
                    if (lastPass)
                        return null;
                    throw new RetryableException("Not a redirect (status " + statusCode + ")");

                case 412:
                    reportRedirectError(statusCode, referer);
                    if (url.startsWith("https://imgprx.livejournal.net/") || url.startsWith("http://imgprx.livejournal.net/"))
                        return null;
                    else
                        throw new RedirectLocationException("Not a redirect (status " + statusCode + ") for " + url);

                default:
                    reportRedirectError(statusCode, referer);
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

    private static void reportRedirectError(int statusCode, String referer)
    {
        if (referer != null && Util.False)
        {
            Util.err(String.format("REDIR error %03d %s", statusCode, referer));
            Util.noop();
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
        return lastURL == null ? null : lastURL.get();
    }

    private static boolean isLivejournalPicture(String url) throws Exception
    {
        String host = new URL(url).getHost().toLowerCase();

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

    private static boolean isArchiveOrg(String url) throws Exception
    {
        if (url.toLowerCase().contains("archive.org"))
        {
            String host = new URL(url).getHost().toLowerCase();

            if (host.equals("archive.org") || host.endsWith(".archive.org"))
                return true;
        }

        return false;
    }

    private static boolean isLivejournal(String url) throws Exception
    {
        String host = (new URL(url)).getHost().toLowerCase();

        if (host.equals("livejournal.com") || host.endsWith(".livejournal.com"))
            return true;
        if (host.equals("livejournal.net") || host.endsWith(".livejournal.net"))
            return true;
        if (host.equals("olegmakarenko.ru") || host.endsWith(".olegmakarenko.ru"))
            return true;

        return false;
    }

    private static boolean shouldLimitRate(String url) throws Exception
    {
        String host = (new URL(url)).getHost().toLowerCase();

        if (host.equals(Config.Site) || host.endsWith("." + Config.Site))
            return true;

        if (isLivejournal(url))
            return true;
        if (host.equals(Sites.RossiaOrg))
            return true;
        if (host.equals(Sites.DreamwidthOrg) || host.endsWith("." + Sites.DreamwidthOrg))
            return true;
        if (host.equals("krylov.cc") || host.endsWith(".krylov.cc"))
            return true;

        return false;
    }

    /* ================================================================================= */

    // Custom HttpEntity to track download progress

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

    /* ============================================================================== */

    private static PoolingHttpClientConnectionManager buildConnManager(boolean useSocks) throws Exception
    {
        PoolingHttpClientConnectionManager connManager = makeConnManager(useSocks);

        // Set max total connections
        connManager.setMaxTotal(200);
        // Set max connections per route (i.e., per host)
        connManager.setDefaultMaxPerRoute(Config.MaxConnectionsPerRoute);

        // higher (or lower) limits for some routes
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

        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("web.archive.org", 443, "https")),
                min(Config.WebArchiveOrg_ConcurrenRequests, Config.MaxConnectionsPerRoute));
        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("web.archive.org", 80, "http")),
                min(Config.WebArchiveOrg_ConcurrenRequests, Config.MaxConnectionsPerRoute));

        connManager.setDefaultSocketConfig(SocketConfig.custom()
                // .setTcpNoDelay(true)
                .setSndBufSize(65536) // 64KB send buffer
                .setRcvBufSize(65536) // 64KB receive buffer
                .build());

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setBufferSize(65536) // 64KB buffer
                .build();
        connManager.setDefaultConnectionConfig(connectionConfig);

        return connManager;
    }

    private static PoolingHttpClientConnectionManager makeConnManager(boolean useSocks) throws Exception
    {
        PoolingHttpClientConnectionManager connManager = null;

        if (useSocks && Config.SocksHost != null && Config.SocksHost.length() != 0)
        {
            if (Config.TrustAnySSLCertificate)
            {
                connManager = new PoolingHttpClientConnectionManager(
                        TrustAnySSL.trustAnySSLViaSocks(Config.SocksHost, Config.SocksPort));
            }
            else
            {
                Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
                        .register("http", new SocksConnectionSocketFactory(Config.SocksHost, Config.SocksPort))
                        .register("https", new SocksConnectionSocketFactory(Config.SocksHost, Config.SocksPort))
                        .build();

                connManager = new PoolingHttpClientConnectionManager(registry);
            }
        }
        else
        {
            if (Config.TrustAnySSLCertificate)
            {
                connManager = new PoolingHttpClientConnectionManager(TrustAnySSL.trustAnySSLSocketFactoryRegistry());
            }
            else
            {
                connManager = new PoolingHttpClientConnectionManager();
            }
        }

        return connManager;
    }
}