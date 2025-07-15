package my.LJExport.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public final class BadToGood
{

    private final Map<String, String> map = new HashMap<>();
    private final Path directory;

    public BadToGood(String dir) throws IOException
    {
        directory = Paths.get(dir);

        DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.bad");

        for (Path badPath : stream)
        {
            String fileName = badPath.getFileName().toString();
            String base = fileName.substring(0, fileName.length() - 4); // remove ".bad"
            Path goodPath = directory.resolve(base + ".good");

            {
            }

            String bad = normalise(readFile(badPath));
            String good = readFile(goodPath);

            String previous = map.put(bad, good);
            if (previous != null)
                throw new IllegalStateException("Duplicate bad value encountered in “" + base + ".*”");
        }
    }

    public synchronized String good(String bad)
    {
        return bad == null ? null : map.get(normalise(bad));
    }

    private static String readFile(Path path) throws IOException
    {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String normalise(String s)
    {
        if (s == null || s.isEmpty())
            return s;

        if (s.charAt(0) == '\uFEFF')
            s = s.substring(1);

        s = s.replace("\r\n", "\n").replace("\r", "\n");

        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1)))
        {
            end--;
        }
        return s.substring(0, end);
    }
}
