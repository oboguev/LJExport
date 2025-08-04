package my.LJExport.runtime.http.ssl;

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

import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.socks.SocksConnectionSocketFactory;
import my.LJExport.runtime.http.socks.SocksSSLConnectionSocketFactory;

import java.security.Security;

/*
 * Disable SSL certificate checking
 */
public class TrustAnySSL
{
    private static SSLContext sslContext;
    private static boolean initialized = false;

    /*
     * REQUIRED to reshuffle ciphers.
     */
    private static boolean useConscrypt = true;

    /**
     * Globally disable SSL certificate and hostname verification.
     */
    public static synchronized void trustAnySSL()
    {
        trustAnySSL(false, useConscrypt);
    }

    public static synchronized void trustAnySSL(boolean forceReinitialization, boolean useConscrypt)
    {
        try
        {
            if (!initialized || forceReinitialization)
            {
                Util.out("Reinitializing SSL/TLS socket factory, provider = " + (useConscrypt ? "Conscrypt" : "default"));

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
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                sslSocketFactory = new CustomSSLSocketFactory(sslSocketFactory);
                HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory);
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

                initialized = true;
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to disable SSL certificate checking globally", e);
        }
    }

    /* ====================================================================================================== */

    public static SSLConnectionSocketFactory trustAnySSLConnectionSocketFactory()
    {
        return new CustomSSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
    }

    public static Registry<ConnectionSocketFactory> trustAnySSLSocketFactoryRegistry()
    {
        SSLConnectionSocketFactory sslConnectionSocketFactory = trustAnySSLConnectionSocketFactory();
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                .register("https", sslConnectionSocketFactory)
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

    /* ====================================================================================================== */

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
