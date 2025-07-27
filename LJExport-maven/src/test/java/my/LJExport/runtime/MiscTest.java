package my.LJExport.runtime;

import my.LJExport.runtime.url.UrlUtil;

public class MiscTest
{
    public static void main(String[] args)
    {
        try
        {
            test_1();
        }
        catch (Exception ex)
        {
            Util.err("*** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }
    
    private static void test_1() throws Exception
    {
        test_1("https://vk.com/away.php?to=http%3A%2F%2Frigort.livejournal.com%2F1788988.html%3Futm_source%3Dvksharing%26utm_medium%3Dsocial&amp;post=-89424527_119771&amp;cc_key=");
        test_encode("http://rigort.livejournal.com/1788988.html?utm_source=vksharing&utm_medium=social&amp;post=-89424527_119771&amp;cc_key=");
    }

    @SuppressWarnings("unused")
    private static void test_1(String url) throws Exception
    {
        String x = UrlUtil.decodeHtmlAttrLink(url);
        Util.out("H  " + url);
        Util.out("R  " + x);
        Util.out("H  " + UrlUtil.encodeUrlForHtmlAttr(x));
        Util.out("");
    }

    @SuppressWarnings("unused")
    private static void test_encode(String url) throws Exception
    {
        Util.out("R  " + url);
        Util.out("H  " + UrlUtil.encodeUrlForHtmlAttr(url));
        Util.out("");
    }
}
