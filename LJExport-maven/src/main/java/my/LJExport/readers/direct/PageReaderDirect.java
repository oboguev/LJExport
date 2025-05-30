package my.LJExport.readers.direct;

import java.io.File;
import java.util.List;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.Comment;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.readers.PageReader;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.Web.Response;
import my.LJExport.xml.JSOUP;

public class PageReaderDirect implements PageReader, PageContentSource
{
    private final String fileDir;
    private final String linksDir;

    private PageParserDirect parser;

    public PageReaderDirect(String rurl, String fileDir, String linksDir)
    {
        parser = new PageParserDirect(this);

        rurl = "2352931.html"; // test: krylov (many pages of comments)
        // rurl = "5938498.html"; // test: oboguev (some comments)

        // rurl = "2106296.html"; // test: tor85 (no comments)  
        // rurl = "175603.html";  // test: a_bugaev (comments disabled)
        // rurl = "2532366.html"; // test: colonelcassad
        // rurl = "5182367.html"; // test: oboguev (private, no comments)
        // rurl = "2504913.html"; // test: krylov (unexpandable link)

        parser.rurl = rurl;
        parser.rid = rurl.substring(0, rurl.indexOf('.'));
        this.fileDir = fileDir + File.separator;
        this.linksDir = linksDir;
    }

    @Override
    public void readPage() throws Exception
    {
        // process page 1 completely but without comments yet
        parser.pageSource = loadPage(1);
        if (parser.pageSource == null)
        {
            Main.markFailedPage(parser.rurl);
            return;
        }

        if (parser.pageRoot == null)
            parser.parseHtml();

        // List<Comment> commentList = CommentHelper.extractCommentsBlockUnordered(parser.pageRoot);
        // CommentsTree commentTree = new CommentsTree(commentList); 

        parser.removeJunk(PageParserDirect.COUNT_PAGES | PageParserDirect.REMOVE_SCRIPTS);
        Node firstPageRoot = parser.pageRoot;

        // load comments for each of the pages
        for (int npage = 1; npage <= parser.npages; npage++)
        {
            String cjson = loadPageComments(npage);
            List<Comment> commentList = CommentHelper.extractCommentsBlockUnordered(cjson);
            CommentsTree commentTree = new CommentsTree(commentList); 

            Element commentsSection = parser.findCommentsSection(firstPageRoot);
            // ### load comments 
            // ### to under <article> find <div id="comments"> and append inside it
        }

        parser.downloadExternalLinks(firstPageRoot, linksDir);
        parser.pageSource = JSOUP.emitHtml(firstPageRoot);
        Util.writeToFileSafe(fileDir + parser.rid + ".html", parser.pageSource);

        // out(">>> done " + rurl);
    }

    private String loadPage(int npage) throws Exception
    {
        long t0 = System.currentTimeMillis();

        parser.pageSource = null;
        parser.pageRoot = null;

        /*
         * Try to load from manual-save override location.
         * Some pages cannot have their comments expanded, even manually, due to LJ bug,
         * see e.g. http://oboguev.livejournal.com/701758.html
         * and need to be loaded from the manual-override area.
         */
        parser.pageSource = Main.manualPageLoad(parser.rurl, npage);
        if (parser.pageSource != null)
            return parser.pageSource;

        /*
         * Perform initial page load
         */
        return loadPage(npage, t0);
    }

    private String lastReadPageSource = null;

    private String loadPage(int npage, long t0) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append("http://" + Config.MangledUser + "." + Config.Site + "/" + parser.rurl + "?format=light");
        if (npage != 1)
            sb.append("&page=" + npage);

        boolean retry = true;
        int retries = 0;

        for (int pass = 0;; pass++)
        {
            Util.unused(pass);
            
            parser.pageRoot = null;
            lastReadPageSource = null;

            Main.checkAborting();

            if (retry)
            {
                retry = false;
                retries++;
                pass = 0;
            }

            Response r = Web.get(sb.toString());

            if (r.code != 200)
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else if (parser.isBadGatewayPage(r.body))
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else
            {
                lastReadPageSource = r.body;
                parser.pageSource = lastReadPageSource;
                return lastReadPageSource;
            }
        }
    }

    @Override
    public String getPageSource() throws Exception
    {
        return lastReadPageSource;
    }

    private String loadPageComments(int npage) throws Exception
    {
        long t0 = System.currentTimeMillis();
        return loadPageComments(npage, t0);
    }

    private String loadPageComments(int npage, long t0) throws Exception
    {
        String url = String.format("http://%s.%s/%s/__rpc_get_thread?journal=%s&itemid=%s&skip=&media=&page=%d&expand_all=1",
                Config.MangledUser, 
                Config.Site, 
                Config.MangledUser,
                Config.User,
                parser.rid,
                npage);
        
        boolean retry = true;
        int retries = 0;

        for (int pass = 0;; pass++)
        {
            Util.unused(pass);
            Main.checkAborting();

            if (retry)
            {
                retry = false;
                retries++;
                pass = 0;
            }

            Response r = Web.get(url);

            if (r.code != 200)
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else if (parser.isBadGatewayPage(r.body))
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else
            {
                return r.body;
            }
        }
    }
}
