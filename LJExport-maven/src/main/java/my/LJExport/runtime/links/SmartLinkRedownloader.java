package my.LJExport.runtime.links;

import java.net.URL;

import my.LJExport.runtime.ContentProvider;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.file.ServerContent;
import my.LJExport.runtime.file.ServerContent.Decision;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.util.LinkFilepath;

public class SmartLinkRedownloader
{
    private final String linksDir;
    private final LinkRedownloader linkRedownloader;

    public SmartLinkRedownloader(String linksDir)
    {
        this.linksDir = linksDir;
        this.linkRedownloader = new LinkRedownloader(linksDir); 
    }
    
    public boolean redownload(boolean image, String href, String unixRelFilePath, String referer) throws Exception
    {
        // ### add handling via archive.org
        // ### strip anchor for arheive.org
        // ### use ArchiveOrgSourceUrl.variants
        // ### limit rate to 1.2
        // ### concurrency 5
        // ### handle http 429 (cool off and retry)
        return false;
    }
    
    private boolean isGoodResponse(boolean image, String href, Web.Response r) throws Exception
    {
        if (r.code != 200)
            return false;
        
        // ### archive prefix
        String urlPathExt = LinkFilepath.getMediaFileExtension(new URL(href).getPath());

        String contentExt = FileTypeDetector.fileExtensionFromActualFileContent(r.binaryBody);

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
}
