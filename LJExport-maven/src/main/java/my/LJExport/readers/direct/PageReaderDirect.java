package my.LJExport.readers.direct;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.Comment;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.readers.PageReader;
import my.LJExport.runtime.LJUtil;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.Web.Response;
import my.LJExport.xml.JSOUP;

public class PageReaderDirect implements PageReader, PageContentSource
{
    private final String fileDir;
    private final String linksDir;

    private PageParserDirectBase parser;
    
    /*
     * For 1st page, use comment data embedded in page response, and do not do RPC.
     * This could have potential benefit, since RPC may be unneeded for 1s page then. 
     * However many of embedded comments are collapsed, so in practice more requests will be required
     * to expand them.
     * 
     * In addition, the following comment elements differ in the embedded case:
     *     type 
     *     level (there is also added deepLevel)
     *     leafclass can be "collapsed"
     */
    private static final boolean UseEmbeddedComments = false;

    public PageReaderDirect(String rurl, String fileDir, String linksDir)
    {
        parser = new PageParserDirectClassic(this);

        if (Config.False)
        {
            // rurl = "19518.html";   // test: roineroyce
            // rurl = "88279.html";   // test: nilsky_nikolay
            // rurl = "1076886.html"; // test: genby
            // rurl = "7430586.html"; // test: oboguev (with snipboard image)
            // rurl = "7450356.html"; // test: oboguev (no comments)
            // rurl = "2352931.html"; // test: krylov (many pages of comments)
            // rurl = "5938498.html"; // test: oboguev (some comments)

            // rurl = "2106296.html"; // test: tor85 (no comments)  
            // rurl = "175603.html";  // test: a_bugaev (comments disabled)
            // rurl = "2532366.html"; // test: colonelcassad
            // rurl = "5182367.html"; // test: oboguev (private, no comments)
            // rurl = "2504913.html"; // test: krylov (unexpandable link)
        }

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

        switch (parser.detectPageStyle())
        {
        case "classic":
            break;

        case "new-style":
            parser = new PageParserDirectNewStyle(parser);
            break;
        }

        // List<Comment> commentList = CommentHelper.extractCommentsBlockUnordered(parser.pageRoot);
        // CommentsTree commentTree = new CommentsTree(commentList);
        
        if (UseEmbeddedComments)
        {
            parser.removeJunk(PageParserDirectBase.COUNT_PAGES |
                    PageParserDirectBase.CHECK_HAS_COMMENTS |
                    PageParserDirectBase.REMOVE_SCRIPTS |
                    PageParserDirectBase.EXTRACT_COMMENTS_JSON);
        }
        else
        {
            parser.removeJunk(PageParserDirectBase.COUNT_PAGES |
                    PageParserDirectBase.CHECK_HAS_COMMENTS |
                    PageParserDirectBase.REMOVE_SCRIPTS);
        }

        Node firstPageRoot = parser.pageRoot;

        // devCapturePageComments();

        String threadName = Thread.currentThread().getName();

        if (parser.hasComments)
        {
            // load comments for each of the pages
            for (int npage = 1; npage <= parser.npages; npage++)
            {
                Thread.currentThread().setName(threadName + " comments page " + npage);
                String cjson = loadPageComments(npage);
                if (cjson == null)
                {
                    Main.markFailedPage(parser.rurl);
                    return;
                }
                // devSaveJson(cjson, "x-" + parser.rid);

                List<Comment> commentList = CommentHelper.extractCommentsBlockUnordered(cjson);
                CommentsTree commentTree = new CommentsTree(commentList);
                if (!expandCommentTree(npage, commentTree))
                {
                    Main.markFailedPage(parser.rurl);
                    return;
                }

                // insert comments from commentTree into commentsSection 
                // i.e. to under <article> find <div id="comments"> and append inside it
                if (commentTree.hasComments())
                {
                    Element commentsSection = parser.findCommentsSection(firstPageRoot, true);
                    parser.injectComments(commentsSection, commentTree);
                }
            }
        }

        Thread.currentThread().setName(threadName);

        parser.downloadExternalLinks(firstPageRoot, linksDir);
        parser.pageSource = JSOUP.emitHtml(firstPageRoot);
        Util.writeToFileSafe(fileDir + parser.rid + ".html", parser.pageSource);

        // out(">>> done " + rurl);
    }

    private boolean expandCommentTree(int npage, CommentsTree commentTree) throws Exception
    {
        String threadNameBase = Thread.currentThread().getName();

        try
        {
            Comment cload = null;
            int nload = 1;

            while (null != (cload = commentTree.findFirstUnloadedOrToExpandComment()))
            {
                if (Config.False)
                {
                    Main.out(String.format("Expanding page=%d [call %d] thread %s, remaining %d of %d",
                            npage,
                            nload,
                            cload.thread,
                            commentTree.countUnloadedOrUnexpandedComments(),
                            commentTree.totalComments()));
                }

                Thread.currentThread().setName(threadNameBase + " expansion #" + nload);
                String cjson = loadCommentsThread(cload.thread);
                if (cjson == null)
                    return false;
                // devSaveJson(cjson, "x-" + parser.rid + "-" + nload + "-" + cload.thread);

                List<Comment> commentList = CommentHelper.extractCommentsBlockUnordered(cjson, cload);
                commentTree.merge(cload, commentList);
                nload++;
            }

            commentTree.checkHaveAllComments();

            return true;
        }
        finally
        {
            Thread.currentThread().setName(threadNameBase);
        }
    }

    private String lastReadPageSource = null;

    @Override
    public String getPageSource() throws Exception
    {
        return lastReadPageSource;
    }

    private String loadPage(int npage) throws Exception
    {
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
         * Perform actual page load
         */
        StringBuilder sb = new StringBuilder();
        sb.append(LJUtil.recordPageURL(parser.rurl) + "?format=light");
        if (npage != 1)
            sb.append("&page=" + npage);

        parser.pageRoot = null;
        lastReadPageSource = null;

        parser.pageSource = lastReadPageSource = load(sb.toString());

        return lastReadPageSource;
    }

    @SuppressWarnings("unused")
    private String lastURL = null;

    private String load(String url) throws Exception
    {
        return load(url, null);
    }

    private String loadJson(String url) throws Exception
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", Config.UserAgentAccept_Json);
        return load(url, headers);
    }

    private String load(String url, Map<String, String> headers) throws Exception
    {
        lastURL = url;

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

            Response r = Web.get(url, headers);

            if (r.code == 204)
            {
                return null;
            }
            else if (r.code == 404 || r.code == 410 || r.code == 451)
            {
                // 404 may mean a suspended journal
                return null;
            }
            else if (r.code != 200)
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

    private String loadPageComments(int npage) throws Exception
    {
        String url = LJUtil.rpcCommentsForPage(parser.rid, npage);
        return loadJson(url);
    }

    private String loadCommentsThread(String thread) throws Exception
    {
        if (thread == null || thread.equals(""))
            throw new Exception("Missing comment thread id");

        String url = LJUtil.rpcCommentsForThread(parser.rid, thread);
        return loadJson(url);
    }

    @SuppressWarnings("unused")
    private void devCapturePageComments() throws Exception
    {
        // flat/noflat совпвадают с точностью до imgprx
        // expand длиннее noexpand

        final int npage = 1;
        String url, json;
        String dir = "c:\\@\\qqq\\";

        url = String.format("http://%s.%s/%s/__rpc_get_thread?journal=%s&itemid=%s&skip=&media=&page=%d&expand_all=1",
                Config.MangledUser,
                Config.Site,
                Config.MangledUser,
                Config.User,
                parser.rid,
                npage);
        json = loadJson(url);
        json = Util.prettyJSON(json);
        Util.writeToFile(dir + "1-expand" + ".json", json);

        url = String.format("http://%s.%s/%s/__rpc_get_thread?journal=%s&itemid=%s&flat=&skip=&media=&page=%d&expand_all=1",
                Config.MangledUser,
                Config.Site,
                Config.MangledUser,
                Config.User,
                parser.rid,
                npage);
        json = loadJson(url);
        json = Util.prettyJSON(json);
        Util.writeToFile(dir + "1-expand-flat" + ".json", json);

        url = String.format("http://%s.%s/%s/__rpc_get_thread?journal=%s&itemid=%s&skip=&media=&page=%d",
                Config.MangledUser,
                Config.Site,
                Config.MangledUser,
                Config.User,
                parser.rid,
                npage);
        json = loadJson(url);
        json = Util.prettyJSON(json);
        Util.writeToFile(dir + "1-noexpand" + ".json", json);

        url = String.format("http://%s.%s/%s/__rpc_get_thread?journal=%s&itemid=%s&flat=&skip=&media=&page=%d",
                Config.MangledUser,
                Config.Site,
                Config.MangledUser,
                Config.User,
                parser.rid,
                npage);
        json = loadJson(url);
        json = Util.prettyJSON(json);
        Util.writeToFile(dir + "1-noexpand-flat" + ".json", json);
    }

    @SuppressWarnings("unused")
    private void devSaveJson(String json, String path) throws Exception
    {
        String dir = "c:\\@\\qqq\\";
        json = Util.prettyJSON(json);
        Util.writeToFileSafe(dir + path + ".json", json);
    }
}
