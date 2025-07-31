package my.LJExport.runtime.http.browserproxy;

import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import java.util.List;

public interface BrowserProxy
{
    public Response executePostRequest(String url, List<BasicHeader> headers, byte[] body, boolean followRedirects) throws Exception;

    public Response executeGetRequest(String url, List<BasicHeader> headers, boolean followRedirects) throws Exception;
    
    public default boolean canInterceptRedirection()
    {
        return true;
    }
    
    public default String getUserAgent() throws Exception
    {
        return null;
    }

    public default String getSecChUa() throws Exception
    {
        return null;
    }

    public default List<BasicClientCookie> getCookies() throws Exception
    {
        return null;
    }

    public static class Response
    {
        public int status;
        public List<BasicHeader> headers;
        public byte[] body;
    }
}