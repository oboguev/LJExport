package my.LJExport.runtime;

import javax.net.ssl.*;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/*
 * Disable SSL certificate checking
 */
public class TrustAnySSL
{
    private static SSLContext sslContext;

    /**
     * Globally disable SSL certificate and hostname verification.
     */
    public static void trustAnySSL()
    {
        try
        {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] { new LooseTrustManager() };

            // Install the all-trusting trust manager
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            SSLContext.setDefault(sslContext);

            // Disable hostname verification globally
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
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
