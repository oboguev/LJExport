package my.LJExport.readers;

import java.io.File;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.Web.Response;
import my.LJExport.xml.JSOUP;

public class PageReaderDirect extends PageParserDirect implements PageReader
{
    private final String fileDir;
    private final String linksDir;

    private final boolean saveAsSinglePage = true;
    private static final String PROCEED = "<PROCEED>";

    public PageReaderDirect(String rurl, String fileDir, String linksDir)
    {
        rurl = "2352931.html"; // test: krylov (many pages of comments)
        // rurl = "5938498.html"; // test: oboguev (some comments)

        // rurl = "2106296.html"; // test: tor85 (no comments)  
        // rurl = "175603.html";  // test: a_bugaev (comments disabled)
        // rurl = "2532366.html"; // test: colonelcassad
        // rurl = "5182367.html"; // test: oboguev (private, no comments)
        // rurl = "2504913.html"; // test: krylov (unexpandable link)
        
        this.rurl = rurl;
        this.rid = rurl.substring(0, rurl.indexOf('.'));
        this.fileDir = fileDir + File.separator;
        this.linksDir = linksDir;
    }

    @Override
    public void readPage() throws Exception
    {
        if (saveAsSinglePage)
        {
            // process page 1 completely
            pageSource = loadPage(1);
            if (pageSource == null)
            {
                Main.markFailedPage(rurl);
                return;
            }

            if (pageRoot == null)
                parseHtml();

            removeJunk(COUNT_PAGES | REMOVE_SCRIPTS | CHECK_COMMENTS_MERGEABLE);
            Node firstPageRoot = pageRoot;

            // load extra pages of comments
            for (int npage = 2; npage <= npages; npage++)
            {
                pageSource = loadPage(npage);
                if (pageSource == null)
                {
                    Main.markFailedPage(rurl);
                    return;
                }

                if (pageRoot == null)
                    parseHtml();

                removeJunk(REMOVE_MAIN_TEXT | REMOVE_SCRIPTS);
                mergeComments(firstPageRoot);
            }

            downloadExternalLinks(firstPageRoot, linksDir);
            pageSource = JSOUP.emitHtml(firstPageRoot);
            Util.writeToFileSafe(fileDir + rid + ".html", pageSource);
        }
        else
        {
            // save page 1 completely, except for junk sections
            pageSource = loadPage(1);
            if (pageSource == null)
            {
                Main.markFailedPage(rurl);
                return;
            }

            String rbody_1 = removeJunkAndEmitHtml(COUNT_PAGES);

            // load extra pages of comments
            for (int npage = 2; npage <= npages; npage++)
            {
                pageSource = loadPage(npage);
                if (pageSource == null)
                {
                    Main.markFailedPage(rurl);
                    return;
                }

                pageSource = removeJunkAndEmitHtml(REMOVE_MAIN_TEXT);
                Util.writeToFileSafe(fileDir + rid + "_page_" + npage + ".html", pageSource);
            }

            Util.writeToFileSafe(fileDir + rid + ".html", rbody_1);
        }

        // out(">>> done " + rurl);
    }

    private String removeJunkAndEmitHtml(int flags) throws Exception
    {
        if (pageRoot == null)
            parseHtml();
        removeJunk(flags);
        downloadExternalLinks(pageRoot, linksDir);
        return JSOUP.emitHtml(pageRoot);
    }

    private String loadPage(int npage) throws Exception
    {
        long t0 = System.currentTimeMillis();

        pageSource = null;
        pageRoot = null;

        /*
         * Try to load from manual-save override location.
         * Some pages cannot have their comments expanded, even manually, due to LJ bug,
         * see e.g. http://oboguev.livejournal.com/701758.html
         * and need to be loaded from the manual-override area.
         */
        pageSource = Main.manualPageLoad(rurl, npage);
        if (pageSource != null)
            return pageSource;

        /*
         * Perform initial page load
         */
        String res = loadPage_initial(npage, t0);
        if (res != PROCEED)
            return res;

        // ###
        return res;

        /*
         * See if we need to expand comments 
         */
        //  ### res = loadPage_checkNeedExpandAllComments(npage, t0);
        //  ### if (res != PROCEED)
        //  ###     return res;

        /*
         * Expand comments 
         */
        //  ### pageSource = loadPage_doExpandAllComments(npage);
        //  ### if (pageSource == null)
        //  ###             return null;

        /*
         * Second round of comment expansion.
         * 
         * On some pages, "Expand All" expands some (most) of the comments,
         * but not all of the comments, leaving elements <span class="b-leaf-seemore-expand">
         * with text like "...and 27 more comments..." next to them.
         * We should locate and trigger an <a> element under these span elements
         * until these span elements are gone.
         */
        //  ### return loadPage_doExpandMoreComments(npage);
    }
    
    private String lastReadPageSource = null;

    private String loadPage_initial(int npage, long t0) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append("http://" + Config.MangledUser + "." + Config.Site + "/" + rurl + "?format=light");
        if (npage != 1)
            sb.append("&page=" + npage);

        boolean retry = true;
        int retries = 0;

        for (int pass = 0;; pass++)
        {
            pageRoot = null;
            lastReadPageSource = null;

            Main.checkAborting();

            if (retry)
            {
                retry = false;
                retries++;
                pass = 0;
            }
            
            Response r = Web.get(sb.toString());

            if (isBadGatewayPage(r.body))
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else if (r.code != 200)
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else
            {
                lastReadPageSource = r.body;
                pageSource = lastReadPageSource;
                // break; // ###
                return lastReadPageSource;
            }
        }

        // ### return PROCEED;
    }

    @Override
    protected String getPageSource() throws Exception
    {
        return lastReadPageSource;
    }
}
