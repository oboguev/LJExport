package my.LJExport.monthly;

import java.io.File;
import java.util.List;

import my.LJExport.MainDownloadLinks.PageParserDirectBasePassive;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.runtime.Util;

public class MonthProcessor
{
    private final String pagesMonthDir;
    private final List<String> pageFileNames;
    private final String monthlyFilePrefix;

    public MonthProcessor(String pagesMonthDir, List<String> pageFileNames, String monthlyFilePrefix)
    {
        this.pagesMonthDir = pagesMonthDir;
        this.pageFileNames = pageFileNames;
        this.monthlyFilePrefix = monthlyFilePrefix;
    }
    
    public void process() throws Exception
    {
        for (String fn : pageFileNames)
        {
            String pageFileFullPath = pagesMonthDir + File.separator + fn;
            
            PageParserDirectBase parser = new PageParserDirectBasePassive();
            parser.rurl = Util.extractFileName(pageFileFullPath);
            
            parser.pageSource = Util.readFileAsString(pageFileFullPath);
            parser.parseHtml(parser.pageSource);
        }
        
        Util.noop();
    }
}
