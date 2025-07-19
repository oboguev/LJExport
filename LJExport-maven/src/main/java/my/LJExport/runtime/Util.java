package my.LJExport.runtime;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import my.LJExport.Config;
import my.LJExport.runtime.synch.NamedLocks;

import org.json.JSONArray;

public class Util
{
    public static char lastChar(String s)
    {
        int len = s.length();
        if (len == 0)
            return '\0';
        else
            return s.charAt(len - 1);
    }

    public static String stripLastChar(String s) throws Exception
    {
        int len = s.length();
        if (len == 0)
            return s;
        else
            return s.substring(0, len - 1);
    }

    public static String stripLastChar(String s, char c) throws Exception
    {
        if (lastChar(s) == c)
            s = stripLastChar(s);
        return s;
    }

    public static String stripTail(String s, String tail) throws Exception
    {
        if (!s.endsWith(tail))
            throw new Exception("stripTail: [" + s + "] does not end with [" + tail + "]");
        return s.substring(0, s.length() - tail.length());
    }

    public static String stripStart(String s, String start) throws Exception
    {
        if (!s.startsWith(start))
            throw new Exception("stripTail: [" + s + "] does not start with [" + start + "]");
        return s.substring(start.length());
    }

    public static List<String> sort(Set<String> set) throws Exception
    {
        List<String> vs = new ArrayList<>();
        for (String s : set)
            vs.add(s);
        Collections.sort(vs);
        return vs;
    }

    public static Vector<String> sortAsVector(Set<String> set) throws Exception
    {
        return new Vector<>(sort(set));
    }

    public static boolean is_in_domain(String site, String domain) throws Exception
    {
        site = site.toLowerCase();
        domain = domain.toLowerCase();

        if (site.equals(domain))
            return true;

        int ls = site.length();
        int ld = domain.length();

        if (ls > ld && site.charAt(ls - ld - 1) == '.' && site.endsWith(domain))
            return true;

        if (site.startsWith("www."))
            return false;

        return is_in_domain("www." + site, domain);
    }

    /*
     * Check if @url begins with @prefix.
     * If yes, strip the prefix, store the result in @sb and return @true.
     * If no, return @false and the value of @sb is unpredictable.
     */
    public static boolean beginsWith(String url, String prefix, StringBuilder sb) throws Exception
    {
        if (url.length() >= prefix.length() && url.substring(0, prefix.length()).equalsIgnoreCase(prefix))
        {
            sb.setLength(0);
            sb.append(url.substring(prefix.length()));
            return true;
        }
        else
        {
            return false;
        }
    }

    /*
     * Check if @url begins with prefix = @path + optional "/". 
     * If yes, strip the prefix, store the result in @sb and return @true.
     * If no, return @false and the value of @sb is unpredictable.
     */
    public static boolean beginsWithPath(String url, String path, StringBuilder sb) throws Exception
    {
        if (url.equalsIgnoreCase(path))
        {
            sb.setLength(0);
            return true;
        }

        return beginsWith(url, path + "/", sb);
    }

    public static void mkdir(String path) throws Exception
    {
        File file = new File(path);
        if (file.exists() && file.isDirectory())
            return;
        if (file.mkdirs())
            return;
        // re-check again in case directory may have been created concurrently
        if (file.exists() && file.isDirectory())
            return;
        throw new UnableCreateDirectoryException("Unable to create directory " + path);
    }

    public static class UnableCreateDirectoryException extends Exception
    {
        private static final long serialVersionUID = 1L;

        public UnableCreateDirectoryException(String msg)
        {
            super(msg);
        }
    }

    public static void writeToFile(String path, String content) throws Exception
    {
        writeToFile(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeToFile(String path, byte[] bytes) throws Exception
    {
        FileOutputStream fos = new FileOutputStream(path);
        fos.write(bytes);
        fos.flush();
        fos.close();
    }

    public static final NamedLocks NamedFileLocks = new NamedLocks();

    public static void writeToFileSafe(String path, String content) throws Exception
    {
        writeToFileSafe(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeToFileSafe(String path, byte[] content) throws Exception
    {
        String threadName = Thread.currentThread().getName();
        Thread.currentThread().setName(threadName + " waiting filelock");

        NamedFileLocks.interlock(path.toLowerCase(), () ->
        {
            Thread.currentThread().setName(threadName);

            File f = new File(path).getAbsoluteFile().getCanonicalFile();
            File ft = new File(path + staticTempExtension()).getAbsoluteFile().getCanonicalFile();
            if (ft.exists())
                ft.delete();
            writeToFile(ft.getCanonicalPath(), content);
            Files.move(ft.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        });
    }

    public static String readFileAsString(String path) throws Exception
    {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] readFileAsByteArray(String path) throws Exception
    {
        return Files.readAllBytes(Paths.get(path));
    }

    /**
     * Atomically replaces {@code path} with {@code content} and tries to survive an OS crash or power loss.
     *
     * <p>
     * The method:
     * <ol>
     * <li>writes to {@code path + ".tmp"} in the same directory,</li>
     * <li>calls {@link FileChannel#force(boolean)} on the temp file,</li>
     * <li>renames the temp file over the target with {@code ATOMIC_MOVE},</li>
     * <li>calls {@code force(true)} on the parent directory.</li>
     * </ol>
     *
     * @throws IOException
     *             if the write cannot be completed safely
     */
    /*
     * Why this is the safest one can get in pure Java:
     * 
     * - FileChannel.force(true) is the Java equivalent of fsync(2); it asks the OS to flush both file data and metadata
     *   to the physical medium.
     * 
     * - Files.move with ATOMIC_MOVE maps to rename(2) on POSIX file-systems and to MoveFileExW(MOVEFILE_WRITE_THROUGH |
     *   MOVEFILE_REPLACE_EXISTING) on modern Windows, giving atomic replacement semantics.
     * 
     * - force(true) on the parent directory completes the durability contract: the rename is only journal-committed
     *   once the directory entry itself is flushed.
     * 
     * Caveats:
     * 
     * - Not all file systems honor fsync/force (e.g., some network shares, old FAT volumes, exotic FUSE file systems).
     * 
     * - Certain hardware controllers lie about flush—a consumer-grade disk with a volatile write-back cache can still
     *   lose the last few milliseconds of writes after power loss unless it has a capacitor or you disable the cache.
     * 
     * - If you need each individual write() in long-running code to be made durable, open the file with
     *   StandardOpenOption.DSYNC or SYNC; here we only force once at the end for performance.
     * 
     * For most desktop/server deployments on ext4, APFS, NTFS or XFS with proper power-loss protection, the method is
     * the practical upper bound of crash-resilient file replacement one can achieve with Java.
     */
    public static void writeToFileVerySafe(String path, String content) throws Exception
    {
        writeToFileVerySafe(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeToFileVerySafe(String path, byte[] content) throws Exception
    {
        String threadName = Thread.currentThread().getName();
        Thread.currentThread().setName(threadName + " waiting filelock");

        NamedFileLocks.interlock(path.toLowerCase(), () ->
        {
            Thread.currentThread().setName(threadName);

            // --- Sanity checks ---------------------------------------------------
            Path target = Paths.get(path).toAbsolutePath().normalize();
            if (!target.isAbsolute())
                throw new IllegalArgumentException("Target path must be absolute");
            Path dir = target.getParent();
            if (dir == null)
                throw new IOException("Target must reside in a directory");

            Path tmp = dir.resolve(target.getFileName() + Util.staticTempExtension());

            // --- 1. Remove any stale temp file ----------------------------------
            Files.deleteIfExists(tmp);

            // --- 2. Write & fsync the temp file ---------------------------------
            try (FileChannel fc = FileChannel.open(tmp,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING))
            {
                ByteBuffer buf = ByteBuffer.wrap(content);
                while (buf.hasRemaining())
                    fc.write(buf);
                fc.force(true); // data + metadata
            }
            catch (IOException e)
            {
                // Clean up on failure
                Files.deleteIfExists(tmp);
                throw e;
            }

            // --- 3. Atomically replace the target -------------------------------
            try
            {
                Files.move(tmp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException | UnsupportedOperationException ex)
            {
                // Windows before JDK 16 cannot combine ATOMIC_MOVE + REPLACE_EXISTING.
                // Fallback: delete target first, then atomic-move.
                Files.deleteIfExists(target);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            }

            // --- 4. fsync the directory so the rename itself is durable ---------
            try
            {
                try (FileChannel dirFc = FileChannel.open(dir, StandardOpenOption.READ))
                {
                    dirFc.force(true); // metadata only would suffice, but true is portable
                }
            }
            catch (IOException | UnsupportedOperationException e)
            {
                // On Windows: expected — skip directory fsync
            }
        });
    }

    public static String stripProtocol(String url) throws Exception
    {
        String lower = url.toLowerCase();

        if (lower.startsWith("http://"))
        {
            return url.substring("http://".length());
        }
        else if (lower.startsWith("https://"))
        {
            return url.substring("https://".length());
        }
        else
        {
            return url;
        }
    }

    public static String stripAnchor(String href) throws Exception
    {
        if (href != null)
        {
            int k = href.indexOf('#');
            if (k != -1)
                href = href.substring(0, k);
        }

        return href;
    }

    public static String stripParameters(String href) throws Exception
    {
        if (href != null)
        {
            int k = href.indexOf('?');
            if (k != -1)
                href = href.substring(0, k);
        }

        return href;
    }

    public static String stripParametersAndAnchor(String href) throws Exception
    {
        href = stripParameters(href);
        href = stripAnchor(href);
        return href;
    }

    public static Map<String, String> parseUrlParams(String params) throws Exception
    {
        Map<String, String> res = new HashMap<String, String>();

        StringTokenizer st = new StringTokenizer(params, "&");

        while (st.hasMoreTokens())
        {
            String tok = st.nextToken();
            int k = tok.indexOf('=');
            if (k == -1)
            {
                res.put(tok, null);
            }
            else
            {
                res.put(tok.substring(0, k), tok.substring(k + 1));
            }
        }

        return res;
    }

    final private static Random random_gen = new Random();

    public static int random(int min, int max) throws Exception
    {
        return min + random_gen.nextInt(max - min + 1);
    }

    public static <T> Vector<T> randomize(Vector<T> vec) throws Exception
    {
        Vector<T> res = new Vector<T>();

        while (vec.size() > 1)
        {
            int k = random(0, vec.size() - 1);
            res.add(vec.get(k));
            vec.remove(k);
        }

        res.addAll(vec);
        return res;
    }

    public static String despace(String text) throws Exception
    {
        text = text.replace('\u00A0', ' ').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return text.replaceAll("\\s+", " ").trim();
    }

    /*
     * Read set of strings from file.  
     */
    public static Set<String> read_set(String path) throws Exception
    {
        Set<String> ws = new HashSet<>();

        String rs = loadResource(path) + "\n";
        rs = rs.replace("\r", "");
        for (String line : rs.split("\n"))
        {
            line = stripComment(line);
            line = despace(line);
            if (line.equals(" ") || line.length() == 0 || line.startsWith("#"))
                continue;
            ws.add(line);
        }

        return ws;
    }

    public static Set<String> read_set_from_file(String path) throws Exception
    {
        Set<String> ws = new HashSet<String>();

        String rs = readFileAsString(path) + "\n";
        rs = rs.replace("\r", "");
        for (String line : rs.split("\n"))
        {
            line = stripComment(line);
            line = despace(line);
            if (line.equals(" ") || line.length() == 0 || line.startsWith("#"))
                continue;
            ws.add(line);
        }

        return ws;
    }

    public static void write_set_to_file(String path, Set<String> ws) throws Exception
    {
        StringBuilder sb = new StringBuilder();

        for (String s : ws)
        {
            if (sb.length() != 0)
            {
                if (isWindowsOS())
                    sb.append("\r\n");
                else
                    sb.append("\n");
            }
            sb.append(s);
        }

        Util.writeToFileSafe(path, sb.toString());
    }

    /*
     * Read list of strings from file.  
     */
    public static List<String> read_list(String path) throws Exception
    {
        List<String> ws = new ArrayList<>();

        String rs = loadResource(path) + "\n";
        rs = rs.replace("\r", "");
        for (String line : rs.split("\n"))
        {
            line = stripComment(line);
            line = despace(line);
            if (line.equals(" ") || line.length() == 0 || line.startsWith("#"))
                continue;
            ws.add(line);
        }

        return ws;
    }

    public static String stripComment(String s) throws Exception
    {
        int k = s.indexOf('#');
        if (k != -1)
            s = s.substring(0, k);
        return s;
    }

    public static List<String> asList(String s)
    {
        return asList(s, ",");
    }

    public static List<String> asList(String s, String sep)
    {
        if (s == null || s.length() == 0)
            return new ArrayList<String>();
        return Arrays.asList(s.split(sep));
    }

    public static void unused(Object... x)
    {
        // no-op
    }

    public static String prettyJSON(String json)
    {
        try
        {
            // Try as JSONObject
            return new JSONObject(json).toString(4);
        }
        catch (Exception e1)
        {
            // Try as JSONArray
            return new JSONArray(json).toString(4);
        }
    }

    public static byte[] loadResourceAsBytes(String path) throws Exception
    {
        try
        {
            return Files.readAllBytes(Paths.get(Util.class.getClassLoader().getResource(path).toURI()));
        }
        catch (Exception ex)
        {
            throw new Exception("Unable to load resource " + path, ex);
        }
    }

    public static String loadResource(String path) throws Exception
    {
        return new String(loadResourceAsBytes(path), StandardCharsets.UTF_8);
    }

    public static Set<String> setOf(String... strings)
    {
        Set<String> xs = new HashSet<>();
        for (String s : strings)
            xs.add(s);
        return xs;
    }

    public static void noop()
    {

    }

    public static List<String> enumerateOnlyHtmlFiles(String root) throws Exception
    {
        return enumerateFiles(root, Util.setOf(".html"));
    }

    public static List<String> enumerateAnyHtmlFiles(String root) throws Exception
    {
        return enumerateFiles(root, Util.setOf(".html", ".htm", ".shtml", ".shtm"));
    }

    public static List<String> enumerateFiles(String root, Set<String> extensions) throws Exception
    {
        Set<String> fset = new HashSet<String>();
        File f = new File(root);
        if (!f.exists() || !f.isDirectory())
            throw new Exception("Directory " + root + " does not exist");
        enumerateFiles(fset, root, null, extensions);
        List<String> list = new ArrayList<>(fset);
        Collections.sort(list);
        return list;
    }

    private static void enumerateFiles(Set<String> fset, String root, String subpath, Set<String> extensions) throws Exception
    {
        String xroot = root;
        if (subpath != null)
            xroot += File.separator + subpath;
        File xrf = new File(xroot);
        File[] xlist = xrf.listFiles();
        if (xlist == null)
            throw new Exception("Unable to enumerate files under " + xroot);
        for (File xf : xlist)
        {
            if (xf.isDirectory())
            {
                if (subpath == null)
                    enumerateFiles(fset, root, xf.getName(), extensions);
                else
                    enumerateFiles(fset, root, subpath + File.separator + xf.getName(), extensions);
            }
            else if (enumerateFilesMatches(xf.getName(), extensions))
            {
                if (subpath == null)
                    fset.add(xf.getName());
                else
                    fset.add(subpath + File.separator + xf.getName());
            }
        }
    }

    private static boolean enumerateFilesMatches(String fn, Set<String> extensions)
    {
        if (extensions == null)
            return true;

        for (String ext : extensions)
        {
            if (fn.toLowerCase().endsWith(ext.toLowerCase()))
                return true;
        }

        return false;
    }

    public static List<String> enumerateFilesAndDirectories(String root) throws Exception
    {
        Set<String> fset = new HashSet<String>();
        File f = new File(root);
        if (!f.exists() || !f.isDirectory())
            throw new Exception("Directory " + root + " does not exist");
        enumerateFilesAndDirectories(fset, root, null);
        List<String> list = new ArrayList<>(fset);
        Collections.sort(list);
        return list;
    }

    private static void enumerateFilesAndDirectories(Set<String> fset, String root, String subpath) throws Exception
    {
        String xroot = root;

        if (subpath != null)
            xroot += File.separator + subpath;

        File xrf = new File(xroot);
        File[] xlist = xrf.listFiles();
        if (xlist == null)
            throw new Exception("Unable to enumerate files under " + xroot);

        for (File xf : xlist)
        {
            if (subpath == null)
                fset.add(xf.getName());
            else
                fset.add(subpath + File.separator + xf.getName());

            if (xf.isDirectory())
            {
                if (subpath == null)
                    enumerateFilesAndDirectories(fset, root, xf.getName());
                else
                    enumerateFilesAndDirectories(fset, root, subpath + File.separator + xf.getName());
            }
        }
    }


    public static List<String> enumerateDirectories(String root) throws Exception
    {
        Set<String> fset = new HashSet<String>();
        File f = new File(root);
        if (!f.exists() || !f.isDirectory())
            throw new Exception("Directory " + root + " does not exist");
        enumerateDirectories(fset, root, null);
        List<String> list = new ArrayList<>(fset);
        Collections.sort(list);
        return list;
    }

    private static void enumerateDirectories(Set<String> fset, String root, String subpath) throws Exception
    {
        String xroot = root;

        if (subpath != null)
            xroot += File.separator + subpath;

        File xrf = new File(xroot);
        File[] xlist = xrf.listFiles();
        if (xlist == null)
            throw new Exception("Unable to enumerate files under " + xroot);

        for (File xf : xlist)
        {
            if (xf.isDirectory())
            {
                if (subpath == null)
                {
                    fset.add(xf.getName());
                    enumerateFilesAndDirectories(fset, root, xf.getName());
                }
                else
                {
                    fset.add(subpath + File.separator + xf.getName());
                    enumerateFilesAndDirectories(fset, root, subpath + File.separator + xf.getName());
                }
            }
        }
    }
    
    
    
    public static String getFileDirectory(String filepath) throws Exception
    {
        File d = new File(filepath).getParentFile().getAbsoluteFile();
        return d.getCanonicalPath();
    }

    public static String extractFileName(String filePath)
    {
        return new File(filePath).getName();
    }

    public static String uuid()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String uuid2()
    {
        return uuid() + uuid();
    }

    // Reserved device names in Windows (case-insensitive)
    private static final String[] WINDOWS_RESERVED_NAMES = {
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    // Illegal characters in Windows file names
    @SuppressWarnings("unused")
    private static final char[] WINDOWS_ILLEGAL_CHARS = {
            '\\', '/', ':', '*', '?', '"', '<', '>', '|'
    };

    // Characters that are problematic even if technically allowed
    @SuppressWarnings("unused")
    private static final char[] LINUX_DISCOURAGED_CHARS = {
            '*', '?', '|', '>', '<', ':', '"', '\\'
    };

    public static boolean isWindowsOS()
    {
        return File.separatorChar == '\\';
    }

    public static boolean isReservedFileName(String fn, boolean anyos)
    {
        if (fn.equals(".") || fn.equals(".."))
            return true;

        if (isWindowsOS() || anyos)
        {
            for (String reserved : WINDOWS_RESERVED_NAMES)
            {
                // COM3
                if (fn.equalsIgnoreCase(reserved))
                    return true;

                // COM3.txt
                if (fn.toLowerCase().startsWith(reserved.toLowerCase() + "."))
                    return true;
            }
        }

        return false;
    }

    public static String flipProtocol(String s) throws Exception
    {
        String lower = s.toLowerCase();

        if (lower.startsWith("http://"))
        {
            return "https://" + s.substring("http://".length());
        }
        else if (lower.startsWith("https://"))
        {
            return "http://" + s.substring("https://".length());
        }
        else
        {
            return s;
        }
    }

    /*
     * count number of times @ss occurs within @s
     */
    public static int countOccurrences(String s, String ss)
    {
        if (ss.isEmpty())
            return 0; // Avoid infinite loop on empty substring

        int count = 0;
        int index = 0;
        while ((index = s.indexOf(ss, index)) != -1)
        {
            count++;
            index += ss.length();
        }

        return count;
    }

    /*
     * extract substring located between two delimiters
     */
    public static String extractBetween(String s, String delimiter1, String delimiter2)
    {
        int start = s.indexOf(delimiter1);
        if (start == -1)
            return null; // delimiter1 not found
        start += delimiter1.length();

        int end = s.indexOf(delimiter2, start);
        if (end == -1)
            return null; // delimiter2 not found after delimiter1

        return s.substring(start, end);
    }

    public static void out(String s)
    {
        System.out.println(s);
        System.out.flush();
    }

    public static void err(String s)
    {
        System.out.flush();
        System.err.println(s);
        System.err.flush();
    }

    public static void deleteFilesInDirectory(String dirPath, String pattern) throws IOException
    {
        Path dir = Paths.get(dirPath);

        if (!Files.isDirectory(dir))
            throw new IllegalArgumentException("Provided path is not a directory: " + dirPath);

        DirectoryStream.Filter<Path> filter = entry -> Files.isRegularFile(entry) &&
                FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(entry.getFileName());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter))
        {
            for (Path entry : stream)
                Files.delete(entry);
        }
    }

    public static boolean deleteDirectoryTree(String root) throws Exception
    {
        return deleteDirectoryTree(new File(root).getCanonicalFile());
    }

    public static boolean deleteDirectoryTree(File root) throws Exception
    {
        return deleteDirectoryTree(root, true);
    }

    private static boolean deleteDirectoryTree(File root, boolean istop) throws Exception
    {
        if (root != null)
            root = root.getCanonicalFile();

        if (root == null || !root.exists())
            return true; // Nothing to delete

        if (istop && !root.isDirectory())
            return false;

        if (root.isDirectory())
        {
            File[] files = root.listFiles();

            if (files != null)
            {
                for (File f : files)
                {
                    // Abort if any delete fails
                    if (!deleteDirectoryTree(f, false))
                        return false;
                }
            }
        }

        if (Config.False)
        {
            out("Pseudo-deleting " + root.getCanonicalPath());
            return true;
        }
        else
        {
            // Delete file or empty directory
            if (!root.exists())
                return true;

            try
            {
                Files.delete(root.toPath());
                return true;
            }
            catch (DirectoryNotEmptyException ex)
            {
                Util.err(String.format("Unable to delete %s, directory is not empty", root.getCanonicalPath()));
                return false;
            }
            catch (AccessDeniedException ex)
            {
                Util.err(String.format("Unable to delete %s, access denied", root.getCanonicalPath()));
                return false;
            }
            catch (NoSuchFileException ex)
            {
                if (!root.exists())
                    return true;
                Util.err(String.format("Unable to delete %s, no such file, cause: %s", root.getCanonicalPath(),
                        ex.getLocalizedMessage()));
                return false;
            }
            catch (IOException ex)
            {
                // This gives the actual reason
                // ex.printStackTrace();
                Util.err(String.format("Unable to delete %s, cause: %s", root.getCanonicalPath(), ex.getLocalizedMessage()));
                return false;
            }
        }
    }

    public static void deleteGuidFilesInDirectory(String dirPath, String pattern) throws IOException
    {
        Path dir = Paths.get(dirPath);

        if (!Files.isDirectory(dir))
            throw new IllegalArgumentException("Provided path is not a directory: " + dirPath);

        // Escape the literal parts and identify GUID placeholder
        String regex = Pattern.quote(pattern)
                .replace("<GUID>", "([A-Fa-f0-9]{32})")
                .replace("<UCGUID>", "([A-F0-9]{32})")
                .replace("<LCGUID>", "([a-f0-9]{32})");

        Pattern compiledPattern = Pattern.compile(regex);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir))
        {
            for (Path entry : stream)
            {
                if (Files.isRegularFile(entry))
                {
                    String fileName = entry.getFileName().toString();
                    Matcher matcher = compiledPattern.matcher(fileName);
                    if (matcher.matches())
                        Files.delete(entry);
                }
            }
        }
    }

    public static String timeNow()
    {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formatted = now.format(formatter);
        return formatted;
    }

    public static String urlHost(String url) throws Exception
    {
        URL x = new URL(url);
        return x.getHost();
    }

    public static <T> boolean containsIdentity(Collection<T> coll, T object)
    {
        for (T x : coll)
        {
            if (x == object)
                return true;
        }

        return false;
    }

    public static void sleep(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException ex)
        {
            noop();
        }
    }

    public static String resolveURL(String baseURL, String relativeURL) throws Exception
    {
        if (baseURL != null)
            baseURL = baseURL.trim();

        if (relativeURL != null)
            relativeURL = relativeURL.trim();

        if (relativeURL != null)
        {
            relativeURL = encodeFragment(relativeURL);
            /* Windows backslashes in some old HTML files, e.g. ..\index.html */
            relativeURL = relativeURL.replace("\\", "/");
        }

        if (baseURL == null || baseURL.isEmpty())
            return relativeURL;

        // Handle protocol-relative URLs (e.g., //cdn.example.com/script.js)
        if (relativeURL != null && relativeURL.startsWith("//"))
        {
            if (baseURL != null && !baseURL.isEmpty())
            {
                URI baseUri = new URI(baseURL);
                String scheme = baseUri.getScheme();
                if (scheme != null && !scheme.isEmpty())
                    return scheme + ":" + relativeURL;
            }

            // If baseURL is missing or lacks scheme, fallback (e.g., assume "https:")
            return "https:" + relativeURL;
        }

        // Handle archive.org URLs with embedded full URLs in the path
        final String archive_org_https_web = "https://web.archive.org/web/";
        if (baseURL.startsWith(archive_org_https_web))
        {
            int schemeIndex = baseURL.indexOf("http://", archive_org_https_web.length());
            if (schemeIndex == -1)
                schemeIndex = baseURL.indexOf("https://", archive_org_https_web.length());

            if (schemeIndex != -1)
            {
                String archivePrefix = baseURL.substring(0, schemeIndex);
                String archivedURL = baseURL.substring(schemeIndex);

                URI archivedBase = new URI(archivedURL);
                URI resolved = archivedBase.resolve(relativeURL);

                return archivePrefix + resolved.toString();
            }
        }

        // Default case
        try
        {
            URI base = new URI(baseURL);
            URI resolved = base.resolve(relativeURL);
            return resolved.toString();
        }
        catch (Exception ex)
        {
            throw ex;
        }
    }

    private static String encodeFragment(String url) throws Exception
    {
        int hashIndex = url.indexOf('#');
        if (hashIndex == -1)
            return url;

        String beforeFragment = url.substring(0, hashIndex);
        String fragment = url.substring(hashIndex + 1);

        // Encode using UTF-8 and percent-encode
        try
        {
            String encodedFragment = URLEncoder.encode(fragment, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20"); // URLEncoder encodes spaces as +, but in URI they should be %20
            return beforeFragment + "#" + encodedFragment;
        }
        catch (UnsupportedEncodingException e)
        {
            throw new Exception("UTF-8 not supported", e);
        }
    }

    public static boolean isSameURL(String url1, String url2)
    {
        try
        {
            URI uri1 = new URI(url1);
            URI uri2 = new URI(url2);

            return isSameURL(uri1, uri2);
        }
        catch (URISyntaxException e)
        {
            return false;
        }
    }

    public static boolean isSameURL(URI uri1, URI uri2)
    {
        // Compare scheme and host case-insensitively
        if (!equalsIgnoreCase(uri1.getScheme(), uri2.getScheme()))
            return false;

        if (!equalsIgnoreCase(uri1.getHost(), uri2.getHost()))
            return false;

        // Compare port (default ports need normalization if desired)
        if (uri1.getPort() != uri2.getPort())
            return false;

        // Compare path, query, and fragment case-sensitively
        if (!Objects.equals(uri1.getPath(), uri2.getPath()))
            return false;

        if (!Objects.equals(uri1.getQuery(), uri2.getQuery()))
            return false;

        if (!Objects.equals(uri1.getFragment(), uri2.getFragment()))
            return false;

        return true;
    }

    public static boolean isAbsoluteURL(String url)
    {
        try
        {
            if (url == null || url.trim().isEmpty())
                return false;

            URI uri = new URI(url.trim());

            if (uri.getScheme() == null || uri.getHost() == null)
                return false;

            switch (uri.getScheme().toLowerCase())
            {
            case "http":
            case "https":
                break;

            default:
                throw new RuntimeException("Unexpected scheme " + uri.getScheme() + "://");

            }

            return 0 != uri.getHost().trim().length();
        }
        catch (URISyntaxException e)
        {
            return false;
        }
    }

    private static boolean equalsIgnoreCase(String a, String b)
    {
        return (a == null && b == null) || (a != null && a.equalsIgnoreCase(b));
    }

    public static List<String> eliminateDuplicates(List<String> list)
    {
        List<String> rlist = new ArrayList<>();
        Set<String> rset = new HashSet<>();
        boolean hasNull = false;

        for (String s : list)
        {
            if (s != null)
            {
                if (!rset.contains(s))
                    rlist.add(s);
                rset.add(s);
            }
            else
            {
                if (!hasNull)
                    rlist.add(s);
                hasNull = true;
            }
        }

        return rlist;
    }

    public static String getFileExtension(String path)
    {
        if (path == null || path.isEmpty())
            return null;

        // Normalize separators to make it OS-independent
        path = path.replace('\\', '/');

        // Extract the last path component
        int lastSlash = path.lastIndexOf('/');
        String fileName = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;

        // Find the last dot in the file name (not in directories)
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1)
            return null;

        return fileName.substring(lastDot + 1);
    }

    public static String getFileExtensionFromURL(String path)
    {
        if (path == null || path.isEmpty())
            return null;

        // Strip query string and fragment
        int q = path.indexOf('?');
        int h = path.indexOf('#');
        int end = path.length();
        if (q >= 0 && h >= 0)
            end = Math.min(q, h);
        else if (q >= 0)
            end = q;
        else if (h >= 0)
            end = h;
        path = path.substring(0, end);

        // Normalize separators to make it OS-independent
        path = path.replace('\\', '/');

        // Extract the last path component
        int lastSlash = path.lastIndexOf('/');
        String fileName = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;

        // Find the last dot in the file name (not in directories)
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1)
            return null;

        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT); // Optional normalization
    }

    public static String trimWithNBSP(String s)
    {
        if (s == null || s.isEmpty())
        {
            return s;
        }

        int length = s.length();
        int start = 0;
        int end = length - 1;

        // Trim leading whitespace and NBSP
        while (start <= end && isWhitespaceOrNBSP(s.charAt(start)))
        {
            start++;
        }

        // Trim trailing whitespace and NBSP
        while (end >= start && isWhitespaceOrNBSP(s.charAt(end)))
        {
            end--;
        }

        return start > end ? "" : s.substring(start, end + 1);
    }

    // Helper method to check if a character is a space, tab, NBSP, etc.
    private static boolean isWhitespaceOrNBSP(char c)
    {
        return Character.isWhitespace(c) || c == '\u00A0';
    }

    public static void safeClose(Closeable c)
    {
        try
        {
            if (c != null)
                c.close();
        }
        catch (Exception ex)
        {
            err("Failed to close " + c.getClass().getName());
        }
    }

    public static void safeClose(AutoCloseable c)
    {
        try
        {
            if (c != null)
                c.close();
        }
        catch (Exception ex)
        {
            err("Failed to close " + c.getClass().getName());
        }
    }

    public static String tempExtension()
    {
        return tempExtension(5);
    }

    public static String tempExtension(int n)
    {
        return ".tmp~" + RandomString.rs(n);
    }

    private static String tempExtension = null;

    public static synchronized String staticTempExtension()
    {
        if (tempExtension == null)
            tempExtension = tempExtension(5);
        return tempExtension;
    }

    /**
     * Utility for calculating how “deep” a path is, measured as the number of
     * name elements that appear after the root of the file-system (or Windows
     * drive/UNC share) and including the file itself.
     *
     * <pre>
     * F:\WINAPPS\LJExport\journals\krylov\pages\2003\10\704750.html  -> 8
     * /LJExport/journals/krylov/pages/2003/10/704750.html            -> 7
     * C:\                       -> 0
     * /                          -> 0
     * \\srv\share\dir\file.txt   -> 2   (dir + file.txt)
     * </pre>
     *
     * The logic is completely platform-neutral—it works the same on Windows,
     * Linux, or macOS, even if the path string itself comes from a different
     * platform.
     */

    /**
     * Returns the depth (number of name elements) of the supplied path string.
     *
     * @param rawPath any absolute or relative path, using ‘/’ or ‘\’
     * @return number of name elements after the root; 0 for “just root”
     * @throws IllegalArgumentException if {@code rawPath} is null/blank
     */
    public static int filePathDepth(String rawPath)
    {
        Objects.requireNonNull(rawPath, "path must not be null");
        String p = rawPath.trim();
        if (p.isEmpty())
            throw new IllegalArgumentException("path must not be blank");

        // 1. Normalise separators to ‘/’ so we only split once.
        p = p.replace('\\', '/');

        // 2. Remove a Windows drive-letter prefix, e.g. “C:” or “C:/”.
        if (p.length() >= 2 && Character.isLetter(p.charAt(0)) && p.charAt(1) == ':')
        {
            p = p.substring(2);
        }

        // 3. Handle UNC paths:  //server/share/dir/…
        if (p.startsWith("//"))
        {
            // Skip ‘//server/share’
            int firstSlash = p.indexOf('/', 2);
            int secondSlash = (firstSlash == -1) ? -1 : p.indexOf('/', firstSlash + 1);
            if (secondSlash == -1)
            {
                // The path is only “//server/share” → depth 0
                return 0;
            }
            p = p.substring(secondSlash);
        }

        // 4. Remove leading & trailing ‘/’ so split() behaves nicely.
        p = p.replaceAll("^/+", "").replaceAll("/+$", "");

        // 5. Empty string at this point means “root” (depth 0).
        if (p.isEmpty())
        {
            return 0;
        }

        // 6. Everything that remains are name elements separated by ‘/’.
        return p.split("/").length;
    }
}