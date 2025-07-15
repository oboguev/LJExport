package my.KrylovCC;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.MemoryMonitor;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.YYYY_MM_DD;
import my.LJExport.runtime.html.JSOUP;

public class MainProcessFB
{
    public static void main(String[] args)
    {
        new MainProcessFB().do_main();
    }

    private void do_main()
    {
        try
        {
            LimitProcessorUsage.limit();
            MemoryMonitor.startMonitor();
            Util.out(">>> Time: " + Util.timeNow());

            Config.init("");
            Config.User = "krylov.cc";
            Config.mangleUser();

            for (int start = 0; start <= 7200; start += 100)
                loadPage("fb", start);
            Collections.reverse(fbs);
            makeMonthlyList();
            writeFiles(Config.DownloadRoot + File.separator + Config.User + File.separator + "fb");

            Util.out("");
            Util.out("Completed.");
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private List<FB> fbs = new ArrayList<>();
    private List<MonthContent> monthlyContent = new ArrayList<>();

    private void loadPage(String which, int start) throws Exception
    {
        Path xpath = MainDownloadPages.rawPagePath(which, start);
        String path = xpath.toAbsolutePath().toString();

        File fp = new File(path).getCanonicalFile();
        PageParserDirectBase parser = new PageParserDirectBasePassive();
        parser.rurl = Util.extractFileName(fp.getCanonicalPath());
        Util.out("Loading " + parser.rurl);
        parser.pageSource = Util.readFileAsString(fp.getCanonicalPath());
        parser.parseHtml(parser.pageSource);

        Node lft = JSOUP.exactlyOne(JSOUP.findElements(parser.pageRoot, "div", "id", "lft"));

        for (Node n : JSOUP.directChildren(lft))
        {
            if (n instanceof Element && n.nodeName().equalsIgnoreCase("div"))
            {
                Element el = JSOUP.asElement(n);
                String id = JSOUP.getAttribute(el, "id");

                if (id != null && id.startsWith("fb_"))
                {
                    Node dateElement = JSOUP.exactlyOne(JSOUP.findElementsWithClass(el, "h6", "bot2"));
                    String dateText = Util.despace(JSOUP.asElement(dateElement).text());
                    YYYY_MM_DD ymd = parseDate(dateText);
                    fbs.add(new FB(el, ymd));
                }
            }
        }
    }

    public static class FB
    {
        public Element fb;
        public YYYY_MM_DD ymd;

        public FB(Element fb, YYYY_MM_DD ymd)
        {
            this.fb = fb;
            this.ymd = ymd;
        }
    }

    // Accepts “08 October 2018, 14:20 UTC” (or any other zone such as “GMT” or “+00:00”)
    private static final DateTimeFormatter DATE_TIME_FMT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive() // “may” or “May” both OK
            .appendPattern("d MMMM uuuu, HH:mm ")
            .appendZoneOrOffsetId() // <— captures the zone (UTC)
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Parse strings like “12 May 2020, 09:32 UTC” and return the date part.
     *
     * @param s non-null string in the expected format
     * @return YYYY_MM_DD value object
     * @throws IllegalArgumentException if the text is null or mismatched
     */
    public static YYYY_MM_DD parseDate(String s)
    {
        Objects.requireNonNull(s, "Input must not be null");

        try
        {
            ZonedDateTime zdt = ZonedDateTime.parse(s.trim(), DATE_TIME_FMT);
            LocalDate d = zdt.toLocalDate();
            return new YYYY_MM_DD(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException(
                    "Date must match \"dd MMMM yyyy, HH:mm <zone>\" — received: \"" + s + "\"",
                    ex);
        }
    }

    public static class MonthContent
    {
        public YYYY_MM_DD ymd;
        public List<FB> fbs = new ArrayList<>();
    }

    private void makeMonthlyList() throws Exception
    {
        MonthContent last = null;

        for (FB fb : fbs)
        {
            if (last != null && Objects.equals(day1(fb.ymd), day1(last.ymd)))
            {
                last.fbs.add(fb);
            }
            else
            {
                last = new MonthContent();
                last.ymd = fb.ymd;
                last.fbs.add(fb);
                monthlyContent.add(last);
            }
        }
    }

    private YYYY_MM_DD day1(YYYY_MM_DD ymd)
    {
        if (ymd == null)
            return null;
        else

            return new YYYY_MM_DD(ymd.yyyy, ymd.mm, 1);
    }

    public void writeFiles(String rootDir) throws Exception
    {
        copyout("krylov.cc/fb", rootDir + File.separator, "index.html");
        copyout("krylov.cc/fb", rootDir + File.separator + "images", "doubleruler.gif");
        copyout("krylov.cc/fb", rootDir + File.separator + "images", "fb.css");
        copyout("krylov.cc/fb", rootDir + File.separator + "images", "favicon.ico");
        copyout("krylov.cc/fb", rootDir + File.separator + "images", "kk.jpg");

        for (MonthContent mc : monthlyContent)
            writeMonthlyFile(rootDir, mc);
    }

    private void copyout(String resourceDir, String fileDir, String fn) throws Exception
    {
        File fp = new File(fileDir);
        if (!fp.exists())
            fp.mkdirs();

        byte[] ba = Util.loadResourceAsBytes(resourceDir + "/" + fn);
        Util.writeToFileSafe(fileDir + File.separator + fn, ba);
    }

    /**
     * Returns the element immediately preceding {@code p} in {@code monthlyContent},
     * or {@code null} if {@code p} is the first element.
     *
     * @throws IllegalArgumentException if {@code p} is not present in the list
     * @throws NullPointerException     if {@code monthlyContent} or {@code p} is {@code null}
     */
    public static MonthContent prev(List<MonthContent> monthlyContent, MonthContent p)
    {
        Objects.requireNonNull(monthlyContent, "monthlyContent must not be null");
        Objects.requireNonNull(p, "p must not be null");

        for (int i = 0, size = monthlyContent.size(); i < size; i++)
        {
            if (monthlyContent.get(i) == p)
            {
                // identity comparison
                return (i == 0) ? null : monthlyContent.get(i - 1);
            }
        }

        throw new IllegalArgumentException("Given element is not contained in the list");
    }

    /**
     * Returns the element immediately following {@code p} in {@code monthlyContent},
     * or {@code null} if {@code p} is the last element.
     *
     * @throws IllegalArgumentException if {@code p} is not present in the list
     * @throws NullPointerException     if {@code monthlyContent} or {@code p} is {@code null}
     */
    public static MonthContent next(List<MonthContent> monthlyContent, MonthContent p)
    {
        Objects.requireNonNull(monthlyContent, "monthlyContent must not be null");
        Objects.requireNonNull(p, "p must not be null");

        for (int i = 0, size = monthlyContent.size(); i < size; i++)
        {
            if (monthlyContent.get(i) == p)
            {
                // identity comparison
                return (i == size - 1) ? null : monthlyContent.get(i + 1);
            }
        }

        throw new IllegalArgumentException("Given element is not contained in the list");
    }

    public void writeMonthlyFile(String rootDir, MonthContent mc) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        append(sb, "<html>");
        append(sb, " <head>");
        append(sb, "  <title>Записи из фейсбука Константина Крылова</title>");
        append(sb, "  <link rel=\"stylesheet\" href=\"images/fb.css\">");
        append(sb, "  <link rel=\"icon\" type=\"image/x-icon\" href=\"imagesfavicon.ico\">");
        append(sb, " </head>");
        append(sb, " <body>");
        append(sb, " <div style=\"text-align: center;\">");
        append(sb, "  <br>");
        append(sb, "  <center><img src=\"images/kk.jpg\">");
        append(sb, "  <br>");
        append(sb, "  <br>");
        append(sb,
                "  <h1 style=\"text-align: center !important; margin: 0 auto !important;\">Записи из фейсбука Константина Крылова</h1></center>");
        append(sb, " </div>");
        append(sb, " </body> ");
        append(sb, "</html>");

        PageParserDirectBase parser = new PageParserDirectBasePassive();
        parser.rurl = null;
        parser.pageSource = sb.toString();
        parser.parseHtml(parser.pageSource);
        Element body = parser.findBody();

        // Parse the fragment into a list of nodes
        final String dividerHtml = "<div class=\"doublegrayruler h20px\"></div>";
        final List<Node> dividerNodes = JSOUP.parseBodyFragment(dividerHtml);

        for (FB fb : mc.fbs)
        {
            append(body, dividerNodes);

            // String dtHtml = String.format("<div class=\"dt2\">%s</div>", Util.despace(qa.dt.text()));
            // List<Node> dtNodes = JSOUP.parseBodyFragment(dtHtml);

            append(body, fb.fb);
        }

        append(body, dividerNodes);

        MonthContent mc_prev = prev(monthlyContent, mc);
        MonthContent mc_next = next(monthlyContent, mc);

        if (mc_prev != null)
        {
            String navHtml = String.format("<a class=\"partial-underline\" href=\"%s\">&lt;&lt;&lt; раньше</a>", filename(mc_prev));
            List<Node> nodes = JSOUP.parseBodyFragment(navHtml);
            append(body, nodes);
        }

        if (mc_next != null)
        {
            String navHtml = String.format("%s<a class=\"partial-underline\" href=\"%s\">дальше &gt;&gt;&gt;</a>",
                    mc_prev != null ? "&nbsp;&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;&nbsp;" : "",
                    filename(mc_next));
            List<Node> nodes = JSOUP.parseBodyFragment(navHtml);
            append(body, nodes);
        }

        String html = JSOUP.emitHtml(parser.pageRoot);

        File fp = new File(rootDir).getCanonicalFile();
        if (!fp.exists())
            fp.mkdirs();
        fp = new File(fp, filename(mc));
        Util.writeToFileSafe(fp.getCanonicalPath(), html);
    }

    private String filename(MonthContent mc)
    {
        if (mc.ymd != null)
        {
            return String.format("%04d-%02d.html", mc.ymd.yyyy, mc.ymd.mm);
        }
        else
        {
            return "$-before-2010.06.14.html";
        }
    }

    private void append(Element body, Node node)
    {
        body.appendChild(node.clone());
    }

    private void append(Element body, List<Node> nodes)
    {
        for (Node node : nodes)
            body.appendChild(node.clone());
    }

    private void append(StringBuilder sb, String text)
    {
        sb.append(text);
        sb.append("\n");
    }
}
