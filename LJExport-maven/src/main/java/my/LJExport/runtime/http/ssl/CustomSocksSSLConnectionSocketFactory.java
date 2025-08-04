package my.LJExport.runtime.http.ssl;

import java.io.IOException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import my.LJExport.runtime.http.socks.SocksSSLConnectionSocketFactory;

/*
 * Interface used by Apache HTTP client through Socks proxy
 */
public class CustomSocksSSLConnectionSocketFactory extends SocksSSLConnectionSocketFactory
{
    public CustomSocksSSLConnectionSocketFactory(SSLContext sslContext, String socksHost, int socksPort)
    {
        super(sslContext, socksHost, socksPort);
    }

    @Override
    protected void prepareSocket(final SSLSocket socket) throws IOException
    {
        super.prepareSocket(socket);
        CustomizeSSL.prepareSocket(socket);
    }
}
