package my.LJExport.runtime;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.LJExport.Config;

/*
 * TODO:
 * 
 * Disallow multiple keys (except for different protocols and host case difference)
 * to point to the same file relative path, or rather same relpath.toLowerCase().
 */
public class FileBackedMap
{
    private final Map<String, String> map = new HashMap<>();
    private boolean initialized = false;
    private String filePathPrefix;
    private File file;
    private ExclusiveFileAccess efa;

    public static final String SEPARATOR = "----";
    private static final String nl = Util.isWindowsOS() ? "\r\n" : "\n";

    public synchronized void init(String path) throws IOException
    {
        if (initialized)
            return;

        file = new File(path).getCanonicalFile();

        if (Config.False)
        {
            /* efa constructor will create file if it does not exist */
            if (!file.exists())
                file.createNewFile();
        }

        filePathPrefix = file.getParentFile().getAbsoluteFile().getCanonicalPath();
        if (!filePathPrefix.endsWith(File.separator))
            filePathPrefix += File.separator;

        try
        {
            efa = new ExclusiveFileAccess(file.getCanonicalPath());

            List<String> lines = efa.readExistingLines();

            while (lines.size() >= 3)
            {
                String key = lines.remove(0);
                String value = lines.remove(0);
                String separator = lines.remove(0);

                if (key.trim().length() == 0 && value.trim().length() == 0 && separator.trim().length() == 0)
                    continue;

                if (!separator.trim().equals(SEPARATOR))
                    throw new IOException("Invalid file format");
                key = key.trim();
                value = value.trim();
                map.put(key, toFullLocalPath(value));
            }

            while (lines.size() != 0)
            {
                String s = lines.remove(0);
                if (s.trim().length() != 0)
                    throw new IOException("Invalid file format");
            }

        }
        catch (Exception ex)
        {
            if (efa != null)
            {
                efa.close();
                efa = null;
            }

            throw ex;
        }

        initialized = true;
    }

    public String getPath() throws Exception
    {
        return file.getCanonicalPath();
    }

    public synchronized String get(String key)
    {
        if (!initialized)
            throw new RuntimeException("File-backed map is not initialized");

        return map.get(key);
    }

    public synchronized String getAnyUrlProtocol(String key) throws Exception
    {
        String value = map.get(key);
        if (value == null)
            value = map.get(Util.flipProtocol(key));
        return value;
    }

    public synchronized String put(String key, String value) throws IOException
    {
        if (!initialized)
            throw new RuntimeException("File-backed map is not initialized");

        if (map.containsKey(key))
            throw new IllegalArgumentException("Key already exists: " + key);

        map.put(key, value);

        StringBuilder sb = new StringBuilder();

        sb.append(key)
                .append(nl)
                .append(toRelativeUnixPath(value))
                .append(nl)
                .append(SEPARATOR)
                .append(nl);

        efa.appendContent(sb.toString());
        efa.flush();

        return value;
    }

    public synchronized void close() throws IOException
    {
        if (efa != null)
        {
            efa.close();
            efa = null;
        }

        map.clear();

        file = null;

        initialized = false;
    }

    private String toFullLocalPath(String s)
    {
        return filePathPrefix + s.replace("/", File.separator);
    }

    private String toRelativeUnixPath(String s)
    {
        if (!s.startsWith(filePathPrefix))
            throw new RuntimeException("Link file path is not within links storage");
        s = s.substring(filePathPrefix.length());
        return s.replace(File.separator, "/");
    }

    /* ================================================================================ */

    public static class LinkMapEntry
    {
        public String key;
        public String value;

        public LinkMapEntry(String key, String value)
        {
            this.key = key;
            this.value = value;
        }
    }

    public static List<LinkMapEntry> readMapFile(String path) throws Exception
    {
        List<LinkMapEntry> list = new ArrayList<>();

        String content = Util.readFileAsString(new File(path).getCanonicalPath());
        content = content.replace("\r\n", "\n");
        List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n")));

        while (lines.size() >= 3)
        {
            String key = lines.remove(0);
            String value = lines.remove(0);
            String separator = lines.remove(0);

            if (key.trim().length() == 0 && value.trim().length() == 0 && separator.trim().length() == 0)
                continue;

            if (!separator.trim().equals(SEPARATOR))
                throw new IOException("Invalid file format");
            key = key.trim();
            value = value.trim();
            list.add(new LinkMapEntry(key, value));
        }

        while (lines.size() != 0)
        {
            String s = lines.remove(0);
            if (s.trim().length() != 0)
                throw new IOException("Invalid file format");
        }

        return list;
    }

    public static String recomposeMapFile(List<LinkMapEntry> list) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        
        for (LinkMapEntry e : list)
        {
            sb.append(e.key)
            .append(nl)
            .append(e.value)
            .append(nl)
            .append(SEPARATOR)
            .append(nl);
        }

        return sb.toString();
    }
}
