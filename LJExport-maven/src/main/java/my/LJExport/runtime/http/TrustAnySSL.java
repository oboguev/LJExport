package my.LJExport.runtime.http;

import javax.net.ssl.*;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import org.conscrypt.Conscrypt;
import java.security.Security;

/*
 * Disable SSL certificate checking
 */
public class TrustAnySSL
{
    private static SSLContext sslContext;
    private static boolean initialized = false;
    
    private static boolean useConscrypt = true;

    /**
     * Globally disable SSL certificate and hostname verification.
     */
    public static synchronized void trustAnySSL()
    {
        try
        {
            if (!initialized)
            {
                // Create a trust manager that does not validate certificate chains
                final TrustManager[] trustAllCerts = new TrustManager[] { new LooseTrustManager() };

                // Install the all-trusting trust manager
                if (useConscrypt)
                {
                    // add Conscrypt as highest priority security provider
                    Security.insertProviderAt(Conscrypt.newProvider(), 1);
                    sslContext = SSLContext.getInstance("TLS", "Conscrypt");
                }
                else
                {
                    sslContext = SSLContext.getInstance("TLS");
                }
                
                sslContext.init(null, trustAllCerts, new SecureRandom());

                // Set as default for new connections
                SSLContext.setDefault(sslContext);

                // Disable hostname verification globally
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

                initialized = true;
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to disable SSL certificate checking globally", e);
        }
    }

    public static SSLConnectionSocketFactory trustAnySSLConnectionSocketFactory()
    {
        return new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
    }

    public static Registry<ConnectionSocketFactory> trustAnySSLSocketFactoryRegistry()
    {
        SSLConnectionSocketFactory sslSocketFactory = trustAnySSLConnectionSocketFactory();
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                .register("https", sslSocketFactory)
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build();
        return socketFactoryRegistry;
    }

    public static Registry<ConnectionSocketFactory> trustAnySSLViaSocks(String socksHost, int socksPort) throws Exception
    {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] { new X509TrustManager()
        {
            public void checkClientTrusted(X509Certificate[] chain, String authType)
            {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
            {
            }

            public X509Certificate[] getAcceptedIssuers()
            {
                return new X509Certificate[0];
            }
        } }, new SecureRandom());

        SSLConnectionSocketFactory sslFactory = new SocksSSLConnectionSocketFactory(sslContext, socksHost, socksPort);
        SocksConnectionSocketFactory plainFactory = new SocksConnectionSocketFactory(socksHost, socksPort);

        return RegistryBuilder.<ConnectionSocketFactory> create()
                .register("https", sslFactory)
                .register("http", plainFactory)
                .build();
    }

    public static class LooseTrustManager implements X509TrustManager
    {
        @Override
        public X509Certificate[] getAcceptedIssuers()
        {
            // System.err.println("getAcceptedIssuers =============");
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType)
        {
            // System.err.println("checkClientTrusted =============");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType)
        {
            // System.err.println("checkServerTrusted =============");
        }
    }
}
