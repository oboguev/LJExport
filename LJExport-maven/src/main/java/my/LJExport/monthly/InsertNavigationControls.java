package my.LJExport.monthly;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InsertNavigationControls
{
    private final File rootDir;
    private final String dividerHtml;

    private static final Pattern FILE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})(?:-(\\d+))?\\.html$");

    private static final String nl = "\n";
    public static final String DIVIDER = "<div style=\"height: 7px;border: 1;box-shadow: inset 0 9px 9px -3px" + nl +
            "      rgba(11, 99, 184, 0.8);-webkit-border-radius:" + nl +
            "      5px;-moz-border-radius: 5px;-ms-border-radius:" + nl +
            "      5px;-o-border-radius: 5px;border-radius: 5px;\"></div>";

    public InsertNavigationControls(String monthlyRootDir, String dividerHtml)
    {
        this.rootDir = new File(monthlyRootDir);
        this.dividerHtml = dividerHtml + "<br>";
    }

    public void insertContols() throws Exception
    {
        List<MonthEntry> allEntries = new ArrayList<>();
        collectMonthFiles(rootDir, allEntries);

        if (allEntries.size() <= 1)
            return;

        allEntries.sort(Comparator.naturalOrder());

        for (int i = 0; i < allEntries.size(); i++)
        {
            MonthEntry current = allEntries.get(i);
            MonthEntry prev = (i > 0) ? allEntries.get(i - 1) : null;
            MonthEntry next = (i < allEntries.size() - 1) ? allEntries.get(i + 1) : null;
            insertNavigation(current, prev, next);
        }
    }

    private void collectMonthFiles(File dir, List<MonthEntry> entries)
    {
        if (!dir.isDirectory())
            return;

        File[] files = dir.listFiles();
        if (files == null)
            return;

        for (File f : files)
        {
            if (f.isDirectory())
            {
                collectMonthFiles(f, entries);
            }
            else
            {
                Matcher m = FILE_PATTERN.matcher(f.getName());
                if (!m.matches())
                    continue;

                String year = m.group(1);
                String month = m.group(2);
                String partStr = m.group(3);
                int part = (partStr == null) ? 0 : Integer.parseInt(partStr);

                entries.add(new MonthEntry(year, month, part, f));
            }
        }
    }

    private void insertNavigation(MonthEntry current, MonthEntry prev, MonthEntry next) throws Exception
    {
        Util.out(String.format("    Inserting navigation for [%s] %s", Config.User, current.toString()));

        String html = Util.readFileAsString(current.file.getAbsolutePath());
        PageParserDirectBase parser = new PageParserDirectBasePassive();
        parser.parseHtml(html);

        Element body = parser.findBody();
        if (body == null)
            return;

        // delete nav section if it already exists
        for (Node n : JSOUP.findElements(parser.pageRoot, "div", "id", "ljexport-monthly-navigation"))
            JSOUP.removeElement(parser.pageRoot, n);

        Element navDiv = new Element(Tag.valueOf("div"), "")
                .attr("id", "ljexport-monthly-navigation")
                .attr("style", "text-align: center; margin-bottom: 2em; font-size: 140%;");

        // Вставляем <style> внутрь div
        Element style = new Element(Tag.valueOf("style"), "").text(
                "a.ljexport-partial-underline {\n" +
                        "  text-decoration: none;\n" +
                        "  background-image: linear-gradient(to top, black 1px, transparent 1px);\n" +
                        "  background-repeat: repeat-x;\n" +
                        "  background-position: 0 1.1em;\n" +
                        "  background-size: 1ch 1em;\n" +
                        "  white-space: pre;\n" +
                        "  color: #228B22;\n" +
                        "}\n" +
                        "a.ljexport-partial-underline:visited {\n" +
                        "  color: #003300;\n" +
                        "}");
        navDiv.appendChild(style);

        // Визуальный разделитель
        navDiv.append(dividerHtml);

        boolean needDivider = false;

        if (prev != null)
        {
            navDiv.appendChild(new Element(Tag.valueOf("a"), "")
                    .addClass("ljexport-partial-underline")
                    .attr("href", computeRelativeHref(current.file, prev.file))
                    .text("◄ раньше к " + prev.displayLabel()));
            needDivider = true;
        }

        if (needDivider || next != null)
        {
            navDiv.append("&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp;");
        }

        navDiv.appendChild(new Element(Tag.valueOf("a"), "")
                .addClass("ljexport-partial-underline")
                .attr("href", "index.html")
                .text("оглавление " + current.year));

        if (next != null)
        {
            navDiv.append("&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp;");
            navDiv.appendChild(new Element(Tag.valueOf("a"), "")
                    .addClass("ljexport-partial-underline")
                    .attr("href", computeRelativeHref(current.file, next.file))
                    .text("дальше к " + next.displayLabel() + " ►"));
        }

        body.appendChild(navDiv);

        String updatedHtml = JSOUP.emitHtml(parser.pageRoot);
        Util.writeToFileSafe(current.file.getAbsolutePath(), updatedHtml);
        Util.noop();
    }

    private static String computeRelativeHref(File from, File to)
    {
        try
        {
            Path fromPath = from.getParentFile().toPath().toAbsolutePath().normalize();
            Path toPath = to.toPath().toAbsolutePath().normalize();
            Path relPath = fromPath.relativize(toPath);
            return relPath.toString().replace(File.separatorChar, '/');
        }
        catch (Exception e)
        {
            // fallback: just file name (may fail, but avoids crash)
            return to.getName();
        }
    }

    private static class MonthEntry implements Comparable<MonthEntry>
    {
        final String year;
        final String month;
        final int part;
        final File file;

        MonthEntry(String year, String month, int part, File file)
        {
            this.year = year;
            this.month = month;
            this.part = part;
            this.file = file;
        }

        String displayLabel()
        {
            if (part == 0)
            {
                return year + " " + month;
            }
            else
            {
                return year + " " + month + " части " + part;
            }
        }

        @Override
        public String toString()
        {
            if (part == 0)
            {
                return year + "-" + month;
            }
            else
            {
                return year + "-" + month + "-" + part;
            }
        }

        @Override
        public int compareTo(MonthEntry o)
        {
            int cmp = this.year.compareTo(o.year);
            if (cmp != 0)
                return cmp;
            cmp = this.month.compareTo(o.month);
            if (cmp != 0)
                return cmp;
            return Integer.compare(this.part, o.part);
        }
    }
}
