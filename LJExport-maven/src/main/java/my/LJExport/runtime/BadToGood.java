package my.LJExport.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BadToGood
{
    private final Map<String, String> map = new HashMap<>();
    private final Map<String, String> source = new HashMap<>();
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

            String bad = normalise(readFile(badPath));
            String good = readFile(goodPath);

            String previous = map.put(bad, good);
            if (previous != null)
                throw new IllegalStateException("Duplicate bad value encountered in " + base + ".bad, same as " + source.get(bad));
            source.put(bad, badPath.getFileName().toString());
        }
    }

    public synchronized String good(String bad)
    {
        return bad == null ? null : map.get(normalise(bad));
    }

    public synchronized String nextFilePath(String seed, String ext, String newExtension) throws Exception
    {
        String fn = nextFileName(seed, ext, newExtension, this.directory) + "." + newExtension;
        File fp = this.directory.toFile();
        fp = new File(fp, fn);
        return fp.getCanonicalPath();
    }

    public synchronized void addMapping(String bad, String good) throws Exception
    {
        bad = normalise(bad);
        if (map.containsKey(bad))
            throw new Exception("Duplicate bad key");
        map.put(bad, good);
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

    /**
     * Scan {@code directory} for files named {@code <seed>-<number>.<ext1>} or
     * {@code <seed>-<number>.<ext2>} (each &lt;number&gt; is one or more digits).
     * Return the next base-name in the sequence: {@code <seed>-<max+1>}.
     *
     * @param seed      fixed seed part (e.g. "SEED")
     * @param ext1      first extension (with or without leading dot, case-sensitive)
     * @param ext2      second extension (with or without leading dot, case-sensitive)
     * @param directory directory to scan (must exist and be readable)
     * @return          next base name, without path or extension (e.g. "SEED-322")
     * @throws IOException if the directory cannot be listed
     */
    public static String nextFileName(String seed,
            String ext1,
            String ext2,
            Path directory) throws IOException
    {

        // Normalise the extensions: strip an optional leading dot
        String e1 = ext1.startsWith(".") ? ext1.substring(1) : ext1;
        String e2 = ext2.startsWith(".") ? ext2.substring(1) : ext2;

        // Build a single regex:  ^<seed>-(digits)\.(ext1|ext2)$
        // Pattern.quote guards against metacharacters in seed/ext
        Pattern pattern = Pattern.compile(
                Pattern.quote(seed) + "-(\\d+)\\.(?:" +
                        Pattern.quote(e1) + "|" + Pattern.quote(e2) + ')');

        int max = 0;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory))
        {
            for (Path file : ds)
            {
                String name = file.getFileName().toString();
                Matcher m = pattern.matcher(name);
                if (m.matches())
                {
                    int n = Integer.parseInt(m.group(1));
                    if (n > max)
                        max = n;
                }
            }
        }

        // No match → max stays 0 → return <seed>-1
        return seed + '-' + (max + 1);
    }
}
