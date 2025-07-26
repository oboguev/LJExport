package my.LJExport.runtime;

import my.LJExport.runtime.file.FilePath;
import my.LJExport.runtime.file.FileTypeDetector;

public class DemoFileTypeDetector
{
    public static void main(String[] args)
    {
        try
        {
            test("c:\\@\\1.png");
            test("c:\\@downloads\\test-octet-stream.bin");

            test2("c:\\qqq\\aaa.bbb");
            test2("c:\\qqq\\aaa.zzz");
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
        String ext = FileTypeDetector.fileExtensionFromActualFileContent(ba, null); // .png
        Util.unused(mime, ext);
    }

    private static void test2(String path) throws Exception
    {
        String xpath = FilePath.getFilePathActualCase(path);
        Util.unused(xpath);
    }
}
