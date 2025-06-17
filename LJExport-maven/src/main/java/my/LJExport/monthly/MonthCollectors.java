package my.LJExport.monthly;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        final String local_href = String.format("../../pages/%s/%s/%s.html", year, month, rid);
        final String visible_href = rid + ".html";
        final String hr = "<hr style=\"height: 7px;border: 1;box-shadow: inset 0 9px 9px -3px\r\n"
                + "      rgba(11, 99, 184, 0.8);-webkit-border-radius:\r\n"
                + "      5px;-moz-border-radius: 5px;-ms-border-radius:\r\n"
                + "      5px;-o-border-radius: 5px;border-radius: 5px;\">";

        String cleanedHead = parser.extractCleanedHead();

        parser.removeNonArticleBodyContent();

        MonthCollector mc = forCleanedHead(cleanedHead);
        if (mc != null)
        {
            Element sourceBody = parser.getBodyTag();
            Element targetBody = mc.parser.getBodyTag();

            // add divider to mc.parser inner body
            // add link to mc.parser inner body
            String htmlToAppend = "<br><br>{$hr}<br><br>" +
                    "<b>{$title}&nbsp;" +
                    "<a href=\"{$local_href}\">{$visible_href}</a>" +
                    "</b><br><br>";

            htmlToAppend = htmlToAppend
                    .replace("{$title}", "Полная запись:")
                    .replace("{$hr}", hr)
                    .replace("{$local_href}", local_href)
                    .replace("{$visible_href}", visible_href);

            // Parse the fragment into a list of nodes and append to targetBody
            List<Node> newNodes = JSOUP.parseBodyFragment(htmlToAppend);
            for (Node node : newNodes)
                targetBody.appendChild(node.clone());

            // add parser inner body to mc.parser inner body  
            // append each child of the cloned <body> to the targetBody
            // use clone to ensure full independence
            Element sourceBodyClone = (Element) sourceBody.clone();
            for (Node child : sourceBodyClone.childNodes())
                targetBody.appendChild(child.clone());
        }
        else
        {
            mc = new MonthCollector();
            mc.cleanedHead = cleanedHead;
            mc.first_rid = rid;
            parser.cleanHead(String.format("%s %s-%s", Config.User, year, month));
            mc.parser = parser;
            collectors.add(mc);

            Element targetBody = mc.parser.getBodyTag();

            String htmlToPrepend = "<b>{$title}&nbsp;" +
                    "<a href=\"{$local_href}\">{$visible_href}</a>" +
                    "</b><br><br>";

            htmlToPrepend = htmlToPrepend
                    .replace("{$title}", "Полная запись:")
                    .replace("{$hr}", hr)
                    .replace("{$local_href}", local_href)
                    .replace("{$visible_href}", visible_href);

            // Parse the fragment into a list of nodes and prepend before targetBody
            List<Node> newNodes = JSOUP.parseBodyFragment(htmlToPrepend);
            Collections.reverse(newNodes);
            for (Node node : newNodes)
                targetBody.insertChildren(0, Arrays.asList(node.clone()));
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

            mc.parser.remapLocalRelativeLinks("../../../links/", "../../links/");
            String monthlyPageSource = JSOUP.emitHtml(mc.parser.pageRoot);
            Util.writeToFileSafe(monthlyFilePath, monthlyPageSource);

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
