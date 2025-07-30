package my.LJExport.runtime.http;

import org.apache.http.HttpHost;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.*;

public class SocksConnectionSocketFactory implements ConnectionSocketFactory
{
    private final String socksHost;
    private final int socksPort;

    public SocksConnectionSocketFactory(String socksHost, int socksPort)
    {
        this.socksHost = socksHost;
        this.socksPort = socksPort;
    }

    @Override
    public Socket createSocket(HttpContext context)
    {
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost, socksPort));
        return new Socket(proxy);
    }

    @Override
    public Socket connectSocket(
            int connectTimeout,
            Socket socket,
            HttpHost host,
            InetSocketAddress remoteAddress,
            InetSocketAddress localAddress,
            HttpContext context) throws IOException
    {

        if (socket == null)
        {
            socket = createSocket(context);
        }

        if (localAddress != null)
        {
            socket.bind(localAddress);
        }

        socket.connect(remoteAddress, connectTimeout);
        return socket;
    }
}
