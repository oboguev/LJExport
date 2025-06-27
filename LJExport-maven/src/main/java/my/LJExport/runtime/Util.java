package my.LJExport.runtime;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.Vector;

import org.json.JSONObject;
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
        StringBuilder sb = new StringBuilder();

        if (beginsWith(url, "http://", sb))
        {
            // stripped http prefix
            url = sb.toString();
        }
        else if (beginsWith(url, "https://", sb))
        {
            // stripped https prefix
            url = sb.toString();
        }

        return url;
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
        if (s.startsWith("http://"))
            return "https://" + Util.stripStart(s, "http://");
        else if (s.startsWith("https://"))
            return "http://" + Util.stripStart(s, "https://");
        else if (s.startsWith("HTTP://"))
            return "HTTPS://" + Util.stripStart(s, "HTTP://");
        else if (s.startsWith("HTTPS://"))
            return "HTTP://" + Util.stripStart(s, "HTTPS://");
        else
            return s;
    }
}