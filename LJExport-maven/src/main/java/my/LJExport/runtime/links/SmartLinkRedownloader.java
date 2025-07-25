package my.LJExport.runtime.links;

public class SmartLinkRedownloader
{
    private final String linksDir;
    private final LinkRedownloader linkRedownloader;

    public SmartLinkRedownloader(String linksDir)
    {
        this.linksDir = linksDir;
        this.linkRedownloader = new LinkRedownloader(linksDir); 
    }
    
    public boolean redownload(String url, String unixRelFilePath, String referer, boolean image) throws Exception
    {
        // ### add handling via archive.org
        // ### strip anchor for arheive.org
        // ### use ArchiveOrgSourceUrl.variants
        // ### limit rate to 1.2
        // ### concurrency 5
        // ### handle 429
        return false;
    }
}
