package my.LJExport.runtime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

import org.json.JSONArray;

public class Util
{
    public static char lastChar(String s) throws Exception
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

        if (ld < ls && site.charAt(ls - ld - 1) == '.')
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
    static boolean beginsWithPath(String url, String path, StringBuilder sb) throws Exception
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

    public static void writeToFileSafe(String path, String content) throws Exception
    {
        File f = new File(path).getAbsoluteFile().getCanonicalFile();
        File ft = new File(path + ".tmp").getAbsoluteFile().getCanonicalFile();
        if (ft.exists())
            ft.delete();
        writeToFile(ft.getCanonicalPath(), content);
        Files.move(ft.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void writeToFileSafe(String path, byte[] content) throws Exception
    {
        File f = new File(path).getAbsoluteFile().getCanonicalFile();
        File ft = new File(path + ".tmp").getAbsoluteFile().getCanonicalFile();
        if (ft.exists())
            ft.delete();
        writeToFile(ft.getCanonicalPath(), content);
        Files.move(ft.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static String readFileAsString(String path) throws Exception
    {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, StandardCharsets.UTF_8);
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
        text = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return text.replaceAll("\\s+", " ").trim();
    }

    /*
     * Read list of strings from file.  
     */
    public static Set<String> read_set(String path) throws Exception
    {
        Set<String> ws = new HashSet<String>();

        String rs = loadResource(path) + "\n";
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

    public static List<String> enumerateFiles(String root) throws Exception
    {
        Set<String> fset = new HashSet<String>();
        File f = new File(root);
        if (!f.exists() || !f.isDirectory())
            throw new Exception("Directory " + root + " does not exist");
        enumerateFiles(fset, root, null);
        List<String> list = new ArrayList<>(fset);
        Collections.sort(list);
        return list;
    }

    private static void enumerateFiles(Set<String> fset, String root, String subpath) throws Exception
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
                    enumerateFiles(fset, root, xf.getName());
                else
                    enumerateFiles(fset, root, subpath + File.separator + xf.getName());
            }
            else if (xf.getName().toLowerCase().endsWith(".html"))
            {
                if (subpath == null)
                    fset.add(xf.getName());
                else
                    fset.add(subpath + File.separator + xf.getName());
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

    public static boolean isReservedFileName(String fn)
    {
        if (fn.equals(".") || fn.equals(".."))
            return true;

        if (isWindowsOS())
        {
            for (String reserved : WINDOWS_RESERVED_NAMES)
            {
                if (reserved.equalsIgnoreCase(fn))
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
            return root.delete();
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

    public static String resolveURL(String baseURL, String relativeURL) throws URISyntaxException
    {
        if (baseURL != null)
            baseURL = baseURL.trim();

        if (relativeURL != null)
            relativeURL = relativeURL.trim();

        if (baseURL == null || baseURL.isEmpty())
            return relativeURL;

        URI base = new URI(baseURL);
        URI resolved = base.resolve(relativeURL);
        return resolved.toString();
    }

    public static boolean isSameURL(String url1, String url2)
    {
        try
        {
            URI uri1 = new URI(url1);
            URI uri2 = new URI(url2);

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
        catch (URISyntaxException e)
        {
            return false;
        }
    }

    private static boolean equalsIgnoreCase(String a, String b)
    {
        return (a == null && b == null) || (a != null && a.equalsIgnoreCase(b));
    }
}