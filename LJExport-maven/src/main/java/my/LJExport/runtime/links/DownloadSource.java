package my.LJExport.runtime.links;

import java.io.File;

import my.LJExport.runtime.Util;

public class DownloadSource
{
    private final String rootDir;

    public DownloadSource(String rootDir)
    {
        this.rootDir = rootDir;
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
        else
        {
            return null;
        }
    }
}
