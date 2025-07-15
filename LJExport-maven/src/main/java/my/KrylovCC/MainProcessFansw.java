package my.KrylovCC;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

public class MainProcessFansw
{
    public static void main(String[] args)
    {
        new MainProcessFansw().do_main();
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

            for (int start = 0; start <= 18400; start += 200)
                loadPage("fansw", start);
            Collections.reverse(qas);
            makeMonthlyList();
            writeFiles(Config.DownloadRoot + File.separator + Config.User + File.separator + "fansw");

            Util.out("");
            Util.out("Completed.");
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private List<QA> qas = new ArrayList<>();
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

        Element q = null;
        Element a = null;
        Element dt = null;

        for (Node n : JSOUP.directChildren(lft))
        {
            if (n instanceof Element && n.nodeName().equalsIgnoreCase("div"))
            {
                Element el = JSOUP.asElement(n);
                String id = JSOUP.getAttribute(el, "id");

                if (id != null && id.startsWith("q_"))
                {
                    if (q != null || a != null || dt != null)
                        throw new Exception("Out of order");
                    q = el;
                }
                else if (id != null && id.startsWith("a_"))
                {
                    if (q == null || a != null || dt != null)
                        throw new Exception("Out of order");
                    a = el;
                }
                else if (id == null && JSOUP.hasClass(el, "dt"))
                {
                    if (a == null || q == null || dt != null)
                        throw new Exception("Out of order");
                    dt = el;
                }
            }

            if (q != null && a != null && dt != null)
            {
                String text = dt.text();
                if (text == null)
                    throw new Exception("Missing time");
                text = Util.despace(text);

                YYYY_MM_DD ymd = null;
                if (!text.startsWith("до "))
                    ymd = parseDate(text);

                qas.add(new QA(q, a, dt, ymd));

                q = null;
                a = null;
                dt = null;
            }
        }

        if (q != null || a != null || dt != null)
            throw new Exception("Out of order");
    }

    public static class QA
    {
        public Element q;
        public Element a;
        public Element dt;
        public YYYY_MM_DD ymd;

        public QA(Element q, Element a, Element dt, YYYY_MM_DD ymd)
        {
            this.q = q;
            this.a = a;
            this.dt = dt;
            this.ymd = ymd;
        }
    }

    public static class MonthContent
    {
        public YYYY_MM_DD ymd;
        public List<QA> qas = new ArrayList<>();
    }

    /**
     * Parses strings like
     *  - "04.10.2010"
     *  - "15.10.2010"
     *  - "16.04.2020 21:37 UTC"
     * into a {@link YYYY_MM_DD} value.
     *
     * @param s the input text; may contain leading/trailing NBSP (U+00A0) or ASCII spaces
     * @return a populated {@code YYYY_MM_DD}
     * @throws IllegalArgumentException if {@code s} is {@code null}, empty, or malformed
     */
    public static YYYY_MM_DD parseDate(String s)
    {
        Objects.requireNonNull(s, "Input string must not be null");

        // 1) Normalize whitespace: map NBSP → ' ', then trim
        String normalized = s.replace('\u00A0', ' ').trim();
        if (normalized.isEmpty())
            throw new IllegalArgumentException("Input string is empty after trimming");

        // 2) Take the first whitespace-separated token – that’s the date part
        String datePart = normalized.split("\\s+", 2)[0];

        // 3) Expect exactly "dd.MM.yyyy" (allowing one- or two-digit day/month)
        String[] chunks = datePart.split("\\.");
        if (chunks.length != 3)
            throw new IllegalArgumentException("Date must have form dd.MM.yyyy: " + datePart);

        try
        {
            int dd = Integer.parseInt(chunks[0]);
            int mm = Integer.parseInt(chunks[1]);
            int yyyy = Integer.parseInt(chunks[2]);

            // 4) Validate the date (e.g. 31.02.2024 should fail)
            LocalDate.of(yyyy, mm, dd);

            return new YYYY_MM_DD(yyyy, mm, dd);
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("Malformed date: " + datePart, ex);
        }
    }

    /* ==================================================================== */

    private void makeMonthlyList() throws Exception
    {
        MonthContent last = null;

        for (QA qa : qas)
        {
            if (last != null && Objects.equals(day1(qa.ymd), day1(last.ymd)))
            {
                last.qas.add(qa);
            }
            else
            {
                last = new MonthContent();
                last.ymd = qa.ymd;
                last.qas.add(qa);
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
        copyout("krylov.cc/fansw", rootDir + File.separator, "index.html");
        copyout("krylov.cc/fansw", rootDir + File.separator + "images", "doubleruler.gif");
        copyout("krylov.cc/fansw", rootDir + File.separator + "images", "fansw.css");
        copyout("krylov.cc/fansw", rootDir + File.separator + "images", "favicon.ico");
        copyout("krylov.cc/fansw", rootDir + File.separator + "images", "kk.jpg");

        for (MonthContent mc : monthlyContent)
            writeMonthlyFile(rootDir, mc);
    }

    public void writeMonthlyFile(String rootDir, MonthContent mc) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        append(sb, "<html>");
        append(sb, " <head>");
        append(sb, "  <title>Ответы Константина Крылова на вопросы</title>");
        append(sb, "  <link rel=\"stylesheet\" href=\"images/fansw.css\">");
        append(sb, "  <link rel=\"icon\" type=\"image/x-icon\" href=\"imagesfavicon.ico\">");
        append(sb, " </head>");
        append(sb, " <body>");
        append(sb, " <div style=\"text-align: center;\">");
        append(sb, "  <br>");
        append(sb, "  <center><img src=\"images/kk.jpg\">");
        append(sb, "  <br>");
        append(sb, "  <br>");
        append(sb,
                "  <h1 style=\"text-align: center !important; margin: 0 auto !important;\">Ответы Константина Крылова на вопросы</h1></center>");
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

        for (QA qa : mc.qas)
        {
            append(body, dividerNodes);

            String dtHtml = String.format("<div class=\"dt2\">%s</div>", Util.despace(qa.dt.text()));
            List<Node> dtNodes = JSOUP.parseBodyFragment(dtHtml);

            append(body, qa.q);
            append(body, qa.a);
            // append(body, qa.dt);
            append(body, dtNodes);
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
}
