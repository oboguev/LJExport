package my.LJExport.monthly;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds an HTML index of all files under rootDir matching the pattern YYYY/MM/SQN.html.
 */
public class BuildDirectPageIndex
{
    private final Path rootDir;
    private final String user;
    private final String host;

    private static final Pattern FILE_PATTERN = Pattern.compile("^(\\d{4})/(\\d{2})/(\\d+)\\.html$");

    private static class Entry implements Comparable<Entry>
    {
        String year;
        String month;
        String sqn;

        Entry(String year, String month, String sqn)
        {
            this.year = year;
            this.month = month;
            this.sqn = sqn;
        }

        @Override
        public int compareTo(Entry other)
        {
            return Long.compare(Long.parseLong(this.sqn), Long.parseLong(other.sqn));
        }

        String getRelativePath()
        {
            return year + "/" + month + "/" + sqn + ".html";
        }

        String getAbsoluteUrl(String user, String host)
        {
            return "https://" + user + "." + host + "/" + sqn + ".html";
        }
    }

    public BuildDirectPageIndex(String rootDir, String user, String host)
    {
        this.rootDir = Paths.get(rootDir);
        this.user = user;
        this.host = host;
    }

    public String buildHtml() throws IOException
    {
        List<Entry> entries = new ArrayList<>();

        // Walk file tree and collect valid entries
        Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .forEach(path ->
                {
                    Path rel = rootDir.relativize(path);
                    String relStr = rel.toString().replace(File.separatorChar, '/');
                    Matcher m = FILE_PATTERN.matcher(relStr);
                    if (m.matches())
                    {
                        String year = m.group(1);
                        String month = m.group(2);
                        String sqn = m.group(3);
                        entries.add(new Entry(year, month, sqn));
                    }
                });

        // Sort by increasing SQN
        Collections.sort(entries);

        StringBuilder sb = new StringBuilder();

        // HTML header
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n<title>Direct Index</title>\n");

        // Embedded CSS
        sb.append("<style>");
        sb.append(
                "a.ljexport-partial-underline { text-decoration: none; background-image: linear-gradient(to top, black 1px, transparent 1px); background-repeat: repeat-x; background-position: 0 1.1em; background-size: 1ch 1em; white-space: pre; color: #228B22; }");
        sb.append("a.ljexport-partial-underline:visited { color: #003300; }");
        sb.append("</style>\n");

        // Embedded JavaScript
        sb.append("<script>\n");
        sb.append("function openSQN(e) {\n");
        sb.append("  if (e.key === 'Enter') {\n");
        sb.append("    var val = document.getElementById('sqnInput').value.trim();\n");
        sb.append("    if (!val) return;\n");
        sb.append("    var match = val.match(/^https:\\/\\/").append(Pattern.quote(user)).append("\\.").append(Pattern.quote(host))
                .append("\\/(\\d+)\\.html$/);\n");
        sb.append("    var sqn = match ? match[1] : val.match(/^\\d+$/) ? val : null;\n");
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
        sb.append("      alert('Invalid input');\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("</script>\n");

        sb.append("</head>\n<body>\n");

        // Input control
        sb.append(
                "<input id=\"sqnInput\" type=\"text\" placeholder=\"Enter SQN or URL\" onkeydown=\"openSQN(event)\" style=\"width: 40em;\">\n");
        sb.append("<hr>\n");

        // Output links
        for (Entry entry : entries)
        {
            sb.append("<a class=\"ljexport-partial-underline\" href=\"")
                    .append(entry.getRelativePath())
                    .append("\">")
                    .append(entry.getAbsoluteUrl(user, host))
                    .append("</a><br>\n");
        }

        sb.append("</body>\n</html>\n");

        return sb.toString();
    }
}