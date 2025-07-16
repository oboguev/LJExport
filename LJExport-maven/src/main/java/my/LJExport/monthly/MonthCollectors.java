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
import my.LJExport.readers.direct.PageParserDirectDreamwidthOrg;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
// import my.LJExport.styles.StyleProcessor;

/*
 * Collects posts within a month with various styles.
 * Different page styles go to different MonthCollector collectors. 
 */
public class MonthCollectors
{
    private List<MonthCollector> collectors = new ArrayList<>();
    private final String year;
    private final String month;
    private final boolean ljsearch;

    public MonthCollectors(String year, String month, boolean ljsearch)
    {
        this.year = year;
        this.month = month;
        this.ljsearch = ljsearch;
    }

    public void addPage(PageParserDirectBase parser, int rid, String whichDir) throws Exception
    {
        if (ljsearch)
        {
            addPage_ljsearch(parser, rid, whichDir);
            return;
        }

        final String local_href = String.format("../../%s/%s/%s/%s.html", whichDir, year, month, rid);
        final String visible_href = rid + ".html";
        final String hr = hr(parser);

        String cleanedHead = parser.extractCleanedHead();

        parser.removeNonArticleBodyContent();
        parser.unsizeArticleHeight();

        MonthCollector mc = forCleanedHead(cleanedHead);
        if (mc == null)
        {
            /*
             * First record for the monthly page
             */
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

            if (parser instanceof PageParserDirectDreamwidthOrg)
            {
                Element contentBody = ((PageParserDirectDreamwidthOrg) parser).findContentWrapper();
                contentBody.insertChildren(0, newNodes);
            }
            else
            {
                Element article = parser.findMainArticle();

                if (Config.True)
                {
                    Element parent = (Element) article.parent();

                    // Find the index of 'article' among parent's children
                    int index = parent.childNodes().indexOf(article);

                    // Insert all nodes from newNodes before 'article'
                    parent.insertChildren(index, newNodes);
                }
                else
                {
                    Collections.reverse(newNodes);
                    for (Node node : newNodes)
                        targetBody.insertChildren(0, Arrays.asList(node.clone()));
                }
            }
        }
        else
        {
            /*
             * Subsequent records
             */
            Element sourceBody = parser.findContentWrapper();
            Element targetBody = mc.parser.findContentWrapper();

            // add divider to mc.parser inner body
            // add link to mc.parser inner body
            String htmlToAppend = "<br>{$hr}<br><br>" +
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
    }

    /* ====================================================================================================== */

    private void addPage_ljsearch(PageParserDirectBase parser, int rid, String whichDir) throws Exception
    {
        final String hr = hr(parser);

        String cleanedHead = parser.extractCleanedHeadLJSearch();
        MonthCollector mc = forCleanedHead(cleanedHead);
        if (mc == null)
        {
            /*
             * First record for the monthly page
             */
            mc = new MonthCollector();
            mc.cleanedHead = cleanedHead;
            mc.first_rid = rid;
            parser.cleanHeadLJSearch(String.format("%s %s-%s", Config.User, year, month));
            mc.parser = parser;
            collectors.add(mc);
        }
        else
        {
            /*
             * Subsequent records
             */
            Element sourceBody = parser.getBodyTag();
            Element targetBody = mc.parser.getBodyTag();

            // add divider to mc.parser inner body
            // add link to mc.parser inner body
            String htmlToAppend = "<br>{$hr}<br><br>";
            htmlToAppend = htmlToAppend.replace("{$hr}", hr);

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
    }

    /* ====================================================================================================== */

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

            if (!ljsearch)
            {
                mc.parser.remapLocalRelativeLinks(
                        LinkDownloader.LINK_REFERENCE_PREFIX_PAGES,
                        LinkDownloader.LINK_REFERENCE_PREFIX_MONTHLY_PAGES);
                
                // ### StyleProcessor.relocaleLocalHtmlStyleReferences(mc.parser.pageRoot, -1);
                
            }
            String monthlyPageSource = JSOUP.emitHtml(mc.parser.pageRoot);

            Util.writeToFileSafe(monthlyFilePath, monthlyPageSource);
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

    private String hr(PageParserDirectBase parser)
    {
        final String hr = "<hr style=\"height: 7px;border: 1;box-shadow: inset 0 9px 9px -3px\r\n"
                + "      rgba(11, 99, 184, 0.8);-webkit-border-radius:\r\n"
                + "      5px;-moz-border-radius: 5px;-ms-border-radius:\r\n"
                + "      5px;-o-border-radius: 5px;border-radius: 5px;\">";

        @SuppressWarnings("unused")
        final String hr_dreamwidth = "<hr style=\"height: 7px; border: none; background-color: rgba(11, 99, 184, 0.4);\r\n"
                + "box-shadow: inset 0 9px 9px -3px rgba(11, 99, 184, 0.8);\r\n"
                + "border-radius: 5px;\">";

        final String div_dreamwidth = "<div style=\"height: 10px; background-color: rgba(11, 99, 184, 0.4);"
                + "margin: 2em 0; border-radius: 5px;"
                + "box-shadow: inset 0 8px 8px -5px rgba(11, 99, 184, 0.6);\">"
                + "</div>";

        if (parser instanceof PageParserDirectDreamwidthOrg)
        {
            // return "<div style=\"border: 1px solid red; padding: 10px;\">" + hr_dreamwidth + "</div>";
            // return hr_dreamwidth;
            return div_dreamwidth;
        }

        return hr;
    }
}
