package my.LJExport.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import my.LJExport.runtime.url.AwayLink;
import my.LJExport.runtime.url.UrlConsolidator;
import my.LJExport.runtime.url.UrlUtil;

public class MiscTest
{
    public static void main(String[] args)
    {
        try
        {
            // test_1();
            consolidate();
            // test_unwrap();
        }
        catch (Exception ex)
        {
            Util.err("*** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    private static void test_1() throws Exception
    {
        test_1("https://vk.com/away.php?to=http%3A%2F%2Frigort.livejournal.com%2F1788988.html%3Futm_source%3Dvksharing%26utm_medium%3Dsocial&amp;post=-89424527_119771&amp;cc_key=");
        test_encode(
                "http://rigort.livejournal.com/1788988.html?utm_source=vksharing&utm_medium=social&amp;post=-89424527_119771&amp;cc_key=");
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

    /* ========================================================================================================================= */

    @SuppressWarnings("unused")
    private static void consolidate() throws Exception
    {
        consolidate(
                "https://vk.com/away.php?to=https://scontent.xx.fbcdn.net/v/t1.0-9/14102445_852529494848760_1139406795505667796_n.jpg?oh=214146c3d68ec8711cbd07d56c04f209&oe=584A844B",
                "https://vk.com/away.php?to=https%3A%2F%2Fscontent.xx.fbcdn.net%2Fv%2Ft1.0-9%2F14102445_852529494848760_1139406795505667796_n.jpg%3Foh%3D214146c3d68ec8711cbd07d56c04f209%26oe%3D584A844B");

        consolidate(
                "https://vk.com/away.php?to=https://scontent.xx.fbcdn.net/hphotos-xap1/v/t1.0-9/12410525_10153839742786462_7478520602963466481_n.jpg?oh=2f2e045d9cf31274d398a913d38f0595&oe=5749916A",
                "https://vk.com/away.php?to=https%3A%2F%2Fscontent.xx.fbcdn.net%2Fhphotos-xap1%2Fv%2Ft1.0-9%2F12410525_10153839742786462_7478520602963466481_n.jpg%3Foh%3D2f2e045d9cf31274d398a913d38f0595%26oe%3D5749916A");

        consolidate(
                "https://yandex.ru/images/search?pos=26&from=tabbar&img_url=https://lookaside.fbsbx.com/lookaside/crawler/media/?media_id=619377832930905&get_thumbnail=1&text=стапелии цветы&rpt=simage&lr=121704",
                "https://yandex.ru/images/search?pos=26&from=tabbar&img_url=https%3A%2F%2Flookaside.fbsbx.com%2Flookaside%2Fcrawler%2Fmedia%2F%3Fmedia_id%3D619377832930905%26get_thumbnail%3D1&text=стапелии+цветы&rpt=simage&lr=121704");

        consolidate(
                "https://yandex.ru/images/search?text=чехов фото&from=tabbar&pos=1&img_url=https://scontent-hel2-1.cdninstagram.com/v/t51.2885-15/e35/107960072_123678059088779_6456558451797944288_n.jpg?_nc_ht=scontent-hel2-1.cdninstagram.com&_nc_cat=111&_nc_ohc=QQqFsRcwNW8AX_pIgGw&oh=553fbfc6b0eff463f233fa76a5500048&oe=5F3C31B0&rpt=simage",
                "https://yandex.ru/images/search?text=%D1%87%D0%B5%D1%85%D0%BE%D0%B2%20%D1%84%D0%BE%D1%82%D0%BE&from=tabbar&pos=1&img_url=https%3A%2F%2Fscontent-hel2-1.cdninstagram.com%2Fv%2Ft51.2885-15%2Fe35%2F107960072_123678059088779_6456558451797944288_n.jpg%3F_nc_ht%3Dscontent-hel2-1.cdninstagram.com%26_nc_cat%3D111%26_nc_ohc%3DQQqFsRcwNW8AX_pIgGw%26oh%3D553fbfc6b0eff463f233fa76a5500048%26oe%3D5F3C31B0&rpt=simage");

        consolidate("https://www.livejournal.com/away/?to=https://dzen.ru/a/aA8bUsz0HUyQsaVt",
                "https://www.livejournal.com/away/?to=https%3A%2F%2Fdzen.ru%2Fa%2FaA8bUsz0HUyQsaVt%23hramozdatelstvo_knyazya_vladimira%3A%7E%3Atext%3D%25D0%25AD%25D1%2582%25D1%2583%2520%25D1%2581%25D1%2582%25D0%25B0%25D1%2582%25D1%258C%25D1%258E%2520%25D0%25BC%25D0%25BE%25D0%25B6%25D0%25B5%25D1%2582,%25D0%25A5%25D1%2580%25D0%25B0%25D0%25BC%25D0%25BE%25D0%25B7%25D0%25B4%25D0%25B0%25D1%2582%25D0%25B5%25D0%25BB%25D1%258C%25D1%2581%25D1%2582%25D0%25B2%25D0%25BE%2520%25D0%25BA%25D0%25BD%25D1%258F%25D0%25B7%25D1%258F%2520%25D0%2592%25D0%25BB%25D0%25B0%25D0%25B4%25D0%25B8%25D0%25BC%25D0%25B8%25D1%2580%25D0%25B0");

        consolidate(
                "http://s1.proxypy.org/p?q=Z3BqLmdpcm9fZjg5ZGExZGNfZjlkMDExXzAvMzkyLjM4NDU4MTk2LzQxOTIvdGVnL3VyLnhlZG5heS5pa3RvZi1nbWkvLzpzcHR0aA==",
                "http://s1.proxypy.org/p?q=Z3BqLmdpcm9fZjg5ZGExZGNfZjlkMDExXzAvMzkyLjM4NDU4MTk2LzQxOTIvdGVnL3VyLnhlZG5h%0AeS5pa3RvZi1nbWkvLzpzcHR0aA%3D%3D%0A");

        consolidate("http://stanishevsky.com/write/img/fPushkin.ttf/s24/c704F32/bFBEABD/tа кто из вас либерал?.png",
                "http://stanishevsky.com/write/img/fPushkin.ttf/s24/c704F32/bFBEABD/t%D0%B0%20%D0%BA%D1%82%D0%BE%20%D0%B8%D0%B7%20%D0%B2%D0%B0%D1%81%20%D0%BB%D0%B8%D0%B1%D0%B5%D1%80%D0%B0%D0%BB%3F.png");
    }

    private static void consolidate(String... ar) throws Exception
    {
        List<String> urls = Arrays.asList(ar);
        urls = prepare(urls);

        String url = UrlConsolidator.consolidateUrlVariants(urls, false);

        if (url == null)
            url = UrlConsolidator.consolidateUrlVariants(urls, true);

        if (url == null)
        {
            Util.out("FAILED =======================");
            for (String s : urls)
                Util.out("     " + s);
            Util.out("");
        }
        else
        {
            try
            {
                new URI(url);
                Util.out("OK  =======================");
                for (String s : urls)
                    Util.out("     " + s);
                Util.out("  => " + url);
                Util.out("");
            }
            catch (Exception ex)
            {
                // malformed url
                Util.err("consolidateUrlVariants returned malformed url " + url);
            }
        }
    }

    private static List<String> prepare(List<String> urls) throws Exception
    {
        List<String> list = new ArrayList<>();

        for (String s : urls)
        {
            s = AwayLink.unwrapAwayLinkDecoded(s);
            s = Util.stripAnchor(s);
            list.add(s);
        }
        
        return list;
    }

    /* ========================================================================================================================= */

    @SuppressWarnings("unused")
    private static void test_unwrap() throws Exception
    {
        test_unwrap("https://www.livejournal.com/away/?to=https://dzen.ru/a/aA8bUsz0HUyQsaVt");
        test_unwrap(
                "https://www.livejournal.com/away/?to=https%3A%2F%2Fdzen.ru%2Fa%2FaA8bUsz0HUyQsaVt%23hramozdatelstvo_knyazya_vladimira%3A%7E%3Atext%3D%25D0%25AD%25D1%2582%25D1%2583%2520%25D1%2581%25D1%2582%25D0%25B0%25D1%2582%25D1%258C%25D1%258E%2520%25D0%25BC%25D0%25BE%25D0%25B6%25D0%25B5%25D1%2582,%25D0%25A5%25D1%2580%25D0%25B0%25D0%25BC%25D0%25BE%25D0%25B7%25D0%25B4%25D0%25B0%25D1%2582%25D0%25B5%25D0%25BB%25D1%258C%25D1%2581%25D1%2582%25D0%25B2%25D0%25BE%2520%25D0%25BA%25D0%25BD%25D1%258F%25D0%25B7%25D1%258F%2520%25D0%2592%25D0%25BB%25D0%25B0%25D0%25B4%25D0%25B8%25D0%25BC%25D0%25B8%25D1%2580%25D0%25B0");

        test_unwrap(
                "https://vk.com/away.php?to=https://scontent.xx.fbcdn.net/v/t1.0-9/14102445_852529494848760_1139406795505667796_n.jpg?oh=214146c3d68ec8711cbd07d56c04f209&oe=584A844B");
        test_unwrap(
                "https://vk.com/away.php?to=https%3A%2F%2Fscontent.xx.fbcdn.net%2Fv%2Ft1.0-9%2F14102445_852529494848760_1139406795505667796_n.jpg%3Foh%3D214146c3d68ec8711cbd07d56c04f209%26oe%3D584A844B");

        test_unwrap(
                "https://vk.com/away.php?to=https://scontent.xx.fbcdn.net/hphotos-xap1/v/t1.0-9/12410525_10153839742786462_7478520602963466481_n.jpg?oh=2f2e045d9cf31274d398a913d38f0595&oe=5749916A");
        test_unwrap(
                "https://vk.com/away.php?to=https%3A%2F%2Fscontent.xx.fbcdn.net%2Fhphotos-xap1%2Fv%2Ft1.0-9%2F12410525_10153839742786462_7478520602963466481_n.jpg%3Foh%3D2f2e045d9cf31274d398a913d38f0595%26oe%3D5749916A");

        test_unwrap(
                "https://www.facebook.com/l.php?u=https%3A%2F%2Foslofreedomforum.com%2Fevents%2F2014-oslo-freedom-forum&amp;h=LAQE94R0e&amp;s=1");

        test_unwrap(
                "http://www.facebook.com/l.php?u=http%3A%2F%2Fru.wikipedia.org%2Fwiki%2F%25CF%25E0%25EC%25FF%25F2%25ED%25E8%25EA&amp;h=pAQFD1SIe");

        test_unwrap(
                "http://l.facebook.com/l.php?u=http%3A%2F%2Fkhodorkovsky.ru%2Fmbh%2Fnews%2Foslo%2F%23comments&amp;h=kAQH2_-7U&amp;s=1");
    }

    @SuppressWarnings("unused")
    private static void test_unwrap(String s) throws Exception
    {
        Util.out("   " + s);
        Util.out("D  " + AwayLink.unwrapAwayLinkDecoded(s));
        Util.out("");
    }
}
