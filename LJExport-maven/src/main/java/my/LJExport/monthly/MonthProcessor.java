package my.LJExport.monthly;

import java.util.List;

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
        Util.noop();
    }
}
