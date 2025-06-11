package my.LJExport.runtime;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class FileBackedMap
{
    private final Map<String, String> map = new HashMap<>();
    private BufferedWriter writer;
    private boolean initialized = false;
    private File file;
    
    private static final String SEPARATOR = "----";

    public synchronized void init(String path) throws IOException
    {
        if (initialized)
            return;

        file = new File(path);
        if (!file.exists())
            file.createNewFile();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
        {
            String key;
            while ((key = reader.readLine()) != null && key.trim().length() != 0)
            {
                String value = reader.readLine();
                String separator = reader.readLine();

                if (value == null || separator == null || !separator.trim().equals(SEPARATOR))
                    throw new IOException("Invalid file format");
                key = key.trim();
                value = value.trim();
                map.put(key, value);
            }
        }

        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8));
        initialized = true;
    }

    public synchronized String get(String key)
    {
        return map.get(key);
    }

    public synchronized String put(String key, String value) throws IOException
    {
        if (map.containsKey(key))
            throw new IllegalArgumentException("Key already exists: " + key);

        map.put(key, value);

        writer.write(key);
        writer.newLine();
        writer.write(value);
        writer.newLine();
        writer.write(SEPARATOR);
        writer.newLine();
        writer.flush();

        return value;
    }

    public synchronized void close() throws IOException
    {
        if (writer != null)
        {
            writer.close();
            writer = null;
        }
        
        map.clear();
        
        file = null;

        initialized = false;
    }
}
