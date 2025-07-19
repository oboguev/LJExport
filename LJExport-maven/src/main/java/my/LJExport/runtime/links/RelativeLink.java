package my.LJExport.runtime.links;

import static my.LJExport.runtime.file.FilePath.canonicalFile;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

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
     * Result examples:
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
        File linkFile = null;
        File loadedFile = null;

        try
        {
            linkFile = canonicalFile(new File(linkPath));
            loadedFile = canonicalFile(new File(loadedFilePath));
        }
        catch (Exception ex)
        {
            throw ex;
        }

        // Validate that paths are absolute
        if (!linkFile.isAbsolute() || !loadedFile.isAbsolute())
            throw new IllegalArgumentException("Both linkPath and loadedFilePath must be fully qualified absolute paths.");

        // Resolve and normalize common root
        if (commonRoot != null)
        {
            File rootFile = canonicalFile(new File(commonRoot));

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

    /**
     * Resolves a relative file link against a given absolute base file path.
     * <p>
     * The resolution is done purely in-memory and syntactically—no filesystem access occurs.
     * Both Unix-style ('/') and platform-specific separators in the {@code relativeLink} are accepted.
     * Components like "." and ".." are normalized.
     *
     * @param absoluteBasePath the absolute path to the base file (not a directory); must be absolute
     * @param relativeLink the relative link to be resolved; must not be absolute
     * @return the resolved absolute path as a {@code String}, with all "." and ".." components resolved
     * @throws IllegalArgumentException if {@code absoluteBasePath} is not absolute,
     *                                  or if {@code relativeLink} is not a relative path
     */
    public static String resolveFileRelativeLink(String absoluteBasePath, String relativeLink)
    {
        return resolveFileRelativeLink(absoluteBasePath, relativeLink, null);
    }

    public static String resolveFileRelativeLink(String absoluteBasePath, String relativeLink, String withinRootDir)
    {
        if (absoluteBasePath == null || relativeLink == null)
            throw new NullPointerException("Arguments must not be null");

        File baseFile = new File(absoluteBasePath);
        if (!baseFile.isAbsolute())
            throw new IllegalArgumentException("absoluteBasePath must be absolute: " + absoluteBasePath);

        String sep = File.separator;
        String rel = relativeLink.replace("/", sep);

        if (new File(rel).isAbsolute())
            throw new IllegalArgumentException("relativeLink must be relative: " + relativeLink);

        // Get base directory and extract drive letter (Windows)
        String baseDir = baseFile.getParent();
        if (baseDir == null)
            throw new IllegalArgumentException("Base file has no parent directory: " + absoluteBasePath);

        String drivePrefix = "";
        int startIndex = 0;

        if (baseDir.length() >= 2 && baseDir.charAt(1) == ':' && Character.isLetter(baseDir.charAt(0)))
        {
            drivePrefix = baseDir.substring(0, 2); // "F:"
            startIndex = 2;

            // Skip possible separator after drive, e.g. "F:\"
            if (baseDir.length() > 2 && (baseDir.charAt(2) == '\\' || baseDir.charAt(2) == '/'))
            {
                startIndex++;
            }
        }

        // Deque holds normalized segments
        Deque<String> stack = new ArrayDeque<>();
        String[] baseParts = baseDir.substring(startIndex).split("[/\\\\]+");

        for (String s : baseParts)
        {
            if (!s.isEmpty())
            {
                stack.addLast(s);
            }
        }

        for (String part : rel.split("[/\\\\]+"))
        {
            if (part.equals(".") || part.isEmpty())
            {
                continue;
            }
            else if (part.equals(".."))
            {
                if (!stack.isEmpty())
                {
                    stack.removeLast();
                }
            }
            else
            {
                stack.addLast(part);
            }
        }

        // Rebuild final path
        StringBuilder resolved = new StringBuilder();
        if (!drivePrefix.isEmpty())
        {
            resolved.append(drivePrefix).append(sep);
        }
        else if (absoluteBasePath.startsWith(sep))
        {
            resolved.append(sep);
        }

        boolean first = true;
        for (String part : stack)
        {
            if (!first)
            {
                resolved.append(sep);
            }
            resolved.append(part);
            first = false;
        }

        String result = resolved.toString();

        if (withinRootDir != null && !withinRoot(withinRootDir, result))
            throw new IllegalArgumentException("Path escapes root directory");

        return result;
    }

    /**
     * Determines whether a given absolute path is located under a specified root directory,
     * excluding the root directory itself.
     * <p>
     * Both input paths must be absolute. Resolution is done syntactically and in-memory—
     * no file system access is performed.
     *
     * @param rootDir the absolute path to the root directory
     * @param path    the absolute path to check
     * @return {@code true} if {@code path} is strictly under {@code rootDir}; {@code false} otherwise
     * @throws IllegalArgumentException if either argument is not an absolute path
     */
    public static boolean withinRoot(String rootDir, String path)
    {
        Path root = Paths.get(rootDir).normalize();
        Path target = null;

        try
        {
            target = Paths.get(path).normalize();
        }
        catch (InvalidPathException ex)
        {
            throw new InvalidNestedPathException(ex.getLocalizedMessage(), ex);
        }

        if (!root.isAbsolute())
            throw new IllegalArgumentException("rootDir must be an absolute path: " + rootDir);
        if (!target.isAbsolute())
            throw new IllegalArgumentException("path must be an absolute path: " + path);

        // target must have more name elements than root, and root must be a prefix of target
        int rootCount = root.getNameCount();
        int targetCount = target.getNameCount();

        return targetCount > rootCount && target.subpath(0, rootCount).equals(root.subpath(0, rootCount));
    }

    public static class InvalidNestedPathException extends IllegalArgumentException
    {
        private static final long serialVersionUID = 1L;

        public InvalidNestedPathException(String message, Throwable cause)
        {
            super(message, cause);

        }

        public InvalidNestedPathException(Throwable cause)
        {
            super(cause);
        }
    }
}
