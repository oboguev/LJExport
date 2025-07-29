package my.LJExport.monthly;

import java.io.File;
import java.util.List;
import java.util.Objects;

import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.readers.direct.PageParserDirectClassic;
import my.LJExport.readers.direct.PageParserDirectDreamwidthOrg;
import my.LJExport.readers.direct.PageParserDirectNewStyle;
import my.LJExport.readers.direct.PageParserDirectRossiaOrg;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.lj.Sites;
import my.LJExport.runtime.parallel.twostage.parser.ParserParallelWorkContext;
import my.LJExport.runtime.parallel.twostage.parser.ParserWorkContext;

public class MonthProcessor
{
    private final String pagesMonthDir;
    private final List<String> pageFileNames;
    private final String monthlyFilePrefix;
    private final String year;
    private final String month;
    private final String whichDir;
    private final boolean ljsearch;
    private final int parallelism = 10;

    public MonthProcessor(String pagesMonthDir, List<String> pageFileNames, String monthlyFilePrefix, String year, String month,
            String whichDir, boolean ljsearch)
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

        if (Util.False)
        {
            for (String fn : pageFileNames)
            {
                String pageFileFullPath = pagesMonthDir + File.separator + fn;

                try
                {
                    PageParserDirectBase parser = new PageParserDirectBasePassive();
                    parser.rurl = Util.extractFileName(pageFileFullPath);
                    parser.pageSource = Util.readFileAsString(pageFileFullPath);
                    parser.parseHtml(parser.pageSource);

                    processOneFile(mcs, pageFileFullPath, fn, parser);
                }
                catch (Exception ex)
                {
                    throw new Exception("Error processing file " + pageFileFullPath, ex);
                }
            }
        }
        else
        {
            ParserParallelWorkContext ppwc = new ParserParallelWorkContext(pageFileNames, pagesMonthDir, parallelism);

            try
            {
                for (ParserWorkContext wcx : ppwc)
                {
                    // Util.out("Processing " + wcx.fullFilePath);

                    Exception ex = wcx.getException();
                    if (ex != null)
                        throw new Exception("While processing " + wcx.fullFilePath, ex);

                    Objects.requireNonNull(wcx.parser, "parser is null");
                    processOneFile(mcs, wcx.fullFilePath, wcx.relativeFilePath, wcx.parser);
                }
            }
            finally
            {
                ppwc.close();
            }
        }

        mcs.complete(monthlyFilePrefix);
    }

    private void processOneFile(MonthCollectors mcs, String pageFileFullPath, String fn, PageParserDirectBase parser)
            throws Exception
    {
        try
        {
            if (Util.False
                    && pageFileFullPath.equals("F:\\WINAPPS\\LJExport\\journals\\harmfulgrumpy\\pages\\2016\\01\\288850.html"))
            {
                Util.noop();
            }

            // Main.out("    " + pageFileFullPath);

            String rid = Util.stripTail(fn, ".html");
            int rid_numeric = Integer.parseInt(rid);

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

                case Sites.RossiaOrg:
                    parser = new PageParserDirectRossiaOrg(parser);
                    break;

                case Sites.DreamwidthOrg:
                    parser = new PageParserDirectDreamwidthOrg(parser);
                    break;
                }
            }

            mcs.addPage(parser, rid_numeric, whichDir);
        }
        catch (Exception ex)
        {
            throw new Exception("Error processing file " + pageFileFullPath, ex);
        }
    }
}