package my.LJExport.readers.direct;

import java.util.Set;
import java.util.Vector;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.LinkDownloader;
import my.LJExport.xml.JSOUP;

public abstract class PageParserDirectBase
{
    private final PageContentSource pageContentSource;

    public PageParserDirectBase(PageContentSource pageContentSource)
    {
        this.pageContentSource = pageContentSource;
    }

    protected String getPageSource() throws Exception
    {
        return pageContentSource.getPageSource();
    }

    /* ============================================================== */

    public final static int COUNT_PAGES = (1 << 0);
    public final static int CHECK_HAS_COMMENTS = (1 << 1);
    public final static int REMOVE_MAIN_TEXT = (1 << 2);
    public final static int REMOVE_SCRIPTS = (1 << 3);

    public int npages = -1;
    public Boolean hasComments = null;

    public Node pageRoot;
    public String pageSource;

    public String rurl;
    public String rid;

    /* ============================================================== */

    public static void out(String s)
    {
        Main.out(s);
    }

    public static void err(String s)
    {
        Main.err(s);
    }

    protected void parseHtml(String html) throws Exception
    {
        this.pageSource = html;
        this.pageRoot = JSOUP.parseHtml(html);
    }

    protected void parseHtml() throws Exception
    {
        parseHtml(this.pageSource);
    }

    protected boolean isBadGatewayPage(String html) throws Exception
    {
        if (html.contains("Bad Gateway:") || html.contains("Gateway Timeout"))
        {
            if (pageRoot == null)
                pageRoot = JSOUP.parseHtml(html);
            Vector<Node> vel = JSOUP.findElements(JSOUP.flatten(pageRoot), "body");
            if (vel.size() != 1)
                throw new Exception("Unable to find BODY element in the html page");
            for (Node n : JSOUP.getChildren(vel.get(0)))
            {
                if (n instanceof TextNode)
                {
                    TextNode tn = (TextNode) n;
                    if (tn.text().contains("Bad Gateway:") || tn.text().contains("Gateway Timeout"))
                        return true;
                }
            }
        }

        return false;
    }

    /*static*/ public void downloadExternalLinks(Node root, String linksDir) throws Exception
    {
        if (linksDir == null || Config.DownloadFileTypes == null || Config.DownloadFileTypes.size() == 0)
            return;
        downloadExternalLinks(root, linksDir, "a", "href");
        downloadExternalLinks(root, linksDir, "img", "src");
    }

    /*static*/ private void downloadExternalLinks(Node root, String linksDir, String tag, String attr) throws Exception
    {
        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);

            if (LinkDownloader.shouldDownload(href))
            {
                String referer = "http://" + Config.MangledUser + "." + Config.Site + "/" + rurl;
                String newref = LinkDownloader.download(linksDir, href, referer);
                if (newref != null)
                    JSOUP.updateAttribute(n, attr, newref);
            }
        }
    }

    /* ============================================================== */

    public String detectPageStyle() throws Exception
    {
        Vector<Node> vnodes = JSOUP.findElements(pageRoot, "article");
        String style = null;

        for (Node n : vnodes)
        {
            Set<String> classes = JSOUP.getClassesLowercase(n);

            if (classes.contains("aentry") && classes.contains("aentry--post2017"))
                style = detectPageStyle(style, "new");

            if (classes.contains("b-singlepost") && classes.contains("hentry"))
                style = detectPageStyle(style, "classic");

            if (classes.contains("b-singlepost-body") && classes.contains("entry-content"))
                style = detectPageStyle(style, "classic");
        }

        if (style == null)
            throw new Exception("Unable to detect page style (missing indicators)");

        return style;
    }

    public String detectPageStyle(String s1, String s2) throws Exception
    {
        if (s1 == null || s1.equals(s2))
            return s2;

        throw new Exception("Unable to detect page style (conflicting indicators)");
    }

    /* ============================================================== */

    public abstract void removeJunk(int flags) throws Exception;

    public abstract Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception;

    public abstract void injectComments(Element commentsSection, CommentsTree commentTree) throws Exception;
}
