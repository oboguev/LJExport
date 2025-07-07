package my.LJExport.runtime.links;

import my.LJExport.runtime.Util;

public class RelativeLink
{
    /*
     * Link arguments have format a/b/c relative to common root.
     * Calculate relative link from @loadedFilePath leading to @linkPath 
     * 
     * For example:
     * 
     *     vvv/index.html
     *     bratstvo/oprichnik/03/fin-de-siecle.htm
     *     ../../../vvv/index.html
     */
    public static String createRelativeLink(String linkPath, String loadedFilePath) throws Exception
    {
        if (linkPath.startsWith("/") || linkPath.contains("//") || linkPath.contains("\\"))
            throw new IllegalArgumentException("Unexpected link: " + linkPath);

        if (loadedFilePath.startsWith("/") || loadedFilePath.contains("//") || loadedFilePath.contains("\\"))
            throw new IllegalArgumentException("Unexpected link: " + loadedFilePath);

        while (linkPath.endsWith("/"))
            linkPath = Util.stripTail(linkPath, "/");

        while (loadedFilePath.endsWith("/"))
            linkPath = Util.stripTail(loadedFilePath, "/");

        // Split paths into components
        String[] linkParts = linkPath.split("/");
        String[] loadedParts = loadedFilePath.split("/");

        // Remove the filename from loaded path
        int loadedDepth = loadedParts.length - 1;

        // Find common prefix
        int common = 0;
        while (common < loadedDepth && common < linkParts.length &&
                loadedParts[common].equals(linkParts[common]))
        {
            common++;
        }

        // Add enough .. to go from loaded file to common ancestor
        StringBuilder relative = new StringBuilder();
        for (int i = common; i < loadedDepth; i++)
        {
            relative.append("../");
        }

        // Add the remaining part of the link path
        for (int i = common; i < linkParts.length; i++)
        {
            relative.append(linkParts[i]);
            if (i < linkParts.length - 1)
            {
                relative.append("/");
            }
        }

        String result = relative.toString();
        
        if (result.contains("//"))
            throw new Exception("Failed to calculate relative link");
        
        return result;
    }
}
