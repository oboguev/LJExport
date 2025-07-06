package my.LJExport.monthly;

import java.io.File;
import java.util.List;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.readers.direct.PageParserDirectClassic;
import my.LJExport.readers.direct.PageParserDirectNewStyle;
import my.LJExport.readers.direct.PageParserDirectRossiaOrg;
import my.LJExport.runtime.Util;

public class MonthProcessor
{
    private final String pagesMonthDir;
    private final List<String> pageFileNames;
    private final String monthlyFilePrefix;
    private final String year;
    private final String month;
    private final String whichDir;
    private final boolean ljsearch;

    public MonthProcessor(String pagesMonthDir, List<String> pageFileNames, String monthlyFilePrefix, String year, String month, String whichDir, boolean ljsearch)
    {
        this.pagesMonthDir = pagesMonthDir;
        this.pageFileNames = pageFileNames;
        this.monthlyFilePrefix = monthlyFilePrefix;
        this.year = year;
        this.month = month;
        this.whichDir = whichDir;
        this.ljsearch = ljsearch;
    }
    
    public void process() throws Exception
    {
        MonthCollectors mcs = new MonthCollectors(year, month, ljsearch);
        
        for (String fn : pageFileNames)
        {
            String pageFileFullPath = pagesMonthDir + File.separator + fn;
            
            try
            {
                if (Config.False && pageFileFullPath.equals("F:\\WINAPPS\\LJExport\\journals\\harmfulgrumpy\\pages\\2016\\01\\288850.html"))
                {
                    Util.noop();
                }
                
                // Main.out("    " + pageFileFullPath);
                
                String rid = Util.stripTail(fn, ".html");
                int rid_numeric = Integer.parseInt(rid);
                
                PageParserDirectBase parser = new PageParserDirectBasePassive();
                parser.rurl = Util.extractFileName(pageFileFullPath);

                parser.pageSource = Util.readFileAsString(pageFileFullPath);
                parser.parseHtml(parser.pageSource);
                
                if (ljsearch)
                {
                    // do nothing
                }
                else
                {
                    switch (parser.detectPageStyle())
                    {
                    case "classic":
                        parser = new PageParserDirectClassic(parser);
                        parser.deleteDivThreeposts();
                        break;

                    case "new-style":
                        parser = new PageParserDirectNewStyle(parser);
                        parser.deleteDivThreeposts();
                        break;
                        
                    case "rossia.org":
                        parser = new PageParserDirectRossiaOrg(parser);                        
                        break;
                        
                        // ###
                    }
                }

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
