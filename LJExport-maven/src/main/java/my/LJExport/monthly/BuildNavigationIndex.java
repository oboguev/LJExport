package my.LJExport.monthly;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.LJExport.runtime.Util;

/**
 * Generates <code>index.html</code> files for a tree structured as
 * <pre>
 * rootDir
 * ├── 2006
 * │   ├── 2006-01.html
 * │   ├── 2006-02.html
 * │   └── ...
 * ├── 2007
 * │   ├── 2007-01.html
 * │   └── ...
 * └── index.html (generated)
 * </pre>
 *
 * <p>For every yearly directory (<code>YYYY</code>) an <code>index.html</code> is
 * produced containing links to available monthly files.  An additional root
 * <code>index.html</code> lists all years (linked to those yearly indexes) and
 * indented lists of months available inside each year.</p>
 *
 * <p>The generator is tolerant of gaps: missing months or even whole years are
 * simply skipped.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * new BuildYearlyMonthlyIndexHtml("/path/to/root").buildNavigation();
 * </pre>
 *
 * <p>The filenames must follow the strict pattern <code>YYYY-MM.html</code>.</p>
 */
public final class BuildNavigationIndex
{
    private static final Pattern YEAR_DIR_PATTERN = Pattern.compile("^\\d{4}$");
    private static final Pattern MONTH_FILE_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})(?:-(\\d+))?\\.html$");

    private final Path rootDir;

    public BuildNavigationIndex(String rootDir)
    {
        Objects.requireNonNull(rootDir, "rootDir must not be null");
        this.rootDir = Paths.get(rootDir);
    }

    public void buildNavigation() throws Exception
    {
        Map<Integer, SortedMap<Integer, List<String>>> filesByYearAndMonth = scanDirectoryTree();

        for (Map.Entry<Integer, SortedMap<Integer, List<String>>> e : filesByYearAndMonth.entrySet())
        {
            int year = e.getKey();
            Path yearDir = rootDir.resolve(Integer.toString(year));
            String content = buildYearIndexHtml(year, e.getValue());
            Util.writeToFileSafe(yearDir.resolve("index.html").toString(), content);
        }

        String rootContent = buildRootIndexHtml(filesByYearAndMonth);
        Util.writeToFileSafe(rootDir.resolve("index.html").toString(), rootContent);
    }

    private Map<Integer, SortedMap<Integer, List<String>>> scanDirectoryTree() throws Exception
    {
        if (!Files.isDirectory(rootDir))
        {
            throw new IllegalArgumentException("rootDir does not exist or is not a directory: " + rootDir);
        }

        Map<Integer, SortedMap<Integer, List<String>>> result = new TreeMap<>();

        try (DirectoryStream<Path> years = Files.newDirectoryStream(rootDir))
        {
            for (Path yearDir : years)
            {
                if (!Files.isDirectory(yearDir))
                {
                    continue;
                }
                String dirName = yearDir.getFileName().toString();
                if (!YEAR_DIR_PATTERN.matcher(dirName).matches())
                {
                    continue;
                }
                int year = Integer.parseInt(dirName);

                try (DirectoryStream<Path> files = Files.newDirectoryStream(yearDir, "*.html"))
                {
                    for (Path file : files)
                    {
                        String fileName = file.getFileName().toString();
                        Matcher m = MONTH_FILE_PATTERN.matcher(fileName);
                        if (!m.matches())
                        {
                            continue;
                        }
                        int fileYear = Integer.parseInt(m.group(1));
                        if (fileYear != year)
                        {
                            continue;
                        }
                        int month = Integer.parseInt(m.group(2));
                        if (month < 1 || month > 12)
                        {
                            continue;
                        }
                        result.computeIfAbsent(year, y -> new TreeMap<>())
                                .computeIfAbsent(month, mth -> new ArrayList<>())
                                .add(fileName);
                    }
                }
            }
        }

        for (SortedMap<Integer, List<String>> monthMap : result.values())
        {
            for (List<String> files : monthMap.values())
            {
                files.sort(Comparator.naturalOrder());
            }
        }

        return result;
    }

    private static String buildYearIndexHtml(int year, SortedMap<Integer, List<String>> monthFiles)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>")
                .append(year).append("</title>\n")
                .append(STYLE_BLOCK)
                .append("</head>\n<body>\n");

        sb.append("<h1>").append(year).append("</h1>\n\n");

        for (Map.Entry<Integer, List<String>> e : monthFiles.entrySet())
        {
            int month = e.getKey();
            List<String> files = e.getValue();
            for (String fname : files)
            {
                int part = extractPartSuffix(fname);
                String label = String.format("%04d %02d%s", year, month, (files.size() > 1 ? " часть " + part : ""));
                sb.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                        .append(String.format("<a class=\"partial-underline\" href=\"%s\">%s</a><br>\n", fname, label));
            }
        }

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private String buildRootIndexHtml(Map<Integer, SortedMap<Integer, List<String>>> filesByYearAndMonth)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>Archive index</title>\n")
                .append(STYLE_BLOCK)
                .append("</head>\n<body>\n");

        for (Iterator<Map.Entry<Integer, SortedMap<Integer, List<String>>>> it = filesByYearAndMonth.entrySet().iterator(); it
                .hasNext();)
        {
            Map.Entry<Integer, SortedMap<Integer, List<String>>> entry = it.next();
            int year = entry.getKey();
            SortedMap<Integer, List<String>> monthFiles = entry.getValue();

            sb.append("<a class=\"partial-underline\" href=\"")
                    .append(year).append("/index.html\"><b>")
                    .append(year).append("</b></a><br><br>\n");

            for (Map.Entry<Integer, List<String>> e : monthFiles.entrySet())
            {
                int month = e.getKey();
                List<String> files = e.getValue();
                for (String fname : files)
                {
                    int part = extractPartSuffix(fname);
                    String path = String.format("%04d/%s", year, fname);
                    String label = String.format("%04d %02d%s", year, month, (files.size() > 1 ? " часть " + part : ""));
                    sb.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append(String.format("<a class=\"partial-underline\" href=\"%s\">%s</a><br>\n", path, label));
                }
            }

            if (it.hasNext())
            {
                sb.append("<br>\n");
            }
        }

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private static int extractPartSuffix(String filename)
    {
        int lastDash = filename.lastIndexOf('-');
        int dot = filename.lastIndexOf('.');
        if (lastDash >= 0 && dot > lastDash)
        {
            try
            {
                return Integer.parseInt(filename.substring(lastDash + 1, dot));
            }
            catch (NumberFormatException ignored)
            {
            }
        }
        return 1;
    }

    private static final String STYLE_BLOCK = "<style>\n" +
            "a.partial-underline {\n" +
            "  text-decoration: none;\n" +
            "  background-image: linear-gradient(to top, black 1px, transparent 1px);\n" +
            "  background-repeat: repeat-x;\n" +
            "  background-position: 0 1.1em;\n" +
            "  background-size: 1ch 1em;\n" +
            "  white-space: pre;\n" +
            "  color: #228B22;\n" +
            "}\n" +
            "a.partial-underline:visited {\n" +
            "  color: #003300;\n" +
            "}\n" +
            "</style>\n";
}