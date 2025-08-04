package my.LJExport.runtime.http.ssl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLSocket;

public class CustomizeSSL
{
    public static ThreadLocal<Boolean> callShowSSL = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static String[] getDefaultCipherSuites(String[] defaultCipherSuites, String[] supportedCipherSuites)
    {
        return defaultCipherSuites;
    }

    public static String[] getSupportedCipherSuites(String[] defaultCipherSuites, String[] supportedCipherSuites)
    {
        return supportedCipherSuites;
    }

    public static void prepareSocket(final SSLSocket socket)
    {
        if (callShowSSL.get())
            ShowSSL.prepareSocket(socket);

        List<String> list = Arrays.asList(socket.getEnabledCipherSuites());
        list = new ArrayList<>(list);
        list.remove("TLS_EMPTY_RENEGOTIATION_INFO_SCSV");
        list.remove("TLS_FALLBACK_SCSV");
        shuffleCipherList(list, 4, 5);

        /*
         * Standard JDK TLS stack DOES NOT maintain ciper orderging,
         * bnly Conscyrpt TLS stack does.  
         */
        socket.setEnabledCipherSuites(list.toArray(new String[0]));
        socket.setEnabledProtocols(new String[] { "TLSv1.3", "TLSv1.2" });
    }

    private static void shuffleCipherList(List<String> list, int nPinFirst, int nPinLast)
    {
        int size = list.size();

        if (nPinFirst + nPinLast >= size || size <= 1)
            return; // nothing to shuffle

        List<String> head = list.subList(0, nPinFirst);
        List<String> middle = new ArrayList<>(list.subList(nPinFirst, size - nPinLast));
        List<String> tail = list.subList(size - nPinLast, size);

        Collections.shuffle(middle);

        // Clear original list and rebuild in place
        list.clear();
        list.addAll(head);
        list.addAll(middle);
        list.addAll(tail);
    }
}
