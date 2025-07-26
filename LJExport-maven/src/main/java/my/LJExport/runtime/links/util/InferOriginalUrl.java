package my.LJExport.runtime.links.util;

import java.net.URLEncoder;

import my.LJExport.maintenance.handlers.util.ShortFilePath;
import my.LJExport.runtime.url.URLCodec;

/*
 * Infer original URL from relative path name in link repository
 */
public class InferOriginalUrl
{
    private static final String schema = "https://";

    /*
     * If URL cannot be inferred, return null.
     * First path component is either hostname or hostname__port. 
     */
    public static String infer(String relpath) throws Exception
    {
        if (ShortFilePath.isGeneratedUnixRelativePath(relpath))
            return null;

        // 1. Decode if needed
        if (relpath.contains("%"))
        {
            relpath = URLCodec.fullyDecodeMixed(relpath);
            if (relpath.contains("\uFFFD"))
                return null; // corrupted
        }
        
        // 1.5 Special case for archive.org stored URLs
        if (relpath.startsWith("web.archive.org/web/"))
        {
            String suffix = relpath.substring("web.archive.org/web/".length());
            int slash = suffix.indexOf('/');
            if (slash > 0 && slash + 1 < suffix.length())
            {
                String timestamp = suffix.substring(0, slash);
                String encodedOriginal = suffix.substring(slash + 1);
                String decodedOriginal = java.net.URLDecoder.decode(encodedOriginal, java.nio.charset.StandardCharsets.UTF_8.name());
                return schema + "web.archive.org/web/" + timestamp + "/" + decodedOriginal;
            }
        }        

        // 2. Restore host[:port] structure
        relpath = restoreHostPort(relpath);

        // 3. Detect and split query string
        String path = relpath;
        String query = null;
        int qpos = relpath.indexOf('?');
        if (qpos >= 0)
        {
            path = relpath.substring(0, qpos);
            query = relpath.substring(qpos + 1);

            // 4. Remove duplicate extension at end of query string
            int dot = path.lastIndexOf('.');
            if (dot >= 0)
            {
                String ext = path.substring(dot);
                if (query.toLowerCase().endsWith(ext.toLowerCase()))
                    query = query.substring(0, query.length() - ext.length());
            }
        }

        // 5. Encode each segment of the path
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++)
            parts[i] = encodeSegment(parts[i]);
        String encodedPath = String.join("/", parts);

        // 6. Compose final URL
        String url = schema + encodedPath;
        if (query != null && !query.isEmpty())
            url += "?" + URLEncoder.encode(query, "UTF-8"); // query needs encoding too

        return url;
    }

    private static String restoreHostPort(String relpath)
    {
        String[] pcs = relpath.split("/");
        pcs[0] = separateHostPort(pcs[0]);
        return recompose(pcs, "/");
    }

    private static String separateHostPort(String hostport)
    {
        int sepIndex = hostport.lastIndexOf("__");

        if (sepIndex != -1 && sepIndex < hostport.length() - 2)
        {
            String host = hostport.substring(0, sepIndex);
            String port = hostport.substring(sepIndex + 2);

            // Check if port is numeric
            if (port.matches("\\d+"))
                return host + ":" + port;
            else
                throw new IllegalArgumentException("Port is not numeric: " + hostport);
        }

        return hostport;
    }

    private static String recompose(String[] components, String separator)
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

    private static String encodeSegment(String segment) throws Exception
    {
        // encode segment safely: Cyrillic, space, etc.
        // prevent space → +
        return URLEncoder.encode(segment, "UTF-8").replace("+", "%20");
    }
}
