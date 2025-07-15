package my.LJExport.styles;

import java.io.File;
import java.util.Objects;

import org.jsoup.nodes.Node;

import my.LJExport.runtime.ErrorMessageLog;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.parallel.twostage.parser.ParserParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.parser.ParserStage1Processor;
import my.LJExport.runtime.parallel.twostage.parser.ParserWorkContext;

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
            ErrorMessageLog errorMessageLog, int parallelism) throws Exception
    {
        StyleManager styleManager = new StyleManager(styleCatalogDir, styleFallbackDir, dryRun);

        try
        {
            styleManager.init();
            processAllHtmlFiles(styleManager, htmlPagesRootDir, action, baseURL, showProgress, dryRun, batchContext,
                    errorMessageLog, parallelism);
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
            HtmlFileBatchProcessingContext batchContext, ErrorMessageLog errorMessageLog, int parallelism) throws Exception
    {
        while (htmlPagesRootDir.endsWith(File.separator))
            htmlPagesRootDir = Util.stripTail(htmlPagesRootDir, File.separator);

        File fp = new File(htmlPagesRootDir).getCanonicalFile();
        if (!fp.exists() && fp.isDirectory())
            throw new Exception("Not a directory: " + fp.getCanonicalPath());

        if (parallelism <= 1)
        {
            for (String relPath : Util.enumerateAnyHtmlFiles(htmlPagesRootDir))
            {
                String path = htmlPagesRootDir + File.separator + relPath;

                if (showProgress)
                    Util.out("Processing styles for " + path);

                batchContext.scannedHtmlFiles.incrementAndGet();
                styleManager.processHtmlFile(path, action, baseURL, dryRun, batchContext, errorMessageLog, null);
            }
        }
        else
        {
            ParserParallelWorkContext ppwc = new ParserParallelWorkContext(Util.enumerateAnyHtmlFiles(htmlPagesRootDir),
                    new ParserStage1Processor(htmlPagesRootDir),
                    parallelism);

            try
            {
                for (ParserWorkContext wcx : ppwc)
                {
                    if (showProgress)
                        Util.out("Processing styles for " + wcx.fullFilePath);

                    Exception ex = wcx.getException();
                    if (ex != null)
                        throw new Exception("While processing " + wcx.fullFilePath, ex);
                    
                    batchContext.scannedHtmlFiles.incrementAndGet();
                    Objects.requireNonNull(wcx.parser, "parser is null");
                    styleManager.processHtmlFile(wcx.fullFilePath, action, baseURL, dryRun, batchContext, errorMessageLog, wcx.parser);
                }
            }
            finally
            {
                ppwc.close();
            }
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
