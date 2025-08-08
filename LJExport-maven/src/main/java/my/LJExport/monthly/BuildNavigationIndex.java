package my.LJExport.monthly;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.lj.LJUserHost;

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
    private static final String PART_SPACER = "&nbsp;&nbsp;&nbsp;";

    private static final String nl = "\n";
    public static final String DIVIDER = "<div style=\"height: 7px;border: 1;box-shadow: inset 0 9px 9px -3px" + nl +
            "      rgba(11, 99, 184, 0.8);-webkit-border-radius:" + nl +
            "      5px;-moz-border-radius: 5px;-ms-border-radius:" + nl +
            "      5px;-o-border-radius: 5px;border-radius: 5px;\"></div>";

    private final String user;
    private final String host;
    private final String section;  // "pages", "reposts"
    private final Path rootDir;
    private final String pagesRootDir;
    private final String dividerHtml;

    public BuildNavigationIndex(String user, String host, String section, String rootDir, String pagesRootDir, String dividerHtml)
    {
        Objects.requireNonNull(rootDir, "rootDir must not be null");
        this.user = user;
        this.host = host;
        this.section = section;
        this.rootDir = Paths.get(rootDir);
        this.pagesRootDir = pagesRootDir; 
        this.dividerHtml = "<br>" + dividerHtml;
    }

    public void buildNavigation() throws Exception
    {
        Map<Integer, SortedMap<Integer, List<String>>> filesByYearAndMonth = scanDirectoryTree();
        List<Integer> allYears = new ArrayList<>(filesByYearAndMonth.keySet());

        for (int i = 0; i < allYears.size(); i++)
        {
            int year = allYears.get(i);
            Integer prevYear = (i > 0) ? allYears.get(i - 1) : null;
            Integer nextYear = (i < allYears.size() - 1) ? allYears.get(i + 1) : null;

            Path yearDir = rootDir.resolve(Integer.toString(year));
            String content = buildYearIndexHtml(year, filesByYearAndMonth.get(year), prevYear, nextYear);
            Util.writeToFileSafe(yearDir.resolve("index.html").toString(), content);
        }
        
        String html = new BuildDirectPageIndex(pagesRootDir, user, host).buildHtml(rootDir.toAbsolutePath().toString());
        Util.writeToFileSafe(rootDir.resolve("direct-index.html").toString(), html);

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

    private static boolean isSingleUnsplitFile(List<String> files)
    {
        if (files.size() != 1)
            return false;
        String name = files.get(0);
        Matcher m = MONTH_FILE_PATTERN.matcher(name);
        return m.matches() && m.group(3) == null;
    }

    private String buildYearIndexHtml(int year, SortedMap<Integer, List<String>> monthFiles, Integer prevYear, Integer nextYear)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>")
                .append(user + " " + year)
                .append("</title>\n")
                .append(STYLE_BLOCK)
                .append("</head>\n<body>\n");
        
        sb.append("<h1>").append(year).append("</h1>\n\n");

        for (Map.Entry<Integer, List<String>> e : monthFiles.entrySet())
        {
            int month = e.getKey();
            List<String> files = e.getValue();
            if (files.isEmpty())
                continue;

            if (isSingleUnsplitFile(files))
            {
                String label = String.format("%04d %02d", year, month);
                sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<a class=\"partial-underline\" href=\"")
                        .append(files.get(0)).append("\">").append(label).append("</a><br>\n");
                continue;
            }

            int part1 = extractPartSuffix(files.get(0));
            String label = String.format("%04d %02d%sчасть %d", year, month, PART_SPACER, part1);
            sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<a class=\"partial-underline\" href=\"")
                    .append(files.get(0)).append("\">").append(label).append("</a>");

            for (int i = 1; i < files.size(); i++)
            {
                int sqn = extractPartSuffix(files.get(i));
                sb.append(PART_SPACER)
                        .append(String.format("<a class=\"partial-underline\" href=\"%s\">часть %d</a>", files.get(i), sqn));
            }
            sb.append("<br>\n");
        }

        // Add navigation section if applicable
        if (dividerHtml != null && (prevYear != null || nextYear != null))
        {
            sb.append(
                    "<div id=\"ljexport-yearly-navigation\" style=\"text-align: center; margin-bottom: 2em; font-size: 140%;\">\n");
            sb.append(
                    "<style>a.ljexport-partial-underline { text-decoration: none; background-image: linear-gradient(to top, black 1px, transparent 1px); background-repeat: repeat-x; background-position: 0 1.1em; background-size: 1ch 1em; white-space: pre; color: #228B22; } a.ljexport-partial-underline:visited { color: #003300; }</style>\n");
            sb.append(dividerHtml).append("<br>\n");
            boolean needSep = false;
            if (prevYear != null)
            {
                sb.append("<a class=\"ljexport-partial-underline\" href=\"../")
                        .append(prevYear).append("/index.html\">◄  раньше к ").append(prevYear).append("</a>");
                needSep = true;
            }
            if (prevYear != null || nextYear != null)
            {
                if (needSep)
                    sb.append(PART_SPACER).append("|").append(PART_SPACER);
                sb.append("<a class=\"ljexport-partial-underline\" href=\"../index.html\">оглавление лет</a>");
                needSep = true;
            }
            if (nextYear != null)
            {
                if (needSep)
                    sb.append(PART_SPACER).append("|").append(PART_SPACER);
                sb.append("<a class=\"ljexport-partial-underline\" href=\"../")
                        .append(nextYear).append("/index.html\">дальше к ").append(nextYear).append("  ►</a>");
            }
            sb.append("\n</div>\n");
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

    private String buildRootIndexHtml(Map<Integer, SortedMap<Integer, List<String>>> filesByYearAndMonth) throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>")
                .append(user)
                .append("</title>\n")
                .append(STYLE_BLOCK)
                .append("</head>\n<body>\n");
        
        buildRootIndexHeader(sb);

        for (Map.Entry<Integer, SortedMap<Integer, List<String>>> yearEntry : filesByYearAndMonth.entrySet())
        {
            int year = yearEntry.getKey();
            sb.append("<h2 style=\"margin:0;padding:0;\"><a class=\"partial-underline\" href=\"")
                    .append(year).append("/index.html\">" + year + "</a></h2>\n<br>\n");

            SortedMap<Integer, List<String>> months = yearEntry.getValue();
            for (Map.Entry<Integer, List<String>> monthEntry : months.entrySet())
            {
                int month = monthEntry.getKey();
                List<String> files = monthEntry.getValue();

                if (files.isEmpty())
                    continue;

                if (isSingleUnsplitFile(files))
                {
                    String label = String.format("%04d %02d", year, month);
                    sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<a class=\"partial-underline\" href=\"")
                            .append(year).append("/").append(files.get(0)).append("\">").append(label).append("</a><br>\n");
                }
                else
                {
                    int part1 = extractPartSuffix(files.get(0));
                    String label = String.format("%04d %02d%sчасть %d", year, month, PART_SPACER, part1);
                    sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<a class=\"partial-underline\" href=\"")
                            .append(year).append("/").append(files.get(0)).append("\">").append(label).append("</a>");

                    for (int i = 1; i < files.size(); i++)
                    {
                        int sqn = extractPartSuffix(files.get(i));
                        sb.append(PART_SPACER)
                                .append(String.format("<a class=\"partial-underline\" href=\"%s/%s\">часть %d</a>", year,
                                        files.get(i), sqn));
                    }
                    sb.append("<br>\n");
                }
            }
            sb.append("<br>\n");
        }

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }
    
    private void buildRootIndexHeader(StringBuilder sb) throws Exception
    {
        String section = this.section;
        if (section.equals("pages"))
            section = "";
        else
            section = " (" + section + ")";
        
        LJUserHost ljUserHost = LJUserHost.split(Config.User);
        
        sb.append(String.format("<div style=\"font-size:110%%;\"><h1>Дневник %s%s</h1></div>" + nl, ljUserHost.user, section));
        
        insertLink(sb, "Профиль", "../profile/profile.html");
        insertLink(sb, "Аватары", "../profile/userpics.html");
        insertLink(sb, "Памятные заметки", "../profile/memories.html");
        insertLink(sb, "Фотографии и картинки", "../profile/pictures.html");
        insertLink(sb, "Прямой указатель записей", "direct-index.html");

        sb.append(dividerHtml);
        sb.append(String.format("<div style=\"font-size:90%%;\"><h1>Месячный указатель</h1></div>" + nl));
    }
    
    private void insertLink(StringBuilder sb, String title, String relpath)
    {
        if (new File(rootDir.toFile(), relpath.replace("/", File.separator)).exists())
        {
            sb.append("&nbsp;&nbsp;&nbsp;&nbsp;");
            sb.append(String.format("<a class=\"partial-underline\" href=\"%s\">%s</a><br>", relpath, title));
            sb.append("\n");
        }
    }
}
