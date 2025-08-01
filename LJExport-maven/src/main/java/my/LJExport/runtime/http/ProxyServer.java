package my.LJExport.runtime.http;

// https://github.com/adamfisk/LittleProxy

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
// import java.net.UnknownHostException;
import java.util.Queue;

import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ThreadPoolConfiguration;
import org.slf4j.LoggerFactory;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.lj.LJUtil;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
// import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpResponseStatus;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

public class ProxyServer
{
    private HttpProxyServer server = null;
    private int port = 0;
    private boolean filtering = false;

    private int clientToProxyWorkerThreads = 0;
    private int proxyToServerWorkerThreads = 0;

    public static ProxyServer create() throws Exception
    {
        int nt = Config.NWorkThreads * Config.ProxyThreadsPerThread;
        nt = Math.min(nt, Config.MaxProxyThreads / 2);
        ProxyServer proxy_server = new ProxyServer(nt, nt);
        proxy_server.setFiltering(true);
        proxy_server.start();
        return proxy_server;
    }

    public static class AbortingFilterAdapter extends HttpFiltersAdapter
    {
        @SuppressWarnings("unused")
        private final ProxyServer proxy;

        public AbortingFilterAdapter(HttpRequest originalRequest, ChannelHandlerContext ctx, ProxyServer proxy)
        {
            super(originalRequest, ctx);
            this.proxy = proxy;
        }

        @Override
        public HttpResponse clientToProxyRequest(HttpObject httpObject)
        {
            HttpResponse response;
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
            response.headers().set("Content-Length", "0");
            // if we do not cause the connection to be dropped (Connection: Close),
            // Firefox for some reason stays stuck on rejected URLs 
            response.headers().set("Connection", "Close");
            return response;
        }
    }

    public static class TimingFilterAdapter extends HttpFiltersAdapter
    {
        @SuppressWarnings("unused")
        private final ProxyServer proxy;
        private final String url;
        private long startTime;

        public TimingFilterAdapter(HttpRequest originalRequest, ChannelHandlerContext ctx, ProxyServer proxy, String url)
        {
            super(originalRequest, ctx);
            this.proxy = proxy;
            this.url = url;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public HttpResponse clientToProxyRequest(HttpObject httpObject)
        {
            return null; // continue processing
        }

        @Override
        public HttpObject serverToProxyResponse(HttpObject httpObject)
        {
            long durationMs = System.currentTimeMillis() - startTime;
            UrlDurationHistory.onComplete(url, durationMs);
            return httpObject;
        }
    }

    public static class Filter extends HttpFiltersSourceAdapter
    {
        ProxyServer proxy;

        public Filter(ProxyServer proxy)
        {
            this.proxy = proxy;
        }

        @Override
        public HttpFilters filterRequest(HttpRequest originalRequest,
                ChannelHandlerContext ctx)
        {
            // see https://chatgpt.com/c/68366e00-e880-8007-bf9f-c97bdd396c55

            String url = originalRequest.getUri();
            if (proxy.isAccepted(url))
            {
                // System.out.println("PROXY: ACCEPT: " + url);
                // return super.filterRequest(originalRequest, ctx);
                return new TimingFilterAdapter(originalRequest, ctx, proxy, url);
            }
            else
            {
                // System.out.println("PROXY: REJECT: " + url);
                return new AbortingFilterAdapter(originalRequest, ctx, proxy);
            }
        }
    }

    private boolean isAccepted(String url)
    {
        if (!filtering)
            return true;

        boolean accepted = false;
        try
        {
            accepted = LJUtil.isServerUrl(url);
        }
        catch (Exception ex)
        {
            Main.err(ex);
        }
        return accepted;
    }

    public ProxyServer() throws Exception
    {
    }

    public ProxyServer(int clientToProxyWorkerThreads, int proxyToServerWorkerThreads) throws Exception
    {
        this.clientToProxyWorkerThreads = clientToProxyWorkerThreads;
        this.proxyToServerWorkerThreads = proxyToServerWorkerThreads;
    }

    public int getPort() throws Exception
    {
        if (port == 0)
        {
            ServerSocket s = new ServerSocket(0);
            port = s.getLocalPort();
            s.close();
        }

        return port;
    }

    public void setFiltering(boolean filtering)
    {
        this.filtering = filtering;
    }

    public void start() throws Exception
    {
        ThreadPoolConfiguration threadPoolConfiguration = new ThreadPoolConfiguration();
        // threadPoolConfiguration = threadPoolConfiguration.withAcceptorThreads(2);
        if (clientToProxyWorkerThreads != 0)
            threadPoolConfiguration = threadPoolConfiguration.withClientToProxyWorkerThreads(clientToProxyWorkerThreads);
        if (proxyToServerWorkerThreads != 0)
            threadPoolConfiguration = threadPoolConfiguration.withProxyToServerWorkerThreads(proxyToServerWorkerThreads);

        HttpProxyServerBootstrap boot = DefaultHttpProxyServer.bootstrap()
                .withPort(getPort())
                .withAllowLocalOnly(true)
                .withConnectTimeout(90 * 1000)
                .withFiltersSource(new Filter(this))
                .withThreadPoolConfiguration(threadPoolConfiguration);

        if (Config.Proxy != null)
        {
            /* 
             * configure downstream proxy that ProxyServer forwards to,
             * e.g. if we are behind the firewall 
             */
            // https://github.com/adamfisk/LittleProxy/blob/master/src/test/java/org/littleshoot/proxy/BaseChainedProxyTest.java
            // https://github.com/adamfisk/LittleProxy/blob/master/src/test/java/org/littleshoot/proxy/EncryptedTCPChainedProxyTest.java
            try
            {
                URL url = new URL(Config.Proxy);
                String host = url.getHost();
                int port = url.getPort();
                InetSocketAddress sockaddr = new InetSocketAddress(InetAddress.getByName(host), port);
                boot = boot.withChainProxyManager(new InnerChainedProxyManager(sockaddr));
            }
            catch (Exception ex)
            {
                throw new Exception("Unable to resolve proxy server", ex);
            }
        }

        setLogging();

        server = boot.start();

        setLogging();
    }

    public void stop() throws Exception
    {
        if (server != null)
        {
            server.stop();
            server = null;
        }
    }

    public void setLogging()
    {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Set global default level
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.WARN);

        // Explicitly adjust noisy categories
        ctx.getLogger("org.littleshoot.proxy.impl.ProxyConnection").setLevel(Level.WARN);
        ctx.getLogger("org.littleshoot.proxy.impl.ClientToProxyConnection").setLevel(Level.WARN);
        ctx.getLogger("impl.ClientToProxyConnection").setLevel(Level.WARN);
    }

    ByteBuf newEmptyByteBuf()
    {
        return ByteBufAllocator.DEFAULT.buffer();
    }

    protected class InnerChainedProxyManager implements ChainedProxyManager
    {
        private InetSocketAddress sockaddr;

        public InnerChainedProxyManager(InetSocketAddress sockaddr)
        {
            this.sockaddr = sockaddr;
        }

        @Override
        public void lookupChainedProxies(HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies)
        {
            chainedProxies.add(new InnerChainedProxy(sockaddr));
        }
    }

    protected class InnerChainedProxy extends ChainedProxyAdapter
    {
        private InetSocketAddress sockaddr;

        public InnerChainedProxy(InetSocketAddress sockaddr)
        {
            this.sockaddr = sockaddr;
        }

        @Override
        public InetSocketAddress getChainedProxyAddress()
        {
            return sockaddr;
        }
    }
}
