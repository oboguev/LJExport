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
