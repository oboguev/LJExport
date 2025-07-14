package my.LJExport.styles;

import java.io.File;

import org.jsoup.nodes.Node;

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

    public static void processAllHtmlFiles(String styleCatalogDir,
            String styleFallbackDir,
            String htmlPagesRootDir,
            StyleProcessorAction action,
            String baseURL,
            boolean showProgress,
            boolean dryRun,
            HtmlFileBatchProcessingContext batchContext,
            StringBuilder errorMessageLog) throws Exception
    {
        StyleManager styleManager = new StyleManager(styleCatalogDir, styleFallbackDir);
        
        try
        {
            styleManager.init();
            processAllHtmlFiles(styleManager, htmlPagesRootDir, action, baseURL, showProgress, dryRun, batchContext, errorMessageLog);
        }
        finally
        {
            styleManager.close();
        }
    }

    public static void processAllHtmlFiles(StyleManager styleManager,
            String htmlPagesRootDir,
            StyleProcessorAction action,
            String baseURL,
            boolean showProgress,
            boolean dryRun,
            HtmlFileBatchProcessingContext batchContext, StringBuilder errorMessageLog) throws Exception
    {
        while (htmlPagesRootDir.endsWith(File.separator))
            htmlPagesRootDir = Util.stripTail(htmlPagesRootDir, File.separator);

        File fp = new File(htmlPagesRootDir).getCanonicalFile();
        if (!fp.exists() && fp.isDirectory())
            throw new Exception("Not a directory: " + fp.getCanonicalPath());

        for (String relPath : Util.enumerateAnyHtmlFiles(htmlPagesRootDir))
        {
            String path = htmlPagesRootDir + File.separator + relPath;

            if (showProgress)
                Util.out("Processing styles for " + path);
            
            batchContext.scannedHtmlFiles.incrementAndGet();
            styleManager.processHtmlFile(path, action, baseURL, dryRun, batchContext, errorMessageLog);
        }
    }

    /*
     * For all links to local styles, i.e. ../../{repeat}/../styles/remainder
     * add or remove preceding ../ as follows:
     * 
     * deltaLevel = +2   add ../../ 
     * deltaLevel = +1   add ../ 
     * deltaLevel = -1   remove ../ 
     * deltaLevel = -2   remove ../../ 
     */
    public static boolean relocaleLocalHtmlStyleReferences(Node pageRoot, int deltaLevel) throws Exception
    {
        return new StyleActionRelocate().relocaleLocalHtmlStyleReferences(pageRoot, deltaLevel);
    }
}
