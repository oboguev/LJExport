package my.LJExport.monthly;

import java.io.File;
import java.util.List;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
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
    private final String whichDir;

    public MonthProcessor(String pagesMonthDir, List<String> pageFileNames, String monthlyFilePrefix, String year, String month, String whichDir)
    {
        this.pagesMonthDir = pagesMonthDir;
        this.pageFileNames = pageFileNames;
        this.monthlyFilePrefix = monthlyFilePrefix;
        this.year = year;
        this.month = month;
        this.whichDir = whichDir;
    }
    
    public void process() throws Exception
    {
        MonthCollectors mcs = new MonthCollectors(year, month);
        
        for (String fn : pageFileNames)
        {
            String pageFileFullPath = pagesMonthDir + File.separator + fn;
            
            try
            {
                if (Config.True && pageFileFullPath.equals("F:\\WINAPPS\\LJExport\\journals\\harmfulgrumpy\\pages\\2016\\01\\288850.html"))
                {
                    Main.out("HIT!");
                    Util.noop();
                }
                
                // Main.out("    " + pageFileFullPath);
                
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
                
                parser.deleteDivThreeposts();
                mcs.addPage(parser, rid_numeric, whichDir);
            }
            catch (Exception ex)
            {
                throw new Exception("Error processing file " + pageFileFullPath, ex);
            }
        }
        
        mcs.complete(monthlyFilePrefix);
    }
}
