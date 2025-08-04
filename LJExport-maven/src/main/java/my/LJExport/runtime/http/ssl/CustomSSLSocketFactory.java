package my.LJExport.runtime.http.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/*
 * Interface used by JDK
 */
public class CustomSSLSocketFactory extends SSLSocketFactory
{
    private final SSLSocketFactory delegate;

    CustomSSLSocketFactory(SSLSocketFactory delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites()
    {
        String[] defaultCipherSuites = delegate.getDefaultCipherSuites();
        String[] supportedCipherSuites = delegate.getSupportedCipherSuites();
        return CustomizeSSL.getDefaultCipherSuites(defaultCipherSuites, supportedCipherSuites);
    }

    @Override
    public String[] getSupportedCipherSuites()
    {
        String[] defaultCipherSuites = delegate.getDefaultCipherSuites();
        String[] supportedCipherSuites = delegate.getSupportedCipherSuites();
        return CustomizeSSL.getSupportedCipherSuites(defaultCipherSuites, supportedCipherSuites);
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException
    {
        Socket s2 = delegate.createSocket(s, host, port, autoClose);
        configureSocket(s2);
        return s2;
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException
    {
        Socket s = delegate.createSocket(host, port);
        configureSocket(s);
        return s;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException
    {
        Socket s = delegate.createSocket(host, port, localHost, localPort);
        configureSocket(s);
        return s;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException
    {
        Socket s = delegate.createSocket(host, port);
        configureSocket(s);
        return s;
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException
    {
        Socket s = delegate.createSocket(address, port, localAddress, localPort);
        configureSocket(s);
        return s;
    }

    private void configureSocket(Socket s)
    {
        if (s instanceof SSLSocket)
        {
            CustomizeSSL.prepareSocket((SSLSocket) s);
        }
    }
}
