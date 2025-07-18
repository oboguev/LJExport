package my.LJExport.runtime;

import my.LJExport.runtime.file.FileTypeDetector;

public class DemoFileTypeDetector
{
    public static void main(String[] args)
    {
        try
        {
            test("c:\\@\\1.png");
            test("c:\\@downloads\\test-octet-stream.bin");
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
        String mime = FileTypeDetector.mimeTypeFromActualFileContent(ba); // image/png
        String ext = FileTypeDetector.fileExtensionFromActualFileContent(ba); // .png
        Util.unused(mime, ext);
    }
}
