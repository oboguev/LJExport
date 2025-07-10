package my.LJExport.runtime.links;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    /*
     * Arguments are full path names.
     * Calculate relative link from @loadedFilePath leading to @linkPath 
     * 
     * For example:
     * 
     *     vvv/index.html
     *     bratstvo/oprichnik/03/fin-de-siecle.htm
     *     ../../../vvv/index.html
     */
    public static String fileRelativeLink(String linkPath, String loadedFilePath) throws Exception
    {
        return fileRelativeLink(linkPath, loadedFilePath, null);
    }

    public static String fileRelativeLink(String linkPath, String loadedFilePath, String commonRoot) throws Exception
    {
        // Resolve canonical paths
        File linkFile = new File(linkPath).getCanonicalFile();
        File loadedFile = new File(loadedFilePath).getCanonicalFile();

        // Validate that paths are absolute
        if (!linkFile.isAbsolute() || !loadedFile.isAbsolute())
            throw new IllegalArgumentException("Both linkPath and loadedFilePath must be fully qualified absolute paths.");

        // Resolve and normalize common root
        if (commonRoot != null)
        {
            File rootFile = new File(commonRoot).getCanonicalFile();

            if (!isSubpath(linkFile, rootFile) || !isSubpath(loadedFile, rootFile))
                throw new IllegalArgumentException("Both linkPath and loadedFilePath must be within the commonRoot.");
        }
        else
        {
            // If on Windows, ensure both files are on the same drive
            if (Util.isWindowsOS())
            {
                String linkDrive = getDriveLetter(linkFile);
                String loadedDrive = getDriveLetter(loadedFile);
                if (!linkDrive.equalsIgnoreCase(loadedDrive))
                {
                    throw new IllegalArgumentException("Paths are on different drives and no commonRoot was specified.");
                }
            }
        }

        // Compute relative path
        Path fromPath = loadedFile.getParentFile().toPath();
        Path toPath = linkFile.toPath();
        Path relativePath = fromPath.relativize(toPath);

        // Convert to Unix-style path
        return relativePath.toString().replace(File.separatorChar, '/');
    }

    private static boolean isSubpath(File child, File parent)
    {
        Path childPath = child.toPath().normalize();
        Path parentPath = parent.toPath().normalize();
        return childPath.startsWith(parentPath);
    }

    private static String getDriveLetter(File file) throws Exception
    {
        String path = file.getCanonicalPath();
        if (path.length() >= 2 && path.charAt(1) == ':')
            return path.substring(0, 2);

        throw new IllegalArgumentException("Path does not have a drive letter: " + path);
    }
}
