package my.LJExport.runtime.parallel.twostage.parser;

import java.io.IOException;
import java.util.List;

import org.jsoup.nodes.Node;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.parallel.twostage.WorkContext;

public class ParserWorkContext extends WorkContext<String>
{
    public PageParserDirectBasePassive parser;
    public List<Node> pageFlat;

    public final String relativeFilePath;
    public String fullFilePath;

    public ParserWorkContext(String relativeFilePath)
    {
        super(relativeFilePath);
        this.relativeFilePath = relativeFilePath;
    }

    @Override
    public void close() throws IOException
    {
        // help GC
        parser = null;
        pageFlat = null;
    }
}
