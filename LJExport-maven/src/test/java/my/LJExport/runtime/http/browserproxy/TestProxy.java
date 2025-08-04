package my.LJExport.runtime.http.browserproxy;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;

import my.LJExport.Config;
import my.LJExport.runtime.Util;

public class TestProxy
{
    public static void main(String[] args)
    {
        try
        {
            TestProxy self = new TestProxy();
            self.test_1();
        }
        catch (Exception ex)
        {
            Util.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }

    private BrowserProxy getProxy()
    {
        return new BrowserProxyNodeJS("http://localhost:3000");
    }

    private void test_1() throws Exception
    {
        BrowserProxy bp = getProxy();

        BrowserProxy.Response r = null;
        List<BasicClientCookie> cookies = null;
        String s = null, href = null;
        List<BasicHeader> headers = null;
        Util.unused(cookies, s, r, headers, href);

        s = bp.getUserAgent();
        cookies = bp.getCookies();
        s = bp.getSecChUa();

        // Results in:
        //
        // GET https://www.yahoo.com/ HTTP/1.1
        // Host: www.yahoo.com
        // Connection: keep-alive
        // Upgrade-Insecure-Requests: 1
        // User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36
        // Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7
        // Sec-Fetch-Site: none
        // Sec-Fetch-Mode: navigate
        // Sec-Fetch-User: ?1
        // Sec-Fetch-Dest: document
        // Accept-Encoding: gzip, deflate, br, zstd
        // Accept-Language: en-US,en;q=0.9
        if (Util.True)
        {
            headers = new ArrayList<>();
            r = bp.executeGetRequest("https://www.yahoo.com", headers, true);
            Util.noop();
        }

        if (Util.True)
        {
            headers = new ArrayList<>();
            addHeader(headers, "Sec-Fetch-Dest", "image");
            addHeader(headers, "Accept", Config.UserAgentAccept_Download);
            addHeader(headers, "X-Test", "test1");
            addHeader(headers, "Referer", "http://kittens.com/kittens.html");
            href = "http://upload.wikimedia.org/wikipedia/commons/2/2f/Amtrak_Auto_Train_52_Passing_Through_Guinea_Station%2C_Virginia.jpg";
            // href = "http://www.diamondpet.com/wp-content/uploads/2021/03/kitten-sitting-on-floor-031621.jpg";
            r = bp.executeGetRequest(href, headers, true);
            Util.noop();
        }

        Util.noop();
    }

    private void addHeader(List<BasicHeader> headers, String key, String value)
    {
        headers.add(new BasicHeader(key, value));
    }
}
