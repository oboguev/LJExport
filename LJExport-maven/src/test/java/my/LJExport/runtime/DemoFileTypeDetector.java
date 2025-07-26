package my.LJExport.runtime;

import java.io.File;

import my.LJExport.Config;
import my.LJExport.runtime.file.FilePath;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.links.util.LinkFilepath;

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
            }

            test3();
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
}
