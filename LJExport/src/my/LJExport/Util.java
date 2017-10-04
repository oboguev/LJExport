package my.LJExport;

import java.util.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

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

    public static Vector<String> sort(Set<String> set) throws Exception
    {
        Vector<String> vs = new Vector<String>();
        for (String s : set)
            vs.add(s);
        Collections.sort(vs);
        return vs;
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
     * Check if URL belongs to the site's server
     */
    static boolean isServerUrl(String url) throws Exception
    {
        if (isServerUrl(url, Config.Site))
            return true;

        for (String site : Config.AllowedUrlSites)
        {
            if (!site.equalsIgnoreCase(Config.Site) && isServerUrl(url, site))
                return true;
        }

        return false;
    }

    static boolean isServerUrl(String url, String site) throws Exception
    {
        int k;

        url = stripProtocol(url);

        // strip port number and everything afterwards
        k = url.indexOf(':');
        if (k != -1)
            url = url.substring(0, k);

        // strip local URI and everything afterwards
        k = url.indexOf('/');
        if (k != -1)
            url = url.substring(0, k);

        // strip parameters
        k = url.indexOf('?');
        if (k != -1)
            url = url.substring(0, k);

        // strip anchor
        k = url.indexOf('#');
        if (k != -1)
            url = url.substring(0, k);

        int lu = url.length();
        int ls = site.length();
        if (lu < ls)
        {
            return false;
        }
        else if (lu == ls)
        {
            return url.equalsIgnoreCase(site);
        }
        else
        {
            return url.substring(lu - ls).equalsIgnoreCase(site) &&
                   url.charAt(lu - ls - 1) == '.' &&
                   url.charAt(0) != '.';
        }
    }

    /*
     * Check if @url is what should be after a login.
     */
    static boolean isValidPostLoginUrl(String url) throws Exception
    {
        if (isJournalUrl(url))
            return true;
        url = stripProtocol(url);
        url = stripLastChar(url, '/');
        if (url.equalsIgnoreCase(Config.Site))
            return true;
        if (url.equalsIgnoreCase("www." + Config.Site))
            return true;
        return false;
    }

    /*
     * Check if @url belongs to the user's journal. 
     * If yes, strip the journal prefix, store the result in @sb and return @true. 
     * If no, return @false and the value of @sb is unpredictable.
     */
    static boolean isJournalUrl(String url) throws Exception
    {
        return isJournalUrl(url, new StringBuilder());
    }

    static boolean isJournalUrl(String url, StringBuilder sb) throws Exception
    {
        if (url.startsWith("/"))
        {
            // e.g. /2532366.html?thread=474097422&format=light
            sb.setLength(0);
            sb.append(url.substring(1));
            return true;
        }

        url = stripProtocol(url);

        String pattern = Config.MangledUser + "." + Config.Site;
        if (beginsWithPath(url, pattern, sb))
            return true;

        pattern = Config.Site + "/users/" + Config.User;
        if (beginsWithPath(url, pattern, sb))
            return true;
        if (beginsWithPath(url, "www." + pattern, sb))
            return true;

        pattern = "users." + Config.Site + "/" + Config.User;
        if (beginsWithPath(url, pattern, sb))
            return true;

        return false;
    }

    /*
     * Check if @url belongs is a record in the user's journal. 
     * If yes, strip the journal prefix, store the result in @sb and return @true.
     * If no, return @false and the value of @sb is unpredictable.
     */
    static boolean isJournalRecordUrl(String url, StringBuilder sb) throws Exception
    {
        if (!isJournalUrl(url, sb))
            return false;
        url = stripParameters(sb.toString());
        int len = url.length();
        if (len == 0)
            return false;
        int k = 0;
        while (k < len && Character.isDigit(url.charAt(k)))
            k++;
        if (k == 0)
            return false;
        url = url.toLowerCase();
        if (url.substring(k).equalsIgnoreCase(".html"))
        {
            sb.setLength(0);
            sb.append(url.toLowerCase());
            return true;
        }

        return false;
    }

    /*
     * Check if @url begins with @prefix.
     * If yes, strip the prefix, store the result in @sb and return @true.
     * If no, return @false and the value of @sb is unpredictable.
     */
    static boolean beginsWith(String url, String prefix, StringBuilder sb) throws Exception
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
        throw new Exception("Unable to create directory " + path);
    }

    public static void writeToFile(String path, String content) throws Exception
    {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        FileOutputStream fos = new FileOutputStream(path);
        fos.write(bytes);
        fos.flush();
        fos.close();
    }

    public static void writeToFileSafe(String path, String content) throws Exception
    {
        File f = new File(path);
        File ft = new File(path + ".tmp");
        if (f.exists())
            f.delete();
        writeToFile(path + ".tmp", content);
        ft.renameTo(f);
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

    public static boolean isLoginPageURL(String url) throws Exception
    {
        url = stripProtocol(url);
        StringBuilder sb = new StringBuilder();
        if (beginsWith(url, Config.Site, sb) ||
            beginsWith(url, "www." + Config.Site, sb))
        {
            url = sb.toString();
            return url.equals("/login.bml");
        }
        else
        {
            return false;
        }
    }

    public static boolean isLogoutURL(String url, StringBuilder sb) throws Exception
    {
        url = stripProtocol(url);
        if (url.startsWith("/logout.bml?"))
        {
            // got it
        }
        else if (beginsWith(url, Config.Site, sb) ||
                 beginsWith(url, "www." + Config.Site, sb))
        {
            url = sb.toString();
            if (!url.startsWith("/logout.bml?"))
                return false;
        }
        else
        {
            return false;
        }

        sb.setLength(0);
        sb.append("http://" + Config.Site + url);
        return true;
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
    public static Set<String> read_list(Object obj, String path) throws Exception
    {
        Set<String> ws = new HashSet<String>();
        String line;

        path = relative_path(obj, path);

        Class cls = null;
        if (obj instanceof Class)
            cls = (Class) obj;
        else
            cls = obj.getClass();

        try (InputStream ris = cls.getClassLoader().getResourceAsStream(path);
             InputStreamReader isr = new InputStreamReader(ris, "UTF-8");
             BufferedReader bufferedReader = new BufferedReader(isr))
        {
            while (null != (line = bufferedReader.readLine()))
            {
                line = stripComment(line);
                line = despace(line);
                if (line.equals(" ") || line.length() == 0)
                    continue;
                ws.add(line);
            }
        }

        return ws;
    }

    public static String relative_path(Object obj, String path) throws Exception
    {
        Class cls = null;
        if (obj instanceof Class)
            cls = (Class) obj;
        else
            cls = obj.getClass();

        String root = cls.getName().replace('.', '/');
        int k = root.lastIndexOf('/');
        if (k == -1)
            throw new Exception("Unexpected class name: " + cls.getName());
        root = root.substring(0, k);
        return root + "/" + path;
    }

    public static String stripComment(String s) throws Exception
    {
        int k = s.indexOf('#');
        if (k != -1)
            s = s.substring(0, k);
        return s;
    }
}
