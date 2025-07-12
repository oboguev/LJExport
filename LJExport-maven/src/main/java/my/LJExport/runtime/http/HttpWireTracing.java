package my.LJExport.runtime.http;

public class HttpWireTracing
{
    public static void enable()
    {
        // options for tracing Apache HTTP Client 4
        System.setProperty("logging.level.org.apache.http.wire", "TRACE");
        System.setProperty("logging.level.org.apache.http.headers", "TRACE");
        System.setProperty("org.apache.http.impl.conn", "TRACE");

        // options for tracing Apache HTTP Client 5
        System.setProperty("logging.level.org.apache.hc.client5.http", "TRACE");
        System.setProperty("logging.level.org.apache.hc.client5.http.headers", "TRACE");
        System.setProperty("logging.level.org.apache.hc.client5.http.wire", "TRACE");
        System.setProperty("logging.level.org.apache.hc.client5.http.impl", "TRACE");
    }
}
