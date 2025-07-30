package my.LJExport.runtime.http.browserproxy;

import org.apache.http.message.BasicHeader;
import java.util.List;

public interface BrowserProxy
{
    public Response executePostRequest(String url, List<BasicHeader> headers, byte[] body) throws Exception;

    public Response executeGetRequest(String url, List<BasicHeader> headers) throws Exception;

    public static class Response
    {
        public int status;
        public List<BasicHeader> headers;
        public byte[] body;
    }
}