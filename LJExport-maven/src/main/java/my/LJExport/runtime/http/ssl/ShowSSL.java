package my.LJExport.runtime.http.ssl;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.Web;

/*
 * In Wireshark, use filter
 * tls.handshake.type == 1
 */
public class ShowSSL
{
    public static ThreadLocal<Boolean> thrown = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void main(String[] args)
    {
        try
        {
            TrustAnySSL.trustAnySSL(true, true);
            showSSL();
        }
        catch (Exception ex)
        {
            Util.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }

    private static void showSSL() throws Exception
    {
        Config.init("");
        Web.init();
        thrown.set(false);

        try
        {
            CustomizeSSL.callShowSSL.set(true);
            Web.get("https://www.google.com/");
        }
        catch (Exception ex)
        {
            if (!thrown.get())
                throw ex;
            Util.noop();
        }
        finally
        {
            thrown.set(false);
            CustomizeSSL.callShowSSL.set(false);
        }
    }

    public static void prepareSocket(final SSLSocket socket)
    {
        Util.noop();

        show("SupportedCipherSuites", socket.getSupportedCipherSuites());
        show("EnabledCipherSuites", socket.getEnabledCipherSuites());

        show("SupportedProtocols", socket.getSupportedProtocols());
        show("EnabledProtocols", socket.getEnabledProtocols());

        SSLParameters params = socket.getSSLParameters();
        show("ApplicationProtocols", params.getApplicationProtocols());
        show("Protocols", params.getProtocols());
        show("CipherSuites", params.getCipherSuites());
        
        // socket.setEnabledCipherSuites(null);
        // socket.setEnabledProtocols(null);
        // params.setProtocols(null);
        // params.setCipherSuites(null);

        Util.out("");
        Util.out("CipherSuitesOrder: " + params.getUseCipherSuitesOrder());
        
        // SSLParameters.setUseCipherSuitesOrder(true);
        
        thrown.set(true);
        throw new RuntimeException("ShowSSL processed");
    }

    private static void show(String title, String[] list)
    {
        int count = list == null ? 0 : list.length;
        
        Util.out("");
        Util.out(title + "(" + count + ")");

        if (list == null)
        {
            Util.out("    -- null list --");
        }
        else if (list.length == 0)
        {
            Util.out("    -- empty list --");
        }
        else
        {
            for (String s : list)
                Util.out("    " + s);
        }
    }
}
