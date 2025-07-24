package my.LJExport.runtime.links.util;

import java.io.File;

import my.LJExport.runtime.Util;

public class DownloadSource
{
    private final String rootDir;
    private DownloadSource chain = null;

    public DownloadSource(String rootDir)
    {
        this.rootDir = rootDir;
    }
    
    public void chain(DownloadSource next)
    {
        if (chain == null)
            chain = next;
        else
            chain.chain(next);
    }    

    /*
     * relPath is Unix-style path relative to overrides repository
     */
    public byte[] load(String href, String relPath) throws Exception
    {
        if (relPath.contains("/general1.css"))
        {
            Util.noop();
        }

        String path = rootDir + File.separator + relPath.replace("/", File.separator);
        File fp = new File(path);
        if (fp.exists())
        {
            return Util.readFileAsByteArray(path);
        }
        else if (chain != null)
        {
            return chain.load(href, relPath);
        }
        else
        {
            return null;
        }
    }
}
