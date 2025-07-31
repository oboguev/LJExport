package my.LJExport.runtime.links;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableObject;

import my.LJExport.Config;
import my.LJExport.runtime.ContentProvider;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.file.ServerContent;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.file.ServerContent.Decision;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.url.AwayLink;
import my.WebArchiveOrg.ArchiveOrgQuery;
import my.WebArchiveOrg.ArchiveOrgUrl;

public class SmartLinkRedownloader
{
    private final String linksDir;
    private boolean useArchiveOrg = true;

    public SmartLinkRedownloader(String linksDir)
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

    public Web.Response smartDownload(boolean image, String href, String referer, boolean allowAway, MutableObject<String> fromWhere)
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

        String archivedUrl = ArchiveOrgUrl.directDownloadUrl(original, timestamp, false);
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
        if (r != null && isGoodResponse(image, href, r))
            return r;
        else
            return null;
    }

    private static boolean isGoodResponse(boolean image, String href, Web.Response r) throws Exception
    {
        if (r.code != 200)
            return false;

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
            return true;

        Decision decision = ServerContent.acceptContent(href, serverExt, urlPathExt, new ContentProvider(r.binaryBody), r);
        if (decision.isReject())
            return false;
        if (decision.isAccept())
            return true;

        if (image || FileTypeDetector.isImageExtension(urlPathExt))
            return FileTypeDetector.isImageExtension(contentExt);

        if (urlPathExt == null || FileTypeDetector.isServletExtension(urlPathExt))
            return true;

        return false;
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

            SmartLinkRedownloader self = new SmartLinkRedownloader(
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
