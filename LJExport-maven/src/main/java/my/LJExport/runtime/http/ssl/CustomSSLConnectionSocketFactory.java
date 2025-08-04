package my.LJExport.runtime.http.ssl;

import java.io.IOException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

/*
 * Interface used by Apache HTTP client
 */
public class CustomSSLConnectionSocketFactory extends SSLConnectionSocketFactory
{
    public CustomSSLConnectionSocketFactory(final SSLContext sslContext)
    {
        super(sslContext);
    }
    
    public CustomSSLConnectionSocketFactory(final SSLContext sslContext, final HostnameVerifier hostnameVerifier) 
    {
        super(sslContext, hostnameVerifier); 
    }

    @Override
    protected void prepareSocket(final SSLSocket socket) throws IOException
    {
        super.prepareSocket(socket);
        CustomizeSSL.prepareSocket(socket);
    }
}
