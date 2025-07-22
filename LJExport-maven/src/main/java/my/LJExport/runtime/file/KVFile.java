package my.LJExport.runtime.file;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class KVFile
{
    private static final String SEPARATOR = "----";

    private final Path filePath;

    public static class KVEntry
    {
        public String key;
        public String value;

        public KVEntry(String key, String value)
        {
            this.key = key;
            this.value = value;
        }
    }

    public KVFile(String path)
    {
        this.filePath = Paths.get(path);
    }

    public void save(List<KVEntry> entries) throws Exception
    {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))
        {
            String lineSeparator = System.lineSeparator();
            for (KVEntry entry : entries)
            {
                writer.write(entry.key);
                writer.write(lineSeparator);
                writer.write(entry.value);
                writer.write(lineSeparator);
                writer.write(SEPARATOR);
                writer.write(lineSeparator);
            }
        }
    }

    public List<KVEntry> load(boolean trim) throws Exception
    {
        if (!Files.exists(filePath))
            throw new FileNotFoundException("File not found: " + filePath);

        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<KVEntry> result = new ArrayList<>();
        int i = 0;
        int n = lines.size();

        while (i + 2 < n)
        {
            String key = lines.get(i++);
            String value = lines.get(i++);
            String sep = lines.get(i++);

            if (trim)
            {
                key = key.trim();
                value = value.trim();
                sep = sep.trim();
            }

            if (!SEPARATOR.equals(sep))
                throw new IOException("Expected separator at line " + i + ", but found: '" + sep + "'");

            result.add(new KVEntry(key, value));
        }

        // After last complete triplet, only whitespace lines are allowed
        while (i < n)
        {
            String tail = lines.get(i++);
            tail = tail.trim();
            if (!tail.isEmpty())
                throw new IOException("Unexpected non-whitespace line after last separator at line " + i);
        }

        return result;
    }

    public void delete() throws Exception
    {
        try
        {
            Files.deleteIfExists(filePath);
        }
        catch (IOException ex)
        {
            throw new IOException("Failed to delete file: " + filePath, ex);
        }
    }
}
