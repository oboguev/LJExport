package my.LJExport.runtime.http;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class HttpWireTracing
{
    public static void enable()
    {
        // options for tracing Apache HTTP Client 4
        ((Logger) LoggerFactory.getLogger("org.apache.http.wire")).setLevel(Level.TRACE);
        ((Logger) LoggerFactory.getLogger("org.apache.http.headers")).setLevel(Level.TRACE);
        ((Logger) LoggerFactory.getLogger("org.apache.http.impl.conn")).setLevel(Level.TRACE);

        // options for tracing Apache HTTP Client 5
        ((Logger) LoggerFactory.getLogger("org.apache.hc.client5.http")).setLevel(Level.TRACE);
        ((Logger) LoggerFactory.getLogger("org.apache.hc.client5.http.headers")).setLevel(Level.TRACE);
        ((Logger) LoggerFactory.getLogger("org.apache.hc.client5.http.wire")).setLevel(Level.TRACE);
        ((Logger) LoggerFactory.getLogger("org.apache.hc.client5.http.impl")).setLevel(Level.TRACE);
    }

    public static void enableSpring()
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
