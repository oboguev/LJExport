package my.LJExport.monthly;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jsoup.nodes.Node;
import org.jsoup.nodes.Element;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.runtime.Util;
import my.LJExport.xml.JSOUP;

/*
 * Collects posts within a month with various styles.
 * Different page styles go to different MonthCollector collectors. 
 */
public class MonthCollectors
{
    private List<MonthCollector> collectors = new ArrayList<>();
    private final String year;
    private final String month;

    public MonthCollectors(String year, String month)
    {
        this.year = year;
        this.month = month;
    }

    public void addPage(PageParserDirectBase parser, int rid) throws Exception
    {
        String cleanedHead = parser.extractCleanedHead();

        parser.removeNonArticleBodyContent();

        MonthCollector mc = forCleanedHead(cleanedHead);
        if (mc != null)
        {
            Element mcbody = mc.parser.getBodyTag();
            Element body = parser.getBodyTag();

            // add divider to mc.parser inner body
            // add link to mc.parser inner body
            // ####
            String htmlToAppend = "<br><hr><br>" +
                    "<b>Полная запись:&nbsp;" +
                    "<a href=\"zzz\">1234.html</a>" +
                    "</b><br>";

            // Parse the fragment into a list of nodes
            List<Node> newNodes = JSOUP.parseBodyFragment(htmlToAppend);
            for (Node node : newNodes)
                body.appendChild(node);

            // add parser inner body to mc.parser inner body  
            // append each child of the cloned <body> to the mcbody
            // use clone to ensure full independence
            Element bodyClone = (Element) body.clone();
            for (Node child : bodyClone.childNodes())
                mcbody.appendChild(child.clone());
        }
        else
        {
            mc = new MonthCollector();
            mc.cleanedHead = cleanedHead;
            mc.first_rid = rid;
            parser.cleanHead(String.format("%s %s-%s", Config.User, year, month));
            mc.parser = parser;
            collectors.add(mc);
        }
    }

    public void complete(String monthlyFilePrefix) throws Exception
    {
        collectors.sort(Comparator.comparingInt(mc -> mc.first_rid));
        int segment = 1;
        for (MonthCollector mc : collectors)
        {
            String monthlyFilePath = monthlyFilePrefix;
            if (collectors.size() != 1)
            {
                monthlyFilePath += "-" + segment;
                segment++;
            }
            monthlyFilePath += ".html";
            
            File fp = new File(monthlyFilePath).getCanonicalFile();
            fp = fp.getParentFile();
            if (!fp.exists())
                fp.mkdirs();
            
            String monthlyPageSource = JSOUP.emitHtml(mc.parser.pageRoot);
            Util.writeToFileSafe(monthlyFilePath, monthlyPageSource);
            
            // ### write out
            // ### maintain same levels
            Util.noop();
        }
    }

    private MonthCollector forCleanedHead(String cleanedHead)
    {
        for (MonthCollector mc : collectors)
        {
            if (mc.cleanedHead.equals(cleanedHead))
                return mc;
        }

        return null;
    }
}
