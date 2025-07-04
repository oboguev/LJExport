package my.LJExport.ljsearch;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.ConsoleProgress;
import my.LJExport.runtime.Util;
import my.LJExport.xml.JSOUP;

/*
 * Склеить отдельные файлы комментариев LJSearch (ljsear.ch) в ленты
 */
public class MainCombineCommentsAsTape
{
    private static final String CommentsRootDir = "oboguev.comments";
    // private static final String CommentsRootDir = "krylov.comments";
    // private static final String CommentsRootDir = "udod99.comments";
    // private static final String CommentsRootDir = "d_olshansky.comments";

    public static void main(String[] args)
    {
        try
        {
            do_main();
        }
        catch (Exception ex)
        {
            Main.err("Error processing comment files for " + CommentsRootDir);
            Main.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }

        Main.playCompletionSound();
    }

    private static void do_main() throws Exception
    {
        if (Config.False)
        {
            do_main(CommentsRootDir);
        }
        else
        {
            do_main("oboguev.comments");
            do_main("krylov.comments");
            do_main("udod99.comments");
            do_main("d_olshansky.comments");
            Util.noop();
        }
    }

    private static void do_main(String rootDir) throws Exception
    {
        MainCombineCommentsAsTape self = new MainCombineCommentsAsTape(Config.DownloadRoot + File.separator + rootDir);
        self.do_rootDir();
    }

    private final String rootDir;

    private MainCombineCommentsAsTape(String rootDir)
    {
        this.rootDir = rootDir;
    }

    private void do_rootDir() throws Exception
    {
        Main.out(">>> Processing comment files for " + rootDir);

        List<CommentFileInfo> list = CommentFileEnumerator.enumCommentFiles(rootDir);
        scanTimestamps(list);

        for (String user : CommentFileInfo.getUsers(list))
        {
            List<CommentFileInfo> xlist = CommentFileInfo.selectByUser(list, user);
            xlist = CommentFileInfo.sortByTimestamp(xlist);
            combineComments(String.format("@all/%s.html", user), xlist, "user " + user);
        }

        list = CommentFileInfo.sortByTimestamp(list);
        combineComments("@all/@all.html", list, "all users");

        Main.out(">>> Completed processing comment files for " + rootDir);
    }

    private void scanTimestamps(List<CommentFileInfo> list) throws Exception
    {
        ConsoleProgress cp = new ConsoleProgress("Scanning comment files for timestamps");

        try
        {
            int count = 0;
            int total = list.size();

            cp.begin();
            cp.update("Scanned 0 out of " + total, 0);

            for (CommentFileInfo fi : list)
            {
                try
                {
                    PageParserDirectBase parser = new PageParserDirectBasePassive();
                    parser.pageSource = Util.readFileAsString(fi.fullPath);
                    parser.parseHtml(parser.pageSource);
                    fi.timestamp = extractTimestamp(parser.pageRoot);

                    count++;
                    double pct = (100.0 * count) / total;
                    cp.update(String.format("Scanned %d out of %d", count, total), pct);
                }
                catch (Exception ex)
                {
                    throw new Exception("While loading " + fi.fullPath, ex);
                }
            }
        }
        finally
        {
            cp.end();
        }
    }

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\b");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Instant extractTimestamp(Node pageRoot) throws Exception
    {
        Node td = JSOUP.exactlyOne(JSOUP.findElements(pageRoot, "td", "bgcolor", "#f9f6e6"));
        String barText = JSOUP.asElement(td).text();

        Matcher matcher = TIMESTAMP_PATTERN.matcher(barText);
        if (!matcher.find())
            throw new Exception("Unable to extract timestamp");
        String timestampStr = matcher.group(1);
        LocalDateTime ldt = LocalDateTime.parse(timestampStr, FORMATTER);
        return ldt.toInstant(ZoneOffset.UTC);
    }

    private void combineComments(String toFilePath, List<CommentFileInfo> list, String who) throws Exception
    {
        Node combinedPageRoot = null;
        ConsoleProgress cp = null;

        try
        {
            int count = 0;
            int total = list.size();

            if (total >= 2000)
            {
                cp = new ConsoleProgress("Combining comment files " + who);
                cp.begin();
                cp.update("Combined 0 out of " + total, 0);
            }

            for (CommentFileInfo fi : list)
            {
                try
                {
                    PageParserDirectBase parser = new PageParserDirectBasePassive();
                    parser.pageSource = Util.readFileAsString(fi.fullPath);
                    parser.parseHtml(parser.pageSource);
                    beautify(parser.pageRoot);

                    if (combinedPageRoot == null)
                    {
                        combinedPageRoot = parser.pageRoot;
                    }
                    else
                    {
                        combineComments(combinedPageRoot, parser.pageRoot);
                    }

                    count++;
                    if (cp != null)
                    {
                        double pct = (100.0 * count) / total;
                        cp.update(String.format("Combined %d out of %d", count, total), pct);
                    }
                }
                catch (Exception ex)
                {
                    throw new Exception("While loading " + fi.fullPath, ex);
                }
            }
        }
        finally
        {
            if (cp != null)
                cp.end();
        }

        String outPath = this.rootDir + File.separator + toFilePath.replace("/", File.separator);
        File fp = new File(outPath).getCanonicalFile();
        if (!fp.getParentFile().exists())
            fp.getParentFile().mkdirs();
        String html = JSOUP.emitHtml(combinedPageRoot);
        Util.writeToFileSafe(fp.getCanonicalPath(), html);
        Util.noop();
    }

    private void beautify(Node toPageRoot) throws Exception
    {
        Element toBody = JSOUP.asDocument(toPageRoot).body();

        // Get the list of direct children of <body>
        List<Node> children = toBody.childNodes();

        // Look for the first <table> element and insert <br> after it
        for (int i = 0; i < children.size(); i++)
        {
            Node node = children.get(i);
            if (node instanceof Element && ((Element) node).tagName().equals("table"))
            {
                // Create a <br> element 
                Element br = new Element(Tag.valueOf("br"), "");

                // Insert <br> after the <table>
                toBody.insertChildren(i + 1, Collections.singletonList(br));

                break;
            }
        }
    }

    private void combineComments(Node toPageRoot, Node fromPageRoot) throws Exception
    {
        Element toBody = JSOUP.asDocument(toPageRoot).body();
        Element fromBody = JSOUP.asDocument(fromPageRoot).body();

        if (fromBody != null && toBody != null)
        {
            toBody.appendElement("br");
            toBody.appendElement("hr");
            toBody.appendElement("br");

            for (Node node : fromBody.childNodes())
            {
                // Import node into the context of toBody
                toBody.appendChild(node.clone());
            }
        }
        else
        {
            throw new Exception("Missing HTML BODY");
        }
    }
}