package my.LJExport.runtime;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import my.LJExport.Config;
import my.LJExport.runtime.file.FilePath;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.url.UrlConsolidator;

public class DemoFileTypeDetector
{
    public static void main(String[] args)
    {
        try
        {
            if (Config.False)
            {
                test("c:\\@\\1.png");
                test("c:\\@downloads\\test-octet-stream.bin");

                test2("c:\\qqq\\aaa.bbb");
                test2("c:\\qqq\\aaa.zzz");

                test3();
            }

            consolidate();
        }
        catch (Exception ex)
        {
            Util.err("*** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }

    private static void test(String path) throws Exception
    {
        byte[] ba = Util.readFileAsByteArray(path);
        String mime = FileTypeDetector.mimeTypeFromActualFileContent(ba, null); // image/png
        String ext = FileTypeDetector.fileExtensionFromActualFileContent(ba, null); // png
        Util.unused(mime, ext);
    }

    private static void test2(String path) throws Exception
    {
        String xpath = FilePath.getFilePathActualCase(path);
        Util.unused(xpath);
    }

    private static void test3() throws Exception
    {
        String ext = FileTypeDetector.fileExtensionFromMimeType("text/plain; charset=windows-1251");
        Util.unused(ext);

        // txt
        test3("F:/WINAPPS/LJExport/journals/krylov/links/www.lib.ru/ANEKDOTY/PODDEREV/pavlik.txt_Ascii.txt");
        Util.out("");

        // txt <style> <div class=
        test3("F:/WINAPPS/LJExport/journals/colonelcassad/links/politikus.ru/uploads/posts/2014-05/1401156278_6857961.jpg");
        test3("F:/WINAPPS/LJExport/journals/colonelcassad/links/politikus.ru/uploads/posts/2014-06/1403529552_1009519164.jpg");
        test3("F:/WINAPPS/LJExport/journals/colonelcassad/links/politikus.ru/uploads/posts/2015-03/1425456982_snimok1.jpg");
        test3("F:/WINAPPS/LJExport/journals/oboguev/links/politikus.ru/uploads/posts/2014-11/1415736098_871223.jpg");
        test3("F:/WINAPPS/LJExport/journals/tanya_mass/links/politikus.ru/uploads/forum/images/1376453236.jpg");
        Util.out("");

        // none (http response with headers)
        test3("F:/WINAPPS/LJExport/journals/oboguev/links/www.loveplanet.lv/host/humor/c7/b_h38599.jpg");
        Util.out("");

        // octet
        test3("F:/WINAPPS/LJExport/journals/man_with_dogs/links/i.imgur.com/VRKxq3w.jpg");
        test3("F:/WINAPPS/LJExport/journals/shrek1/links/ic.pics.livejournal.com/kolokoll/68075906/12282/12282_original.jpg");
        test3("F:/WINAPPS/LJExport/journals/colonelcassad/links/www.optima-finance.ru/add-on/comchart.php%3Fwidth=728&height=340&ticker=CB&history=200.php");
        test3("F:/WINAPPS/LJExport/journals/pavell/links/imgprx.livejournal.net/x-66de5a3923ec4ddba8030abdece93c32/x-83efb3cbe3bb4b7dbb123f504dbea913");
        test3("F:/WINAPPS/LJExport/journals/ru_history/links/imgprx.livejournal.net/x-a45d523509cb4be191196722eac28e35/x-8dd5710c6bcc4cbfa3e035c2b453a412");
        test3("F:/WINAPPS/LJExport/journals/shrek1/links/l-userpic.livejournal.com/123215892/70283046");
        test3("F:/WINAPPS/LJExport/journals/pioneer_lj/links/timesmachine.nytimes.com/timesmachine/1872/03/12/121600740.pdf");
        test3("F:/WINAPPS/LJExport/journals/pioneer_lj/links/timesmachine.nytimes.com/timesmachine/1899/06/27/101127854.pdf");
        Util.out("");

        // ....................

        // doc
        test3("F:/WINAPPS/LJExport/journals/oboguev/links/murders.ru/Kyrs-s-s-sk.doc");

        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1980/vsemirno.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1982/korablik.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1985/jubilejn.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1990/moekoro3.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1992/rozhdest.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1993/ballada.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1994/chinatow.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1994/diktant.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1994/romans.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/blackalpinist.com/scherbakov/texts/1998/interme4.txt");
        test3("F:/WINAPPS/LJExport/journals/abcdefgh/links/lib.ru/ILFPETROV/ilf_fel.txt_Ascii.txt");
        test3("F:/WINAPPS/LJExport/journals/andronic/links/www.library.kherson.ua/astrid/14/Ich.txt");

        test3("F:/WINAPPS/LJExport/journals/avmalgin/links/kenigtiger.livejournal.com/robots.txt");
        test3("F:/WINAPPS/LJExport/journals/a_bugaev/links/cumatoid.narod.ru/publications/rozov_monograf_2006.rar");
        test3("F:/WINAPPS/LJExport/journals/alexandrov_g/links/left.ru/bib/barbarossa.rar");

        String DIR = "P:\\@downloads\\mime\\";

        test3(DIR + "a.7z");
        test3(DIR + "a.avif");
        test3(DIR + "a.bmp");
        test3(DIR + "a.dib");
        test3(DIR + "a.djvu");
        test3(DIR + "a.doc");
        test3(DIR + "a.docx");
        test3(DIR + "a.gif");
        test3(DIR + "a.jpeg");
        test3(DIR + "a.jpg");
        test3(DIR + "a.odt");
        test3(DIR + "a.pdf");
        test3(DIR + "a.png");
        test3(DIR + "a.rar");
        test3(DIR + "a.rtf");
        test3(DIR + "a.svg");
        test3(DIR + "a.tar");
        test3(DIR + "a.tar.gz");
        test3(DIR + "a.tgz");
        test3(DIR + "a.tif");
        test3(DIR + "a.tiff");
        test3(DIR + "a.webp");
        test3(DIR + "a.zip");

    }

    private static void test3(String path) throws Exception
    {
        path = path.replace("/", File.separator);
        String fnExt = LinkFilepath.getMediaFileExtension(path);
        byte[] ba = Util.readFileAsByteArray(path);
        String ext = FileTypeDetector.fileExtensionFromActualFileContent(ba, fnExt);

        Util.out(String.format("%s => %s", path, ext == null ? "<null>" : ext));
    }

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

        String url = UrlConsolidator.consolidateUrlVariants(urls, false);

        if (url == null)
            url = UrlConsolidator.consolidateUrlVariants(urls, true);

        if (url == null)
            Util.out("FAILED");
        else
            Util.out("OK");
    }
}
