package my.LJExport.runtime.links;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
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

    public static enum LoadFrom
    {
        Online, Archive, OnlineAndArchive;

        public boolean hasOnline()
        {
            switch (this)
            {
            case Online:
            case OnlineAndArchive:
                return true;

            default:
                return false;
            }
        }

        public boolean hasArchive()
        {
            switch (this)
            {
            case Archive:
            case OnlineAndArchive:
                return true;

            default:
                return false;
            }
        }
    }

    public SmartLinkDownloader(String linksDir)
    {
        this.linksDir = linksDir;
    }

    public boolean redownloadToFile(boolean image, String href, String unixRelFilePath, String referer, LoadFrom loadFrom ,
            MutableObject<String> fromWhere)
            throws Exception
    {
        String fullFilePath = this.linksDir + File.separator + unixRelFilePath.replace('/', File.separatorChar);
        return redownloadToAbsoluteFile(image, href, fullFilePath, referer, loadFrom, fromWhere);
    }

    public boolean redownloadToAbsoluteFile(boolean image, String href, String fullFilePath, String referer,
            LoadFrom loadFrom,
            MutableObject<String> fromWhere)
            throws Exception
    {
        Web.Response r = smartDownload(image, href, referer, true, loadFrom, fromWhere);

        if (r != null)
        {
            File fp = new File(fullFilePath).getCanonicalFile();
            if (!fp.getParentFile().exists())
                fp.getParentFile().mkdirs();
            Util.writeToFileSafe(fullFilePath, r.binaryBody);
        }

        return r != null;
    }

    /* ================================================================================================== */

    public Web.Response smartDownload(boolean image, String href, String referer, boolean allowAway, LoadFrom loadFrom,
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
                Web.Response r = smartDownload(image, href, referer, false, loadFrom, fromWhere);
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

        Web.Response r = null;

        /*
         * Load live online copy
         */
        if (loadFrom.hasOnline())
        {
            r = load_good(image, href, referer, true);
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
        }
        

        /*
         * Load from acrhive.org
         */
        // was already an archive.org URL? 
        if (!loadFrom.hasArchive()|| ArchiveOrgUrl.isArchiveOrgUrl(href))
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
        r = load_good(image, archivedUrl, null, false);
        if (r != null)
        {
            if (fromWhere != null)
                fromWhere.setValue("web.archive.org/dynamic");
            return r;
        }

        return null;
    }

    /* ================================================================================================== */

    private static Web.Response load_good(boolean image, String href, String referer, boolean online) throws Exception
    {
        if (!ShouldDownload.shouldDownload(image, href, online))
            return null;

        Web.Response r = LinkRedownloader.redownload(image, href, referer);

        if (r == null)
            return null;

        ResponseAnalysis an = isGoodResponse(image, href, r);
        if (an.isGood)
            return r;

        if (image)
        {
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

            if (!FileTypeDetector.isHtmlExtension(an.serverExt) || r.textBody() == null)
                return null;

            PageParserDirectBasePassive parser = new PageParserDirectBasePassive();
            try
            {
                parser.parseHtml(r.textBody());
            }
            catch (Exception ex)
            {
                return null;
            }

            List<Node> vn = JSOUP.findElements(parser.pageRoot, "img");
            vn = eliminateStaticArchiveOrgImages(vn);
            if (vn.size() != 1)
                return null;

            String src = JSOUP.getAttribute(vn.get(0), "src");
            src = UrlUtil.decodeHtmlAttrLink(src);
            if (src == null || src.startsWith("data:"))
                return null;

            try
            {
                src = UrlUtil.encodeMinimal(src);
                src = Util.resolveURL(href, src);
            }
            catch (Exception ex)
            {
                return null;
            }

            if (!ShouldDownload.shouldDownload(image, src, online))
                return null;

            r = LinkRedownloader.redownload(image, src, referer);
            if (r == null)
                return null;

            an = isGoodResponse(image, href, r);
            if (an.isGood)
                return r;
        }

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

        Decision decision = ServerContent.acceptContent(href, serverExt, headerExt, urlPathExt, new ContentProvider(r.binaryBody),
                r);
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

    private static List<Node> eliminateStaticArchiveOrgImages(List<Node> vn) throws Exception
    {
        List<Node> list = new ArrayList<>();

        for (Node n : vn)
        {
            String src = JSOUP.getAttribute(vn.get(0), "src");
            src = UrlUtil.decodeHtmlAttrLink(src);

            if (src == null || Util.startsWith(src.trim().toLowerCase(), null,
                    "https://web-static.archive.org/",
                    "http://web-static.archive.org/",
                    "https://archiveteam.org/",
                    "http://archiveteam.org/"))
            {
                continue;
            }

            list.add(n);
        }

        return list;
    }

    /* =============================================================================================== */

    public static void main(String[] args)
    {
        try
        {
            Config.init("");
            Web.init();
            Config.mangleUser();
            Config.autoconfigureSite(false);

            // String href = "http://www.trilateral.org/library/crisis_of_democracy.pdf";
            // String fullFllePath = Config.DownloadRoot + File.separator + "@debug" + File.separator + "crisis_of_democracy.pdf";

            // String href = "http://www.trumanlibrary.org/whistlestop/study_collections/coldwar/documents/pdf/4-1.pdf";
            // String fullFllePath = Config.DownloadRoot + File.separator + "@debug" + File.separator + "4-1.pdf";

            // String href = "https://web.archive.org/web/20231201081812if_/https://1.bp.blogspot.com/_h_hLztz7W0s/Sq0s6CwFrJI/AAAAAAAADX4/xfV04qkGa1A/s1600-h/CheKa.JPG";
            // String href = "https://web.archive.org/web/20231201081812/https://1.bp.blogspot.com/_h_hLztz7W0s/Sq0s6CwFrJI/AAAAAAAADX4/xfV04qkGa1A/s1600-h/CheKa.JPG";

            // String href = "https://www.mid.ru/upload/iblock/e89/%D0%98%D0%9D%D0%A4%D0%9E%D0%A0%D0%9C%D0%90%D0%A6%D0%98%D0%9E%D0%9D%D0%9D%D0%AB%D0%99%20%D0%91%D0%AE%D0%9B%D0%9B%D0%95%D0%A2%D0%95%D0%9D%D0%AC%2024-26%20%D0%BC%D0%B0%D1%80%D1%82%D0%B0%20%202014.doc";
            // String fullFllePath = Config.DownloadRoot + File.separator + "@debug" + File.separator + "mid-x.doc";

            String href = "http://www.militaryphotos.net/forums/attachment.php?attachmentid=25628&d=1176896352";
            String fullFllePath = Config.DownloadRoot + File.separator + "@debug" + File.separator + "militaryphotos.xxx";

            SmartLinkDownloader self = new SmartLinkDownloader(null);
            boolean b = self.redownloadToAbsoluteFile(false, href, fullFllePath, null, LoadFrom.OnlineAndArchive, null);
            Util.unused(b);
        }
        catch (Exception ex)
        {
            Util.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }
}
