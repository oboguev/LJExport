package my.LJExport.runtime.lins;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.ShouldDownload;

public class TestLinks
{
    public static void main(String[] args)
    {
        try
        {
            TestLinks self = new TestLinks();
            Config.init("");
            Web.init();
            self.test_1();
        }
        catch (Exception ex)
        {
            Util.err("** Exception: " + ex.getLocalizedMessage());;
            ex.printStackTrace();
        }
    }
    
    private void test_1() throws Exception
    {
        boolean b = false;
        String href = "https://imgprx.livejournal.net/65243a387dc401ba8042f5344cfd8592d163bb55/e7072IPXyBeTj2iXMu5vZg7085xqpIbizD50nypPlzJeNvF0IIPx5ryx1FGD-NzwPD3hfrtJwUhLHknwYwEt2WOWeYuV4N2jvm3kIGMik6cAE12ut0lftxHty9VqSleS";
        b = ShouldDownload.shouldDownloadImage(href, false);
        b = ShouldDownload.shouldDownloadDocument(href, false);
        Util.unused(b);
    }
}
