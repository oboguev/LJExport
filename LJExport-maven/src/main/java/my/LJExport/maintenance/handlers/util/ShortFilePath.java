package my.LJExport.maintenance.handlers.util;

import java.net.URI;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.url.URLCodec;

public class ShortFilePath
{
    private final int MaxRelativeFilePath;

    public ShortFilePath(int MaxRelativeFilePath)
    {
        this.MaxRelativeFilePath = MaxRelativeFilePath;
    }

    public String makeShorterFileRelativePath(String rel) throws Exception
    {
        String[] components = rel.split("/");

        String host = components[0];

        for (int k = 1; k < components.length; k++)
            components[k] = URLCodec.fullyDecodeMixed(components[k]);

        String pc1 = components.length <= 1 ? null : components[1];
        String pc2 = components.length <= 2 ? null : components[2];
        // String pc3 = components.length <= 3 ? null : components[3];
        String pclast = components[components.length - 1];

        String newrel = null;

        if (host.startsWith("sun") && host.endsWith(".userapi.com") ||
                host.startsWith("scontent") && host.endsWith(".fbcdn.net"))
        {
            URI uri = new URI(pclast);

            if (uri.getPath() != null)
            {
                String[] xc = components.clone();
                xc[xc.length - 1] = uri.getPath();
                newrel = recompose(xc, "/");
                if (newrel.length() <= MaxRelativeFilePath)
                    return newrel;
            }
        }

        if ((host.equals("substackcdn.com") || host.equals("cdn.substack.com")) &&
                components.length >= 5 && pc1.equals("image") && pc2.equals("fetch"))
        {
            String xclast = null;

            if (pclast.startsWith("https://"))
            {
                xclast = Util.stripStart(pclast, "https://");
            }
            else if (pclast.startsWith("http://"))
            {
                xclast = Util.stripStart(pclast, "http://");
            }

            if (xclast != null)
            {
                String[] xc = concat(host, "image", "fetch");
                xc = concat(xc, xclast.split("/"));
                xc = saneExceptFirst(xc, true);
                newrel = recompose(xc, "/");
                if (newrel.length() <= MaxRelativeFilePath)
                    return newrel;

            }
        }

        if (host.toLowerCase().endsWith(".yimg.com"))
        {
            String[] pcs = extractRemainderAfter(components, "http:");
            if (pcs == null)
                pcs = extractRemainderAfter(components, "https:");
            if (pcs != null && pcs.length >= 1)
            {
                String[] xc = concat(host, pcs);
                xc = saneExceptFirst(xc, true);
                newrel = recompose(xc, "/");
                if (newrel.length() <= MaxRelativeFilePath)
                    return newrel;
            }
        }

        if (host.toLowerCase().equals("cdn.vox-cdn.com") && components.length >= 8)
        {
            if (pc1.equals("thumbor") && components[6].equals("cdn.vox-cdn.com") && components[7].equals("uploads"))
            {
                String[] xc = since(components, 6);
                xc = saneExceptFirst(xc, true);
                newrel = recompose(xc, "/");
                if (newrel.length() <= MaxRelativeFilePath)
                    return newrel;
            }
        }

        if (host.endsWith(".gannett-cdn.com") && components.length >= 13)
        {
            if (pc1.equals("-mm-") && components[4].equals("http") && components[6].equals("-mm-") &&
                    components[9].equals("local") && components[10].equals("-"))
            {
                String[] xc = since(components, 11);
                xc = concat(host, xc);
                xc = saneExceptFirst(xc, true);
                newrel = recompose(xc, "/");
                if (newrel.length() <= MaxRelativeFilePath)
                    return newrel;
            }
        }

        if (isBotchedImgPrx(components))
        {
            String[] xc = new String[4];
            xc[0] = components[0];
            xc[1] = "@@@";

            int folder = (int) (Math.random() * 100);
            if (folder >= 100)
                folder = 99;
            xc[2] = String.format("x-%02d", folder);
            xc[3] = sane(pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        if (Util.True)
        {
            String[] xc = saneExceptFirst(components, true);
            reapplyExtension(xc, pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        if (components.length >= 3)
        {
            String[] xc = new String[3];
            xc[0] = components[0];
            xc[1] = "@@@";
            xc[2] = sane(pclast);
            reapplyExtension(xc, pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        if (components.length >= 3)
        {
            String[] xc = saneExceptFirst(components, false);
            xc[xc.length - 1] = "x - " + Util.uuid();
            reapplyExtension(xc, pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        throwException("Unable to shorten file path using employed methods: " + rel);

        return null;
    }

    private String sane(String component) throws Exception
    {
        return LinkFilepath.makeSanePathComponent(component);
    }

    @SuppressWarnings("unused")
    private String[] saneAll(String[] components, boolean tryAvoidGuid) throws Exception
    {
        components = components.clone();

        String pclast = components[components.length - 1];

        for (int k = 0; k < components.length; k++)
            components[k] = sane(components[k]);

        if (tryAvoidGuid)
            tryAvoidGuid(components, pclast);

        return components;
    }

    private String[] saneExceptFirst(String[] components, boolean tryAvoidGuid) throws Exception
    {
        components = components.clone();

        String pclast = components[components.length - 1];

        for (int k = 1; k < components.length; k++)
            components[k] = sane(components[k]);

        if (tryAvoidGuid)
            tryAvoidGuid(components, pclast);

        return components;
    }

    private void tryAvoidGuid(String[] components, String pclast) throws Exception
    {
        if (!isLowercaseGuid(components[components.length - 1]))
            return;

        String path = null;
        try
        {
            URI uri = new URI(URLCodec.fullyDecodeMixed(pclast));
            path = uri.getPath();
        }
        catch (Exception ex)
        {
            return;
        }

        if (path == null)
            return;

        components[components.length - 1] = sane(path);
    }

    private void reapplyExtension(String[] xc, String pclast) throws Exception
    {
        String ext = LinkFilepath.getMediaFileExtension(pclast);

        if (ext == null)
        {
            URI uri = null;

            try
            {
                uri = new URI(URLCodec.fullyDecodeMixed(pclast));
            }
            catch (Exception ex)
            {
                // disregard
            }

            if (uri != null && uri.getPath() != null)
                ext = LinkFilepath.getMediaFileExtension(uri.getPath());
        }

        if (ext != null && !xc[xc.length - 1].toLowerCase().endsWith("." + ext.toLowerCase()))
            xc[xc.length - 1] += "." + ext;
    }

    private boolean isBotchedImgPrx(String[] components)
    {
        if (!components[0].equals("imgprx.livejournal.net"))
            return false;

        for (int k = 1; k < components.length; k++)
        {
            String pc = components[k];
            if (!pc.startsWith("x-") || !isLowercaseGuid(pc.substring(2)))
                return false;
        }

        return true;
    }

    public static boolean isLowercaseGuid(String s)
    {
        if (s == null || s.length() != 32)
            return false;

        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')))
                return false;
        }

        return true;
    }

    // String[] tokens = {"A", "B", "BREAK", "C", "D"};
    // String[] result = extractRemainderAfter(tokens, "BREAK");
    // result is ["C", "D"]
    public static String[] extractRemainderAfter(String[] tokens, String breaker)
    {
        if (tokens == null || breaker == null)
            throw new NullPointerException("Arguments must not be null");

        for (int i = 0; i < tokens.length; i++)
        {
            if (breaker.equals(tokens[i]))
            {
                int remainderLength = tokens.length - (i + 1);
                if (remainderLength <= 0)
                    return new String[0]; // no elements after breaker

                String[] remainder = new String[remainderLength];
                System.arraycopy(tokens, i + 1, remainder, 0, remainderLength);
                return remainder;
            }
        }

        return null; // breaker not found
    }

    private String[] since(String[] sa, int k)
    {
        if (sa == null)
            throw new NullPointerException("Input array is null");

        if (k < 0 || k > sa.length)
            throw new IndexOutOfBoundsException("Index k is out of bounds: " + k);

        int newLength = sa.length - k;
        String[] result = new String[newLength];
        System.arraycopy(sa, k, result, 0, newLength);
        return result;
    }

    private String recompose(String[] components, String separator) throws Exception
    {
        StringBuilder path = new StringBuilder();

        for (String x : components)
        {
            if (path.length() != 0)
                path.append(separator);
            path.append(x);
        }

        return path.toString();
    }

    private String[] concat(String[] sa1, String[] sa2)
    {
        if (sa1 == null || sa2 == null)
            throw new NullPointerException("Arguments must not be null");

        String[] result = new String[sa1.length + sa2.length];
        System.arraycopy(sa1, 0, result, 0, sa1.length);
        System.arraycopy(sa2, 0, result, sa1.length, sa2.length);
        return result;
    }

    private String[] concat(String s, String[] sa)
    {
        if (s == null || sa == null)
            throw new NullPointerException("Arguments must not be null");

        String[] result = new String[1 + sa.length];
        result[0] = s;
        System.arraycopy(sa, 0, result, 1, sa.length);
        return result;
    }

    @SuppressWarnings("unused")
    private String[] concat(String[] sa, String s)
    {
        if (sa == null || s == null)
            throw new NullPointerException("Arguments must not be null");

        String[] result = new String[sa.length + 1];
        System.arraycopy(sa, 0, result, 0, sa.length);
        result[sa.length] = s;
        return result;
    }

    private String[] concat(String... s)
    {
        return s.clone();
    }

    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }

    /* ===================================================================================== */

    public String makeShorterFileRelativePathAfterCollision(String rel) throws Exception
    {
        String[] components = rel.split("/");

        String host = components[0];

        for (int k = 1; k < components.length; k++)
            components[k] = URLCodec.fullyDecodeMixed(components[k]);

        // String pc1 = components.length <= 1 ? null : components[1];
        // String pc2 = components.length <= 2 ? null : components[2];
        // String pc3 = components.length <= 3 ? null : components[3];
        String pclast = components[components.length - 1];

        String newrel = null;

        if (components.length >= 3)
        {
            String[] xc = new String[3];
            xc[0] = host;
            xc[1] = "@@@";
            xc[2] = "x - " + Util.uuid();
            reapplyExtension(xc, pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        throwException("Unable to shorten file path using employed methods: " + rel);

        return null;
    }

    /* ===================================================================================== */

    public String reconstructURL(String rel) throws Exception
    {
        String[] components = rel.split("/");

        String host = components[0];
        host = host.replace("__", ":");
        components[0] = host;

        for (int k = 1; k < components.length; k++)
            components[k] = URLCodec.fullyDecodeMixed(components[k]);

        for (int k = 1; k < components.length; k++)
        {
            String pc = components[k];

            String ext = LinkFilepath.getMediaFileExtension(pc);
            if (ext != null)
                pc = Util.stripTail(pc, "." + ext);

            if (pc.startsWith("x-") && isLowercaseGuid(pc.substring(2)))
                return null;
        }

        String url = "https://" + recompose(components, "/");

        return url;
    }

    /* ===================================================================================== */

    public static boolean isGeneratedUnixRelativePath(String rel) throws Exception
    {
        String[] components = rel.split("/");

        for (String pc : components)
        {
            if (pc.equals("@@@") || isLowercaseGuid(pc))
                return true;
        }

        String pclast = components[components.length - 1];
        String ext = LinkFilepath.getMediaFileExtension(pclast);
        
        if (ext != null)
        {
            String pc = Util.stripTail(pclast, "." + ext);
            if (isLowercaseGuid(pc))
                return true;
        }

        return false;
    }
}
