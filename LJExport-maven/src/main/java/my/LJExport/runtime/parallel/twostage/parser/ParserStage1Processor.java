package my.LJExport.runtime.parallel.twostage.parser;

import java.io.File;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.parallel.twostage.Stage1Processor;

public class ParserStage1Processor implements Stage1Processor<ParserWorkContext>
{
    private final String rootDir;
    
    public ParserStage1Processor(String rootDir)
    {
        this.rootDir = rootDir;
    }
    
    @Override
    public void process(ParserWorkContext ctx) throws Exception
    {
        String threadName = Thread.currentThread().getName();
        try
        {
            String fn = ctx.getWorkItem();
            File fp = new File(rootDir).getCanonicalFile();
            fp = new File(fp, fn).getCanonicalFile();
            String path = fp.getCanonicalPath();
            ctx.fullFilePath = path;
            
            Thread.currentThread().setName("parsing " + fp.getName());
            
            PageParserDirectBasePassive parser = new PageParserDirectBasePassive();
            parser.rid = parser.rurl = null;
            parser.pageSource = Util.readFileAsString(path);
            parser.parseHtml(parser.pageSource);
            
            ctx.parser = parser;
            ctx.pageFlat = JSOUP.flatten(parser.pageRoot);
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }
    }
}
