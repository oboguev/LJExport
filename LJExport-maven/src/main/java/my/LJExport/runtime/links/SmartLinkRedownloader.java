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
        return false;
    }
}
