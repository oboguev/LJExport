package my.LJExport.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.client.CookieStore;

import my.LJExport.runtime.browsers.FirefoxCookies;
import my.LJExport.runtime.url.AwayLink;
import my.LJExport.runtime.url.UrlConsolidator;
import my.LJExport.runtime.url.UrlFixCP1251;
import my.LJExport.runtime.url.UrlUtil;

public class MiscTest
{
    public static void main(String[] args)
    {
        try
        {
            // test_1();
            // test_2();
            // test_firefox_coookies();
            // consolidate();
         // test_unwrap();
            // test_infonarod_away();
            // test_cp1251();
            test_encode_url();
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
        test_encode("https://cloud.mail.ru/public/15220191b401/речь зацепина в прениях - 2014.pdf");
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

    @SuppressWarnings("unused")
    private static void test_2() throws Exception
    {
        test_2("HTTPS://Example.COM:443/Путь/Ресурс?q=значение#anchor");
    }

    private static void test_2(String url) throws Exception
    {
        String xurl;
        Util.out(url);
        Util.out(xurl = UrlUtil.encodeUrlForApacheWire(url));
        Util.out(UrlUtil.decodeUrl(xurl));
        Util.out("");
    }

    /* ========================================================================================================================= */

    @SuppressWarnings("unused")
    private static void consolidate() throws Exception
    {
        consolidate("https://www.otzywy.com",
                "https://www.otzywy.com/");

        consolidate(
                "http://img3.joyreactor.cc/pics/post/full/countryballs-Комиксы-польша-песочница-803313.jpeg",
                "http://img3.joyreactor.cc/pics/post/full/countryballs-Комиксы-Польша-песочница-803313.jpeg",
                "http://img3.joyreactor.cc/pics/post/full/countryballs-%D0%9A%D0%BE%D0%BC%D0%B8%D0%BA%D1%81%D1%8B-%D0%BF%D0%BE%D0%BB%D1%8C%D1%88%D0%B0-%D0%BF%D0%B5%D1%81%D0%BE%D1%87%D0%BD%D0%B8%D1%86%D0%B0-803313.jpeg",
                "http://img3.joyreactor.cc/pics/post/full/countryballs-%D0%9A%D0%BE%D0%BC%D0%B8%D0%BA%D1%81%D1%8B-%D0%9F%D0%BE%D0%BB%D1%8C%D1%88%D0%B0-%D0%BF%D0%B5%D1%81%D0%BE%D1%87%D0%BD%D0%B8%D1%86%D0%B0-803313.jpeg");

        consolidate(
                "https://expert.ru:443/data/public/338045/338074/expert_774_023_jpg_625x625_q85.jpg",
                "http://expert.ru/data/public/338045/338074/expert_774_023_jpg_625x625_q85.jpg");

        consolidate(
                "http://i.imgur.com/TWqVwm5.jpg",
                "https://i.imgur.com/TWqVwm5.jpg");

        consolidate(
                "http://i486.photobucket.com/albums/rr221/zletcorsab/FLO/RMSOlympic2.jpg",
                "https://i486.photobucket.com/albums/rr221/zletcorsab/FLO/RMSOlympic2.jpg");

        consolidate(
                "https://i.imgur.com/oR75hhd.jpg",
                "http://i.imgur.com/oR75hhd.jpg");

        consolidate(
                "http://i.imgur.com/TWqVwm5.jpg",
                "https://i.imgur.com/TWqVwm5.jpg");

        consolidate(
                "https://l-userpic.livejournal.com/63804671/10265057",
                "http://l-userpic.livejournal.com/63804671/10265057");

        consolidate(
                "https://yandex.ru/images/search?pos=26&from=tabbar&img_url=https://lookaside.fbsbx.com/lookaside/crawler/media/?media_id=619377832930905&get_thumbnail=1&text=стапелии цветы&rpt=simage&lr=121704",
                "https://yandex.ru/images/search?pos=26&from=tabbar&img_url=https%3A%2F%2Flookaside.fbsbx.com%2Flookaside%2Fcrawler%2Fmedia%2F%3Fmedia_id%3D619377832930905%26get_thumbnail%3D1&text=стапелии+цветы&rpt=simage&lr=121704");

        consolidate(
                "https://vk.com/away.php?to=https://scontent.xx.fbcdn.net/v/t1.0-9/14102445_852529494848760_1139406795505667796_n.jpg?oh=214146c3d68ec8711cbd07d56c04f209&oe=584A844B",
                "https://vk.com/away.php?to=https%3A%2F%2Fscontent.xx.fbcdn.net%2Fv%2Ft1.0-9%2F14102445_852529494848760_1139406795505667796_n.jpg%3Foh%3D214146c3d68ec8711cbd07d56c04f209%26oe%3D584A844B");

        consolidate(
                "https://vk.com/away.php?to=https://scontent.xx.fbcdn.net/hphotos-xap1/v/t1.0-9/12410525_10153839742786462_7478520602963466481_n.jpg?oh=2f2e045d9cf31274d398a913d38f0595&oe=5749916A",
                "https://vk.com/away.php?to=https%3A%2F%2Fscontent.xx.fbcdn.net%2Fhphotos-xap1%2Fv%2Ft1.0-9%2F12410525_10153839742786462_7478520602963466481_n.jpg%3Foh%3D2f2e045d9cf31274d398a913d38f0595%26oe%3D5749916A");

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

        Util.out("=========================================================");
        Util.out("");
        for (String s : urls)
            Util.out("    " + s);
        Util.out("");

        String url = UrlConsolidator.consolidateUrlVariants(urls, false);

        if (url == null)
        {
            Util.out("");
            Util.out("=== SECOND PASS ===");
            Util.out("");
            url = UrlConsolidator.consolidateUrlVariants(urls, true);
        }

        if (url == null)
        {
            Util.out("FAILED =======================");
        }
        else
        {
            try
            {
                new URI(url);
                Util.out("OK  =======================");
                Util.out("");
                Util.out("  => " + url);
            }
            catch (Exception ex)
            {
                // malformed url
                Util.err("consolidateUrlVariants returned malformed url " + url);
            }
        }

        Util.out("");
    }

    private static List<String> prepare(List<String> urls) throws Exception
    {
        List<String> list = new ArrayList<>();

        for (String s : urls)
        {
            s = AwayLink.unwrapAwayLinkDecoded(s);
            s = Util.stripAnchor(s);
            // s = UrlUtil.stripDefaultPort(s);
            list.add(s);
        }

        return list;
    }

    /* ========================================================================================================================= */

    @SuppressWarnings("unused")
    private static void test_unwrap() throws Exception
    {
        test_unwrap(true,
                "https://www.facebook.com/l.php?u=https%3A%2F%2Foslofreedomforum.com%2Fevents%2F2014-oslo-freedom-forum&amp;h=LAQE94R0e&amp;s=1");
        test_unwrap(true,
                "http://www.facebook.com/l.php?u=http%3A%2F%2Fru.wikipedia.org%2Fwiki%2F%25CF%25E0%25EC%25FF%25F2%25ED%25E8%25EA&amp;h=pAQFD1SIe");
        test_unwrap(true,
                "http://l.facebook.com/l.php?u=http%3A%2F%2Fkhodorkovsky.ru%2Fmbh%2Fnews%2Foslo%2F%23comments&amp;h=kAQH2_-7U&amp;s=1");

        test_unwrap(true,
                "https://external.xx.fbcdn.net/safe_image.php?d=AQASlRgcl1dld6f3&amp;w=487&amp;h=340&amp;url=https%3A%2F%2Fpbs.twimg.com%2Fmedia%2FChybXmJXEAA8m1V.jpg");
        test_unwrap(true,
                "https://external.xx.fbcdn.net/safe_image.php?d=AQDUeuZ_v3uu2lV7&amp;w=130&amp;h=130&amp;url=http%3A%2F%2Fwww.mk.ru%2Fupload%2Fentities%2F2016%2F07%2F25%2Farticles%2FdetailPicture%2F2e%2F4b%2Fb4%2F513367864_5216281.jpg&amp;cfs=1&amp;sx=110&amp;sy=0&amp;sw=412&amp;sh=412");
        test_unwrap(true,
                "https://external.xx.fbcdn.net/safe_image.php?d=AQCGFA0tZwkf8pbu&amp;w=130&amp;h=130&amp;url=http%3A%2F%2Fgotoroad.ru%2Fimg%2Fmap-index-life.jpg&amp;cfs=1&amp;_nc_hash=AQC6OxfHDMHpoNRy");

        test_unwrap(
                "https://www.livejournal.com/away/?to=https://dzen.ru/a/aA8bUsz0HUyQsaVt");

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
    }

    @SuppressWarnings("unused")
    private static void test_unwrap(String s) throws Exception
    {
        test_unwrap(false, s);
        
    }
    private static void test_unwrap(boolean unescape, String s) throws Exception
    {
        Util.out("   " + s);
        if (unescape)
        {
            s = StringEscapeUtils.unescapeHtml4(s);
            Util.out("U  " + s);
        }
        Util.out("D  " + AwayLink.unwrapAwayLinkDecoded(s));
        Util.out("");
    }

    /* ========================================================================================================================= */

    @SuppressWarnings("unused")
    private static void test_infonarod_away()
    {
        test_infonarod_away("http://infonarod.ru/away.php?url=http%253A%252F%252Fsocialist.memo.ru%252Fbooks%252Fdocuments.htm");
        test_infonarod_away(
                "http://infonarod.ru/away.php?url=https%253A%252F%252Fru.wikipedia.org%252Fwiki%252F%2525D0%25259B%2525D0%2525B5%2525D0%2525BD%2525D0%2525B8%2525D0%2525BD%252C_%2525D0%252592%2525D0%2525BB%2525D0%2525B0%2525D0%2525B4%2525D0%2525B8%2525D0%2525BC%2525D0%2525B8%2525D1%252580_%2525D0%252598%2525D0%2525BB%2525D1%25258C%2525D0%2525B8%2525D1%252587");
        test_infonarod_away(
                "http://infonarod.ru/away.php?url=https%253A%252F%252Fru.wikipedia.org%252Fwiki%252F%2525D0%2525A1%2525D0%2525B2%2525D0%2525B5%2525D1%252580%2525D0%2525B4%2525D0%2525BB%2525D0%2525BE%2525D0%2525B2%252C_%2525D0%2525AF%2525D0%2525BA%2525D0%2525BE%2525D0%2525B2_%2525D0%25259C%2525D0%2525B8%2525D1%252585%2525D0%2525B0%2525D0%2525B9%2525D0%2525BB%2525D0%2525BE%2525D0%2525B2%2525D0%2525B8%2525D1%252587");
        test_infonarod_away(
                "http://infonarod.ru/away.php?url=https%253A%252F%252Fru.wikipedia.org%252Fwiki%252F%2525D0%25259F%2525D0%2525BE%2525D0%2525B4%2525D0%2525B2%2525D0%2525BE%2525D0%2525B9%2525D1%252581%2525D0%2525BA%2525D0%2525B8%2525D0%2525B9%252C_%2525D0%25259D%2525D0%2525B8%2525D0%2525BA%2525D0%2525BE%2525D0%2525BB%2525D0%2525B0%2525D0%2525B9_%2525D0%252598%2525D0%2525BB%2525D1%25258C%2525D0%2525B8%2525D1%252587");
        test_infonarod_away(
                "http://infonarod.ru/away.php?url=https%253A%252F%252Fru.wikipedia.org%252Fwiki%252F%2525D0%2525A3%2525D1%252580%2525D0%2525B8%2525D1%252586%2525D0%2525BA%2525D0%2525B8%2525D0%2525B9%252C_%2525D0%25259C%2525D0%2525BE%2525D0%2525B8%2525D1%252581%2525D0%2525B5%2525D0%2525B9_%2525D0%2525A1%2525D0%2525BE%2525D0%2525BB%2525D0%2525BE%2525D0%2525BC%2525D0%2525BE%2525D0%2525BD%2525D0%2525BE%2525D0%2525B2%2525D0%2525B8%2525D1%252587");
        test_infonarod_away(
                "http://infonarod.ru/away.php?url=https%253A%252F%252Fru.wikipedia.org%252Fwiki%252F%2525D0%252591%2525D0%2525BE%2525D0%2525BD%2525D1%252587-%2525D0%252591%2525D1%252580%2525D1%252583%2525D0%2525B5%2525D0%2525B2%2525D0%2525B8%2525D1%252587%252C_%2525D0%252592%2525D0%2525BB%2525D0%2525B0%2525D0%2525B4%2525D0%2525B8%2525D0%2525BC%2525D0%2525B8%2525D1%252580_%2525D0%252594%2525D0%2525BC%2525D0%2525B8%2525D1%252582%2525D1%252580%2525D0%2525B8%2525D0%2525B5%2525D0%2525B2%2525D0%2525B8%2525D1%252587");
        test_infonarod_away(
                "http://infonarod.ru/away.php?url=https%253A%252F%252Fru.wikipedia.org%252Fwiki%252F%2525D0%2525A4%2525D0%2525B8%2525D0%2525BB%2525D0%2525B8%2525D0%2525BF%2525D0%2525BF%2525D0%2525BE%2525D0%2525B2%2525D1%252581%2525D0%2525BA%2525D0%2525B8%2525D0%2525B9%252C_%2525D0%252592%2525D0%2525B0%2525D1%252581%2525D0%2525B8%2525D0%2525BB%2525D0%2525B8%2525D0%2525B9_%2525D0%25259D%2525D0%2525B8%2525D0%2525BA%2525D0%2525BE%2525D0%2525BB%2525D0%2525B0%2525D0%2525B5%2525D0%2525B2%2525D0%2525B8%2525D1%252587");
        test_infonarod_away(
                "http://infonarod.ru/away.php?url=https%253A%252F%252Fru.wikipedia.org%252Fwiki%252F%2525D0%25259A%2525D1%252580%2525D1%25258B%2525D0%2525BB%2525D0%2525B5%2525D0%2525BD%2525D0%2525BA%2525D0%2525BE%252C_%2525D0%25259D%2525D0%2525B8%2525D0%2525BA%2525D0%2525BE%2525D0%2525BB%2525D0%2525B0%2525D0%2525B9_%2525D0%252592%2525D0%2525B0%2525D1%252581%2525D0%2525B8%2525D0%2525BB%2525D1%25258C%2525D0%2525B5%2525D0%2525B2%2525D0%2525B8%2525D1%252587");
        test_infonarod_away(
                "http://infonarod.ru/away.php?url=https%253A%252F%252Fru.wikipedia.org%252Fwiki%252F%2525D0%2525A2%2525D0%2525B0%2525D0%2525B2%2525D1%252580%2525D0%2525B8%2525D1%252587%2525D0%2525B5%2525D1%252581%2525D0%2525BA%2525D0%2525B8%2525D0%2525B9_%2525D0%2525B4%2525D0%2525B2%2525D0%2525BE%2525D1%252580%2525D0%2525B5%2525D1%252586");
    }

    private static void test_infonarod_away(String s)
    {
        Util.out(s);

        String decoded = UrlUtil.decodeHtmlAttrLink(s);
        Util.out(decoded);

        String xurl = AwayLink.unwrapInfonarodRuAaway(decoded);
        Util.out(xurl);

        Util.out("");
    }

    /* ========================================================================================================================= */

    @SuppressWarnings("unused")
    private static void test_cp1251()
    {
        test_cp1251("http://ru.wikipedia.org/wiki/%CF%E0%EC%FF%F2%ED%E8%EA");
        test_cp1251("---2F%7A%7A%7A%2F%E9%E9%E9%2F%7A%7A%7A%E9%E9%E9%3F%7A%7A%7A---");
        test_cp1251(
                "---2F%7A%7A%7A%2F%E9%E9%E9%2F%7A%7A%7A%E9%E9%E9%3F%7A%7A%7A---2F%7A%7A%7A%2F%E9%E9%E9%2F%7A%7A%7A%E9%E9%E9%3F%7A%7A%7A---http://ru.wikipedia.org/wiki/%CF%E0%EC%FF%F2%ED%E8%EA---");
    }

    private static void test_cp1251(String original)
    {
        Util.out(original);
        String fixed = UrlFixCP1251.fixUrlCp1251Sequences(original);
        Util.out(fixed);
        Util.out(UrlUtil.decodeUrl(fixed));

        Util.out("");
    }
    /* ========================================================================================================================= */

    @SuppressWarnings("unused")
    private static void test_firefox_coookies() throws Exception
    {
        CookieStore cs = FirefoxCookies.loadCookiesFromFirefox();
        Util.unused(cs);
        Util.noop();
    }

    @SuppressWarnings("unused")
    private static void test_encode_url() throws Exception
    {
        test_encode_url("https://web.archive.org/cdx/search/cdx?output=json&fl=timestamp,original,statuscode&filter=statuscode:200&matchType=exact&limit=1&url=http%3A%2F%2F1.bp.blogspot.com%2F_h_hLztz7W0s%2FSq0s6CwFrJI%2FAAAAAAAADX4%2FxfV04qkGa1A%2Fs1600-h%2FCheKa.JPG");
        test_encode_url("https://web.archive.org/cdx/search/cdx?output=json&fl=timestamp,original,statuscode&filter=statuscode:200&matchType=exact&limit=1&url=http%3A%2F%2F1.bp.blogspot.com%2F_h_hLztz7W0s%2FSq0s6CwFrJI%2FAAAAAAAADX4%2FxfV04qkGa1A%2Fs1600-h%2FCheKa.JPGяяя-ййй");
    }

    private static void test_encode_url(String s) throws Exception
    {
        Util.out("    " + s);
        Util.out("X   " + UrlUtil.encodeUrlForApacheWire(s));
        Util.out("    ");
    }
}
