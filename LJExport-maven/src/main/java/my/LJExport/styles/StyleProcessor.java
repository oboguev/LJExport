package my.LJExport.styles;

import java.io.File;

import my.LJExport.runtime.Util;

public class StyleProcessor
{
    public static void processAllHtmlFiles(StyleManager styleManager, String htmlPagesRootDir) throws Exception
    {
        while (htmlPagesRootDir.endsWith(File.separator))
            htmlPagesRootDir= Util.stripTail(htmlPagesRootDir, File.separator);

        File fp = new File(htmlPagesRootDir).getCanonicalFile();
        if (!fp.exists() && fp.isDirectory())
            throw new Exception("Not a directory: " + fp.getCanonicalPath());
        
        
        for (String relPath : Util.enumerateAnyHtmlFiles(htmlPagesRootDir))
        {
            String path = htmlPagesRootDir + File.separator + relPath;
            styleManager.processHtmlFile(path);
        }
    }
}
