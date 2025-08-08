package my.LJExport.monthly;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds an HTML index for files under rootDir matching the pattern YYYY/MM/SQN.html.
 * The links in the index are adjusted to be correct relative to indexLocationDir.
 */
public class BuildDirectPageIndex
{
    private final Path rootDir;
    private final String user;
    private final String host;

    private static final Pattern FILE_PATTERN = Pattern.compile("^(\\d{4})/(\\d{2})/(\\d+)\\.html$");
    private static final String nl = "\n";

    private static class Entry implements Comparable<Entry>
    {
        Path relativePath; // relative to rootDir
        String sqn;

        Entry(Path relativePath, String sqn)
        {
            this.relativePath = relativePath;
            this.sqn = sqn;
        }

        @Override
        public int compareTo(Entry other)
        {
            return Long.compare(Long.parseLong(this.sqn), Long.parseLong(other.sqn));
        }
    }

    public BuildDirectPageIndex(String rootDir, String user, String host)
    {
        this.rootDir = Paths.get(rootDir).normalize();
        this.user = user;
        this.host = host;
    }

    /**
     * Builds the HTML index as a string.
     *
     * @param indexLocationDir the directory where the index file will be placed, used to compute relative HREFs
     * @return the generated HTML content
     * @throws IOException if file walking fails
     */
    public String buildHtml(String indexLocationDir) throws IOException
    {
        Path indexDir = Paths.get(indexLocationDir).normalize();
        List<Entry> entries = new ArrayList<>();

        Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .forEach(path ->
                {
                    Path relPath = rootDir.relativize(path);
                    String relStr = relPath.toString().replace(File.separatorChar, '/');
                    Matcher m = FILE_PATTERN.matcher(relStr);
                    if (m.matches())
                    {
                        String sqn = m.group(3);
                        entries.add(new Entry(relPath, sqn));
                    }
                });

        Collections.sort(entries);

        StringBuilder sb = new StringBuilder();

        sb.append(String.format("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n<title>%s - прямой указатель записей</title>\n", user));

        // Embedded CSS
        sb.append("<style>");
        sb.append(
                "a.ljexport-partial-underline { text-decoration: none; background-image: linear-gradient(to top, black 1px, transparent 1px); background-repeat: repeat-x; background-position: 0 1.1em; background-size: 1ch 1em; white-space: pre; color: #228B22; }");
        sb.append("a.ljexport-partial-underline:visited { color: #003300; }");
        sb.append("</style>\n");

        // Embedded JS
        sb.append("<script>\n");
        sb.append("function openSQN(e) {\n");
        sb.append("  if (e.key === 'Enter') {\n");
        sb.append("    var val = document.getElementById('sqnInput').value.trim();\n");
        sb.append("    if (!val) return;\n");
        sb.append("    var sqn = null;\n");
        sb.append("    var urlRegex = /^https:\\/\\/([^.]+)\\.([\\/a-zA-Z0-9.-]+)\\/(\\d+)\\.html$/;\n");
        sb.append("    var urlMatch = val.match(urlRegex);\n");
        sb.append("    if (urlMatch) {\n");
        sb.append("      sqn = urlMatch[3];\n");
        sb.append("    } else if (/^\\d+\\.html$/.test(val)) {\n");
        sb.append("      sqn = val.slice(0, -5);\n");
        sb.append("    } else if (/^\\d+$/.test(val)) {\n");
        sb.append("      sqn = val;\n");
        sb.append("    }\n");
        sb.append("    if (sqn) {\n");
        sb.append("      var links = document.querySelectorAll('a.ljexport-partial-underline');\n");
        sb.append("      for (var i = 0; i < links.length; i++) {\n");
        sb.append("        if (links[i].href.endsWith('/' + sqn + '.html')) {\n");
        sb.append("          window.open(links[i].getAttribute('href'), '_blank');\n");
        sb.append("          return;\n");
        sb.append("        }\n");
        sb.append("      }\n");
        sb.append("      alert('SQN ' + sqn + ' not found.');\n");
        sb.append("    } else {\n");
        sb.append("      alert('Invalid input format');\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("</script>\n");


        sb.append("</head>\n<body>\n");

        sb.append(String.format("<h1>Прямой указатель записей дневника %s.</h1><br>" + nl, user));
        sb.append(String.format("Для навигации можно ввести:" + nl));
        sb.append(String.format("<ul>" + nl));
        sb.append(String.format("<li>номер записи</li>" + nl));
        sb.append(String.format("<li>номер.html</li>" + nl));
        sb.append(String.format("<li>полную ссылку</li>" + nl));
        sb.append(String.format("</ul><br>" + nl));

        // Input control
        sb.append(
                "<input id=\"sqnInput\" type=\"text\" placeholder=\"Enter SQN or URL\" onkeydown=\"openSQN(event)\" style=\"width: 40em;\">\n");
        sb.append("<hr>\n");

        // Create links
        for (Entry entry : entries)
        {
            Path targetFile = rootDir.resolve(entry.relativePath);
            Path relativeHref = indexDir.relativize(targetFile).normalize();
            String href = relativeHref.toString().replace(File.separatorChar, '/');

            String absoluteUrl = "https://" + user + "." + host + "/" + entry.sqn + ".html";

            sb.append("<a class=\"ljexport-partial-underline\" href=\"")
                    .append(href)
                    .append("\">")
                    .append(absoluteUrl)
                    .append("</a><br>\n");
        }

        sb.append("</body>\n</html>\n");

        return sb.toString();
    }
}