package my.LJExport.styles;

import java.io.File;

import my.LJExport.runtime.Util;

public class StyleProcessor
{
    public enum StyleProcessorAction
    {
        /* load remote styles to local repository and patch HTML files to reference local copies */
        TO_LOCAL,

        /* revert the change to HTML files */
        REVERT
    }

    public static void processAllHtmlFiles(String styleCatalogDir, String htmlPagesRootDir, StyleProcessorAction action,
            String baseURL) throws Exception
    {
        StyleManager styleManager = new StyleManager(styleCatalogDir);
        try
        {
            styleManager.init();
            processAllHtmlFiles(styleManager, htmlPagesRootDir, action, baseURL);
        }
        finally
        {
            styleManager.close();
        }
    }

    public static void processAllHtmlFiles(StyleManager styleManager, String htmlPagesRootDir, StyleProcessorAction action,
            String baseURL) throws Exception
    {
        while (htmlPagesRootDir.endsWith(File.separator))
            htmlPagesRootDir = Util.stripTail(htmlPagesRootDir, File.separator);

        File fp = new File(htmlPagesRootDir).getCanonicalFile();
        if (!fp.exists() && fp.isDirectory())
            throw new Exception("Not a directory: " + fp.getCanonicalPath());

        for (String relPath : Util.enumerateAnyHtmlFiles(htmlPagesRootDir))
        {
            String path = htmlPagesRootDir + File.separator + relPath;
            styleManager.processHtmlFile(path, action, baseURL);
        }
    }
}
