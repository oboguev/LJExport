package my.LJExport.runtime;

import javax.net.ssl.SSLException;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility for classifying whether a Throwable represents a network/HTTP problem.
 */
public final class NetErrors
{
    private static final Set<String> APACHE_NET_EX = new HashSet<>(Arrays.asList(
            "org.apache.http.client.ClientProtocolException",
            "org.apache.http.client.CircularRedirectException",
            "org.apache.http.NoHttpResponseException",
            "org.apache.http.conn.ConnectTimeoutException",
            "org.apache.http.conn.HttpHostConnectException"));

    private NetErrors()
    {
    }

    /**
     * @param ex
     *            any Throwable
     * @return {@code true} if {@code ex} (or any cause) is clearly due to network / HTTP I/O; {@code false} otherwise
     */
    public static boolean isNetworkException(Throwable ex)
    {
        for (Throwable t = ex; t != null; t = t.getCause())
        {
            if (t instanceof UnknownHostException
                    || t instanceof ConnectException
                    || t instanceof NoRouteToHostException
                    || t instanceof SocketTimeoutException
                    || t instanceof SocketException
                    || t instanceof MalformedURLException
                    || t instanceof SSLException)
                return true;

            if (APACHE_NET_EX.contains(t.getClass().getName()))
                return true;

            Package p = t.getClass().getPackage();
            if (p != null)
            {
                String pkg = p.getName();
                if (pkg.startsWith("java.net")
                        || pkg.startsWith("javax.net")
                        || pkg.startsWith("org.apache.http"))
                    return true;
            }
        }
        return false;
    }
}
