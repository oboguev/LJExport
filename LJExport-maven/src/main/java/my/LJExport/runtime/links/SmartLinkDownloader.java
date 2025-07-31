package my.LJExport.runtime.links;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.ContentProvider;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.file.ServerContent;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.file.ServerContent.Decision;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.url.AwayLink;
import my.LJExport.runtime.url.UrlUtil;
import my.WebArchiveOrg.ArchiveOrgQuery;
import my.WebArchiveOrg.ArchiveOrgUrl;

public class SmartLinkDownloader
{
    private final String linksDir;
    private boolean useArchiveOrg = true;

    public SmartLinkDownloader(String linksDir)
    {
        this.linksDir = linksDir;
    }

    public void useArchiveOrg(boolean useArchiveOrg)
    {
        this.useArchiveOrg = useArchiveOrg;
    }

    public boolean redownload(boolean image, String href, String unixRelFilePath, String referer, MutableObject<String> fromWhere)
            throws Exception
    {
        Web.Response r = smartDownload(image, href, referer, true, fromWhere);

        if (r != null)
        {
            String fullFilePath = this.linksDir + File.separator + unixRelFilePath.replace('/', File.separatorChar);
            File fp = new File(fullFilePath).getCanonicalFile();
            if (!fp.exists())
                fp.mkdirs();
            Util.writeToFileSafe(fullFilePath, r.binaryBody);
        }

        return r != null;
    }

    /* ================================================================================================== */

    public Web.Response smartDownload(boolean image, String href, String referer, boolean allowAway,
            MutableObject<String> fromWhere)
            throws Exception
    {
        href = Util.stripAnchor(href);

        if (allowAway)
        {
            /*
             * If the link is packed into a redirector URL,
             * iteratively try loading unwrapping the link from outer to inner layers
             */
            for (;;)
            {
                Web.Response r = smartDownload(image, href, referer, false, fromWhere);
                if (r != null)
                    return r;

                String prev = href;
                href = AwayLink.unwrapAwayLinkDecoded(href);
                href = Util.stripAnchor(href);
                if (href.equals(prev))
                    return null;
            }
        }

        if (fromWhere != null)
            fromWhere.setValue(null);

        /*
         * Load live online copy
         */
        Web.Response r = load_good(image, href, referer);
        if (r != null)
        {
            if (fromWhere != null)
            {
                if (ArchiveOrgUrl.isArchiveOrgUrl(href))
                    fromWhere.setValue("web.archive.org/explicit");
                else
                    fromWhere.setValue("online");
            }

            return r;
        }

        /*
         * Load from acrhive.org
         */
        // was already an archive.org URL? 
        if (!useArchiveOrg || ArchiveOrgUrl.isArchiveOrgUrl(href))
            return null;

        /*
         * Query available acrhive.org snapshots 
         */
        List<KVEntry> entries = ArchiveOrgQuery.querySnapshots(href, 1);
        if (entries == null || entries.size() == 0)
            return null;

        /*
         * Load the snapshot
         */
        KVEntry e = entries.get(0);
        String timestamp = e.key;
        String original = e.value;

        String archivedUrl = ArchiveOrgUrl.directDownloadUrl(original, timestamp, false, image);
        r = load_good(image, archivedUrl, null);
        if (r != null)
        {
            if (fromWhere != null)
                fromWhere.setValue("web.archive.org/dynamic");
            return r;
        }

        return null;
    }

    /* ================================================================================================== */

    private static Web.Response load_good(boolean image, String href, String referer) throws Exception
    {
        Web.Response r = LinkRedownloader.redownload(image, href, referer);

        if (r == null)
            return null;

        ResponseAnalysis an = isGoodResponse(image, href, r);
        if (an.isGood)
            return r;

        if (!FileTypeDetector.isHtmlExtension(an.serverExt) || r.textBody() == null)
            return null;

        /*
         * archive.org snapshot for image URLs lead to if_ pages, 
         * which are HTML pahes with one IMG tag to archive.org im_ resource
         * 
         * For example, snapshot may contain a link to 
         *     https://web.archive.org/web/20231201081812if_/https://1.bp.blogspot.com/_h_hLztz7W0s/Sq0s6CwFrJI/AAAAAAAADX4/xfV04qkGa1A/s1600-h/CheKa.JPG
         * which is an HTML file with IMG.SRC link to    
         *     https://web.archive.org/web/20231201081812im_/https://1.bp.blogspot.com/_h_hLztz7W0s/Sq0s6CwFrJI/AAAAAAAADX4/xfV04qkGa1A/s1600/CheKa.JPG
        
         * We should follow it.
         * The same can also happen in other situation when a link is provided to HTML page that links to a single IMG.
         */
        PageParserDirectBasePassive parser = new PageParserDirectBasePassive();
        parser.parseHtml(r.textBody());
        List<Node> vn = JSOUP.findElements(parser.pageRoot, "img");
        if (vn.size() != 1)
            return null;
        String src = JSOUP.getAttribute(vn.get(0), "src");
        src = UrlUtil.decodeHtmlAttrLink(src);
        if (src == null)
            return null;
        
        r = LinkRedownloader.redownload(image, src, referer);
        if (r == null)
            return null;

        an = isGoodResponse(image, href, r);
        if (an.isGood)
            return r;

        return null;
    }

    private static ResponseAnalysis isGoodResponse(boolean image, String href, Web.Response r) throws Exception
    {
        if (r.code != 200)
            return new ResponseAnalysis(false, null);

        if (ArchiveOrgUrl.isArchiveOrgUrl(href))
            href = ArchiveOrgUrl.extractArchivedUrlPart(href);

        if (!Util.isAbsoluteURL(href))
            href = "https://" + href;

        String urlPathExt = LinkFilepath.getMediaFileExtension(new URL(href).getPath());

        String contentExt = FileTypeDetector.fileExtensionFromActualFileContent(r.binaryBody, urlPathExt);

        String headerExt = null;
        if (r.contentType != null)
            headerExt = FileTypeDetector.fileExtensionFromMimeType(Util.despace(r.contentType).toLowerCase());

        String serverExt = contentExt;
        if (serverExt == null)
            serverExt = headerExt;

        if (serverExt != null && urlPathExt != null && FileTypeDetector.isEquivalentExtensions(urlPathExt, serverExt))
            return new ResponseAnalysis(true, serverExt);

        Decision decision = ServerContent.acceptContent(href, serverExt, urlPathExt, new ContentProvider(r.binaryBody), r);
        if (decision.isReject())
            return new ResponseAnalysis(false, serverExt);
        if (decision.isAccept())
            return new ResponseAnalysis(true, serverExt);

        if (image || FileTypeDetector.isImageExtension(urlPathExt))
            return new ResponseAnalysis(FileTypeDetector.isImageExtension(contentExt), serverExt);

        if (urlPathExt == null || FileTypeDetector.isServletExtension(urlPathExt))
            return new ResponseAnalysis(true, serverExt);

        return new ResponseAnalysis(false, serverExt);
    }

    private static class ResponseAnalysis
    {
        public final boolean isGood;
        public final String serverExt;

        public ResponseAnalysis(boolean isGood, String serverExt)
        {
            this.isGood = isGood;
            this.serverExt = serverExt;
        }
    }

    /* =============================================================================================== */

    public static void main(String[] args)
    {
        try
        {
            Config.init("");
            Web.init();
            Config.mangleUser();
            Config.autoconfigureSite();

            SmartLinkDownloader self = new SmartLinkDownloader(
                    Config.DownloadRoot + File.separator + Config.User + File.separator + "links");
            String href = "http://www.trilateral.org/library/crisis_of_democracy.pdf";
            boolean b = self.redownload(false, href, null, null, null);
            Util.unused(b);
        }
        catch (Exception ex)
        {
            Util.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }
}
