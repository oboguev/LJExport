package my.LJExport.runtime.file;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Returns a syntactically normalized version of the given absolute {@link File} path.
 * 
 * This method performs the equivalent of {@link File#getCanonicalFile()}, but entirely
 * in-memory, without accessing the file system or resolving symbolic links.
 *
 * <p>It performs the following operations:</p>
 * <ul>
 *   <li>Verifies that the file path is absolute; if not, throws {@link IllegalArgumentException}.</li>
 *   <li>Rejects paths containing OS-incompatible separators (e.g., '/' on Windows or '\\' on Linux).</li>
 *   <li>Normalizes the path by resolving {@code "."} and {@code ".."} components.</li>
 *   <li>Preserves Windows drive prefixes (e.g., {@code "C:"}).</li>
 *   <li>Does not verify the existence of the file or directories in the path.</li>
 * </ul>
 *
 * <p>This method avoids JNI calls and is suitable for performance-critical code that
 * requires only syntactic normalization of paths.</p>
 *
 * @param fp the input file to normalize
 * @return a new {@code File} object with a normalized absolute path
 * @throws IllegalArgumentException if the input is {@code null}, not absolute,
 *         or contains invalid separators for the current OS
 */
public class FilePath
{
    public static File canonicalFile(File fp)
    {
        if (fp == null)
            throw new IllegalArgumentException("File must not be null");

        String path = fp.getPath();

        // Check OS-specific separator rules
        boolean isWindows = File.separatorChar == '\\';

        if (isWindows && path.contains("/"))
            throw new IllegalArgumentException("Invalid path: contains '/' on Windows: " + path);

        if (!isWindows && path.contains("\\"))
            throw new IllegalArgumentException("Invalid path: contains '\\' on Linux/Unix: " + path);

        // Check that the path is absolute
        if (!fp.isAbsolute())
            throw new IllegalArgumentException("Path is not absolute: " + path);

        String[] parts = path.split(isWindows ? "\\\\" : "/");

        Deque<String> stack = new ArrayDeque<>();

        int i = 0;

        // Handle Windows drive letter, e.g., "C:"
        if (isWindows && parts.length > 0 && parts[0].matches("^[A-Za-z]:$"))
        {
            stack.push(parts[0]);
            i = 1;
        }

        for (; i < parts.length; i++)
        {
            String part = parts[i];
            if (part.isEmpty() || part.equals("."))
                continue;
            if (part.equals(".."))
            {
                if (!stack.isEmpty() && !stack.peek().matches("^[A-Za-z]:$"))
                {
                    stack.pop();
                }
                else
                {
                    // Either root-level ".." or attempting to go above drive root — ignore
                }
            }
            else
            {
                stack.push(part);
            }
        }

        // Reconstruct normalized path
        StringBuilder sb = new StringBuilder();
        if (!isWindows)
        {
            sb.append('/');
        }

        Deque<String> reverse = new ArrayDeque<>();
        while (!stack.isEmpty())
            reverse.push(stack.pop());

        boolean first = true;
        while (!reverse.isEmpty())
        {
            String part = reverse.pop();
            if (first)
            {
                first = false;
                sb.append(part);
            }
            else
            {
                sb.append(isWindows ? "\\" : "/").append(part);
            }
        }

        return new File(sb.toString());
    }

    /*
     * If file already exists, but have different path name case 
     * (in Windows or even in Linux on case-insensitive file volume such as NTFS)
     * then return file path in actual case existing in file system.
     * 
     * If file does not exist, throws NoSuchFileException (from toRealPath).  
     */
    public static Path getFilePathActualCase(Path path) throws Exception
    {
        path = path.toRealPath(LinkOption.NOFOLLOW_LINKS); // resolves symlinks, . and ..

        Path parent = path.getParent();
        if (parent == null)
            return path; // root

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent))
        {
            for (Path entry : stream)
            {
                if (entry.getFileName().toString().equalsIgnoreCase(path.getFileName().toString()))
                {
                    Path result = getFilePathActualCase(parent).resolve(entry.getFileName());

                    String px = path.toAbsolutePath().toString();
                    String rx = result.toAbsolutePath().toString();
                    if (!px.equalsIgnoreCase(rx))
                        throw new Exception("Internal consistencey check failed");

                    return result;
                }
            }
        }

        // fallback
        return path;
    }

    public static String getFilePathActualCase(String path) throws Exception
    {
        String rpath = getFilePathActualCase(Paths.get(path)).toString();
        if (!rpath.equalsIgnoreCase(path))
            throw new Exception("Internal consistencey check failed");
        return rpath;
    }
}
