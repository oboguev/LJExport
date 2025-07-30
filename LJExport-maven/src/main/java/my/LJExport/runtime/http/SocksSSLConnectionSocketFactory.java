package my.LJExport.runtime.http;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.NoopHostnameVerifier;

/**
 * SSL socket factory that routes HTTPS connections through a SOCKS5 proxy,
 * and disables hostname verification (trusts all certs).
 */
public class SocksSSLConnectionSocketFactory extends SSLConnectionSocketFactory
{

    private final String socksHost;
    private final int socksPort;

    public SocksSSLConnectionSocketFactory(SSLContext sslContext, String socksHost, int socksPort)
    {
        super(sslContext, NoopHostnameVerifier.INSTANCE);
        this.socksHost = socksHost;
        this.socksPort = socksPort;
    }

    @Override
    public Socket createSocket(HttpContext context)
    {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost, socksPort));
        return new Socket(proxy);
    }
}