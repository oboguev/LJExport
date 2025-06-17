package my.LJExport.monthly;

import java.io.File;
import java.util.List;

import my.LJExport.MainDownloadLinks.PageParserDirectBasePassive;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectClassic;
import my.LJExport.readers.direct.PageParserDirectNewStyle;
import my.LJExport.runtime.Util;

public class MonthProcessor
{
    private final String pagesMonthDir;
    private final List<String> pageFileNames;
    private final String monthlyFilePrefix;
    private final String year;
    private final String month;

    public MonthProcessor(String pagesMonthDir, List<String> pageFileNames, String monthlyFilePrefix, String year, String month)
    {
        this.pagesMonthDir = pagesMonthDir;
        this.pageFileNames = pageFileNames;
        this.monthlyFilePrefix = monthlyFilePrefix;
        this.year = year;
        this.month = month;
    }
    
    public void process() throws Exception
    {
        MonthCollectors mcs = new MonthCollectors(year, month);
        
        for (String fn : pageFileNames)
        {
            String pageFileFullPath = pagesMonthDir + File.separator + fn;
            String rid = Util.stripTail(fn, ".html");
            int rid_numeric = Integer.parseInt(rid);
            
            PageParserDirectBase parser = new PageParserDirectBasePassive();
            parser.rurl = Util.extractFileName(pageFileFullPath);
            
            parser.pageSource = Util.readFileAsString(pageFileFullPath);
            parser.parseHtml(parser.pageSource);

            switch (parser.detectPageStyle())
            {
            case "classic":
                parser = new PageParserDirectClassic(parser);
                break;

            case "new-style":
                parser = new PageParserDirectNewStyle(parser);
                break;
            }
            
            mcs.addPage(parser, rid_numeric);
        }
        
        mcs.complete(monthlyFilePrefix);
    }
}
