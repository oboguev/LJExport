package my.LJExport.runtime.links.util;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.url.URLCodec;

public class LinkFilepath
{
    public static String encodePathComponents(String ref)
    {
        StringBuilder sb = new StringBuilder();

        for (String pc : Util.asList(ref, "/"))
        {
            if (pc.length() != 0)
            {
                if (sb.length() != 0)
                    sb.append("/");
                sb.append(URLCodec.encode(pc));
            }
        }

        return sb.toString();
    }

    public static String decodePathComponents(String ref)
    {
        StringBuilder sb = new StringBuilder();

        for (String pc : Util.asList(ref, "/"))
        {
            if (pc.length() != 0)
            {
                if (sb.length() != 0)
                    sb.append("/");
                sb.append(URLCodec.decode(pc));
            }
        }

        return sb.toString();
    }
    
    public static String getMediaFileExtension(String fn)
    {
        int dotIndex = fn.lastIndexOf('.');

        // no extension or dot is at the end
        if (dotIndex == -1 || dotIndex == fn.length() - 1)
            return null;

        String ext = fn.substring(dotIndex + 1);
        if (ext.length() == 0 || ext.length() > 4)
            ext = null;
        
        // ### xhtml shtml
        
        return ext;
    }
    
    /* ======================================================================== */
    
    private static final int MaxFilePathComponentLength = 90;
    private static final int MaxFilePathLentgh = 230;

    public static String buildFilePath(String linksDir, String href) throws Exception
    {
        URL url = new URL(href);

        List<StringBuilder> list = new ArrayList<>();

        StringBuilder sb = new StringBuilder(linksDir + File.separator);
        sb.append(url.getHost());
        int port = url.getPort();
        if (port > 0 && port != 80 && port != 443)
            sb.append("__" + port);
        list.add(sb);
        sb = null;

        if (url.getHost().equals("imgprx.livejournal.net"))
        {
            int folder = (int) (Math.random() * 100);
            if (folder >= 100)
                folder = 99;
            list.add(new StringBuilder(String.format("x-%02d", folder)));
            list.add(new StringBuilder("x-" + Util.uuid()));
        }
        else
        {
            for (String pc : Util.asList(url.getPath(), "/"))
            {
                if (pc.length() != 0)
                {
                    sb = new StringBuilder(pc);
                    list.add(sb);
                }
            }

            String ext = (sb == null) ? null : getMediaFileExtension(sb.toString());

            String query = url.getQuery();
            if (query != null && query.length() != 0)
            {
                if (sb == null)
                {
                    // https://simg.sputnik.ru/?key=671d8d631c860987add28ec9742b240a2b6cac18
                    sb = new StringBuilder();
                    list.add(sb);
                }

                sb.append("?" + query);
                // reappend extenstion
                if (ext != null && ext.length() != 0 && ext.length() <= 4)
                    sb.append("." + ext);
            }

            if (sb == null)
            {
                // https://simg.sputnik.ru
                sb = new StringBuilder("[unnamed-root]");
                list.add(sb);
            }
        }

        StringBuilder path = new StringBuilder();
        for (StringBuilder x : list)
        {
            if (path.length() == 0)
            {
                path.append(x.toString());
            }
            else
            {
                path.append(File.separator);
                path.append(makeSanePathComponent(x.toString()));
            }
        }

        String result = path.toString();

        if (result.length() >= MaxFilePathLentgh)
        {
            sb = list.get(0);
            list.clear();
            list.add(sb);

            list.add(new StringBuilder("@@@"));

            int folder = (int) (Math.random() * 100);
            if (folder >= 100)
                folder = 99;
            list.add(new StringBuilder(String.format("x-%02d", folder)));

            list.add(sb = new StringBuilder("x-" + Util.uuid()));

            String ext = getMediaFileExtension(getLastPathComponent(url));
            if (ext != null)
                sb.append("." + ext);

            path = new StringBuilder();
            for (StringBuilder x : list)
            {
                if (path.length() != 0)
                    path.append(File.separator);
                path.append(x.toString());
            }

            result = path.toString();
        }

        return result;
    }

    public static String makeSanePathComponent(String component) throws Exception
    {
        /*
         * Unpack %xx sequences -> unicode.
         * Sometimes it has double or even triple encoding.
         */
        String fn = URLCodec.fullyDecodeMixed(component);

        /*
         * Encode reserved characters to URL representation
         */
        fn = URLCodec.encodeFilename(fn);

        /* Fix dangerous trailing . or space (Windows will strip them silently) */
        fn = escapeTrailingDotsAndSpaces(fn);
        fn = escapeLeadingSpacesAndUnicode(fn);

        /*
         * If reserved file name, mangle it
         */
        if (Util.isReservedFileName(fn, true))
            return "x-" + Util.uuid() + "_" + fn;

        /*
         * If name is too long
         */
        if (fn.length() > MaxFilePathComponentLength)
        {
            String ext = getMediaFileExtension(fn);
            if (ext == null)
                return "x-" + Util.uuid();
            else
                return "x-" + Util.uuid() + "." + ext;
        }

        return fn;
    }

    public static String escapeTrailingDotsAndSpaces(String s)
    {
        int i = s.length();

        while (i > 0 && isProblematicTrailingChar(s.charAt(i - 1)))
            i--;

        if (i == s.length())
            return s;

        if (i == 0)
            return "x-" + Util.uuid();

        StringBuilder out = new StringBuilder(s.substring(0, i));
        for (int j = i; j < s.length(); j++)
            out.append(String.format("%%%02X", (int) s.charAt(j)));
        return out.toString();
    }

    private static boolean isProblematicTrailingChar(char c)
    {
        return c == '.' || c == ' ' || c == '\u0009' || c == '\u00A0' || (c >= '\u2000' && c <= '\u200F');
    }

    public static String escapeLeadingSpacesAndUnicode(String s)
    {
        int i = 0;
        while (i < s.length() && isProblematicLeadingChar(s.charAt(i)))
            i++;

        if (i == 0)
            return s;

        if (i == s.length())
            return "x-" + Util.uuid(); // string was entirely invisible chars

        StringBuilder out = new StringBuilder();
        for (int j = 0; j < i; j++)
            out.append(String.format("%%%02X", (int) s.charAt(j)));
        out.append(s.substring(i));

        return out.toString();
    }

    private static boolean isProblematicLeadingChar(char c)
    {
        return c == ' ' || c == '\u0009' || c == '\u00A0' || (c >= '\u2000' && c <= '\u200F');
    }

    private static String getLastPathComponent(URL url)
    {
        String path = url.getPath();

        if (path == null || path.isEmpty())
            return "";

        // Trailing slash means "directory", last component is empty
        if (path.endsWith("/"))
            return "";

        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
