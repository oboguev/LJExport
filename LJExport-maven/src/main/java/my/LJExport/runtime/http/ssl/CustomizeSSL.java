package my.LJExport.runtime.http.ssl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.net.ssl.SSLSocket;

import my.LJExport.Config;

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
        list = shuffle(list);

        /*
         * Standard JDK TLS stack DOES NOT maintain ciper orderging,
         * only Conscyrpt TLS stack does.  
         */
        socket.setEnabledCipherSuites(list.toArray(new String[0]));
        socket.setEnabledProtocols(new String[] { "TLSv1.3", "TLSv1.2" });
    }
    
    private static List<String> permuted = null;
    
    private static synchronized List<String> shuffle(List<String> list)
    {
        if (permuted == null)
        {
            list = dup(list);
            list.remove("TLS_EMPTY_RENEGOTIATION_INFO_SCSV");
            list.remove("TLS_FALLBACK_SCSV");
            List<String> original_list = dup(list);
            shuffleCipherList(list, 4, 5, Config.TlsSignatureIncarnation);
            
            while (list.equals(original_list))
                shuffleCipherList(list, 4, 5, System.nanoTime());
            
            permuted = list;
        }

        return permuted;
    }

    private static void shuffleCipherList(List<String> list, int nPinFirst, int nPinLast, long shuffleIncarnation)
    {
        int size = list.size();

        if (nPinFirst + nPinLast >= size || size <= 1)
            return; // nothing to shuffle

        List<String> head = dup(list.subList(0, nPinFirst));
        List<String> middle = new ArrayList<>(list.subList(nPinFirst, size - nPinLast));
        List<String> tail = dup(list.subList(size - nPinLast, size));

        Collections.shuffle(middle, new Random(shuffleIncarnation));

        // Clear original list and rebuild in place
        list.clear();
        list.addAll(head);
        list.addAll(middle);
        list.addAll(tail);
    }
    
    private static List<String> dup(List<String> list)
    {
        return new ArrayList<>(list);
    }
}
