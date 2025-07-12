package my.LJExport.readers;

import org.apache.http.HttpStatus;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.html.DOM;
import my.LJExport.runtime.LJUtil;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.Web;

public class PageReaderBasic implements PageReader
{
    private String rurl;
    private String rid;
    private Node pageRoot;
    private String fileDir;
    private Set<String> pageThreads = new HashSet<String>();
    private Set<String> processedThreads = new HashSet<String>();
    private int npages = -1;

    private final static int COUNT_PAGES = (1 << 0);
    private final static int REMOVE_MAIN_TEXT = (1 << 1);

    public PageReaderBasic(String rurl, String fileDir)
    {
        // rurl = "2532366.html"; // test: colonelcassad
        // rurl = "5182367.html"; // test: oboguev (private)
        this.rurl = rurl;
        this.rid = rurl.substring(0, rurl.indexOf('.'));
        this.fileDir = fileDir + File.separator;
    }

    public void readPage() throws Exception
    {
        // save page 1 completely, except for junk sections
        Web.Response r = loadPage(1, null);
        String rbody = removeJunkAndEmitHtml(r.body, COUNT_PAGES);
        Util.writeToFile(fileDir + rid + ".html", rbody);
        collectThreadLinks();

        // load extra pages of comments
        for (int npage = 2; npage <= npages; npage++)
        {
            r = loadPage(npage, null);
            removeJunk(r.body, REMOVE_MAIN_TEXT);
            rbody = DOM.emitHtml(pageRoot);
            Util.writeToFile(fileDir + rid + "_page_" + npage + ".html", rbody);
            collectThreadLinks();
        }

        // load threads
        String thread;
        while (null != (thread = getNextThread()))
        {
            // out(">>> " + "  " + rid + "_" + thread);
            r = loadPage(1, thread);
            removeJunk(r.body, REMOVE_MAIN_TEXT);
            rbody = DOM.emitHtml(pageRoot);
            Util.writeToFile(fileDir + rid + "_" + thread + ".html", rbody);
            collectThreadLinks();
        }

        // out(">>> done " + rurl);
    }

    private String getNextThread() throws Exception
    {
        Set<String> toRemove = new HashSet<String>();
        String result = null;

        for (String thread : pageThreads)
        {
            if (processedThreads.contains(thread))
            {
                toRemove.add(thread);
            }
            else
            {
                toRemove.add(thread);
                processedThreads.add(thread);
                result = thread;
                break;
            }
        }

        for (String thread : toRemove)
            pageThreads.remove(thread);

        return result;
    }

    private Web.Response loadPage(int npage, String thread) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append("http://" + Config.MangledUser + "." + Config.Site + "/" + rurl + "?nojs=1&format=light");
        if (npage != 1)
            sb.append("&page=" + npage);
        if (thread != null)
            sb.append("&thread=" + thread);
        Web.Response r = Web.get(sb.toString());
        if (r.code != HttpStatus.SC_OK)
            throw new Exception("Unable to read user journal record: " + Web.describe(r.code));
        return r;
    }

    private String removeJunkAndEmitHtml(String body, int flags) throws Exception
    {
        removeJunk(body, flags);
        return DOM.emitHtml(pageRoot);
    }

    private void removeJunk(String body, int flags) throws Exception
    {
        pageRoot = DOM.parseHtmlAsXml(body);

        /*
         * Record is a set of nested tables, with relevant content located under the <article> tag.
         * Traverse the tree and delete all tables (<table>, <tr> and <td> tags) that do not
         * have <article> as their eventual child.
         */
        removeJunk_1();

        /*
         * Remove known sections that contain no record-related information.
         */
        DOM.removeElements(pageRoot, null, "div", "class", "b-singlepost-standout");
        DOM.removeElements(pageRoot, null, "div", "class", "entry-unrelated");
        DOM.removeElements(pageRoot, null, "div", "class", "threeposts__inner");
        DOM.removeElements(pageRoot, null, "div", "class", "b-singlepost-prevnext");
        DOM.removeElements(pageRoot, null, "div", "class", "b-massaction");
        DOM.removeElements(pageRoot, null, "div", "class", "b-massaction-anchor");
        DOM.removeElements(pageRoot, null, "div", "class", "b-massaction-mobile");

        if (0 != (flags & COUNT_PAGES))
        {
            // find out if there are multiple pages with comments
            npages = numberOfCommentPages();
        }

        DOM.removeElements(pageRoot, null, "div", "class", "b-xylem  b-xylem-first");
        DOM.removeElements(pageRoot, null, "div", "class", "b-xylem b-xylem-first");
        DOM.removeElements(pageRoot, null, "div", "class", "b-tree b-tree-best");
        DOM.removeElements(pageRoot, null, "div", "class", "b-tree b-tree-promo");
        DOM.removeElements(pageRoot, null, "div", "class", "b-xylem");

        if (0 != (flags & REMOVE_MAIN_TEXT))
        {
            DOM.removeElements(pageRoot, null, "div", "class", "b-singlepost-about");
            DOM.removeElements(pageRoot, null, "div", "class", "b-singlepost-wrapper");
        }
    }

    private void removeJunk_1() throws Exception
    {
        // find article tags
        List<Node> articles = DOM.findElements(DOM.flatten(pageRoot), "article");

        // something wrong? leave it alone
        if (articles.size() == 0)
            return;

        // traverse upwards from articles and mark the nodes to keep
        Set<Node> keepSet = new HashSet<Node>();
        for (Node n : articles)
        {
            DOM.enumParents(keepSet, n);
            keepSet.add(n);
        }

        // traverse from root recursively downwards (like in flatten)
        // marking all <table>, <tr> and <td> not in created keep set
        // to be deleted
        List<Node> delvec = new ArrayList<>();
        rj1_enum_deletes(delvec, keepSet, new HashSet<Node>(articles), pageRoot);

        // delete these elements
        if (delvec.size() != 0)
        {
            DOM.removeElements(pageRoot, delvec);
        }
    }

    private void rj1_enum_deletes(List<Node> delvec, Set<Node> keepSet, Set<Node> stopSet, Node n) throws Exception
    {
        if (n == null)
            return;

        if (stopSet.contains(n))
        {
            // DOM.dumpNodeOffset(n, "STOP *** ");
            return;
        }

        if (n instanceof Element)
        {
            Element el = (Element) n;
            String name = el.getNodeName();
            if (name.equalsIgnoreCase("table") || name.equalsIgnoreCase("tr") || name.equalsIgnoreCase("td"))
            {
                if (!keepSet.contains(n))
                {
                    delvec.add(n);
                    // DOM.dumpNodeOffset(n, "DELETE *** ");
                    rj1_enum_deletes(delvec, keepSet, stopSet, n.getNextSibling());
                    return;
                }
            }
        }

        // DOM.dumpNodeOffset(n);

        rj1_enum_deletes(delvec, keepSet, stopSet, n.getFirstChild());
        rj1_enum_deletes(delvec, keepSet, stopSet, n.getNextSibling());
    }

    private void collectThreadLinks() throws Exception
    {
        StringBuilder sb = new StringBuilder();

        for (Node n : DOM.findElements(DOM.flatten(pageRoot), "a"))
        {
            String href = DOM.getAttribute(n, "href");
            if (href == null)
                continue;
            href = Util.stripAnchor(href);
            if (!LJUtil.isJournalUrl(href, sb))
                continue;
            if (!Util.beginsWith(sb.toString(), this.rurl + "?", sb))
                continue;
            Map<String, String> params = Util.parseUrlParams(sb.toString());
            String thread = params.get("thread");
            if (thread != null)
                pageThreads.add(thread);
        }
    }

    int numberOfCommentPages() throws Exception
    {
        int npages = 1;

        StringBuilder sb = new StringBuilder();

        for (Node n : DOM.findElements(DOM.flatten(pageRoot), "a"))
        {
            String href = DOM.getAttribute(n, "href");
            if (href == null)
                continue;
            href = Util.stripAnchor(href);
            if (!LJUtil.isJournalUrl(href, sb))
                continue;
            if (!Util.beginsWith(sb.toString(), this.rurl + "?", sb))
                continue;
            Map<String, String> params = Util.parseUrlParams(sb.toString());
            String np = params.get("page");
            if (np != null)
            {
                try
                {
                    int npg = Integer.parseInt(np);
                    if (npg > npages)
                        npages = npg;
                }
                catch (NumberFormatException ex)
                {
                    out("*** Error: record " + rurl + " contains invalid comments page link");
                }
            }
        }

        return npages;
    }

    public static void out(String s) throws Exception
    {
        Main.out(s);
    }
}
