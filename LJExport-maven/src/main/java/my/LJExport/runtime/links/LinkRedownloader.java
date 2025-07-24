package my.LJExport.runtime.links;

import java.io.File;

public class LinkRedownloader
{
    private final String linksDir;
    
    public LinkRedownloader(String linksDir)
    {
        this.linksDir = linksDir;
    }
    
    public boolean redownload(String url, String unixRelFilePath) throws Exception
    {
        String fullFilePath = linksDir + File.separator + unixRelFilePath.replace("/", File.separator);
        // ###
        return true;
    }
}
