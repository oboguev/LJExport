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
