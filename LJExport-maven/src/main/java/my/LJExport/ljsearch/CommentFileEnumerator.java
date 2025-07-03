package my.LJExport.ljsearch;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

public class CommentFileEnumerator
{

    private static final Pattern RELATIVE_PATH_PATTERN = Pattern.compile(
            "([^/]+)/([0-9]{4})/([0-9]{2})/([0-9]+)_([0-9]+)\\.html");

    public static List<CommentFileInfo> enumCommentFiles(String rootDir) throws IOException
    {
        Path rootPath = Paths.get(rootDir).toAbsolutePath().normalize();
        List<CommentFileInfo> result = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(rootPath))
        {
            stream.filter(Files::isRegularFile).forEach(path ->
            {
                Path relPath = rootPath.relativize(path.toAbsolutePath().normalize());
                String relUnixPath = relPath.toString().replace(File.separatorChar, '/');

                Matcher matcher = RELATIVE_PATH_PATTERN.matcher(relUnixPath);
                if (!matcher.matches())
                    throw new RuntimeException("Invalid file path structure: " + relUnixPath);

                String user = matcher.group(1);
                if (user.startsWith("@"))
                    return; // skip users starting with "@"

                int yyyy = Integer.parseInt(matcher.group(2));
                int mm = Integer.parseInt(matcher.group(3));
                int rid = Integer.parseInt(matcher.group(4));
                int tid = Integer.parseInt(matcher.group(5));

                CommentFileInfo info = new CommentFileInfo();
                info.user = user;
                info.yyyy = yyyy;
                info.mm = mm;
                info.rid = rid;
                info.tid = tid;
                info.fullPath = path.toFile().getAbsoluteFile().toString();
                info.relativePath = relUnixPath;

                result.add(info);
            });
        }

        result.sort(Comparator
                .comparing((CommentFileInfo f) -> f.user)
                .thenComparingInt(f -> f.yyyy)
                .thenComparingInt(f -> f.mm)
                .thenComparingInt(f -> f.rid)
                .thenComparingInt(f -> f.tid));

        return result;
    }
}
