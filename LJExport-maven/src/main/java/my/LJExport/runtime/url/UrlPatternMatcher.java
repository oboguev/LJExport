package my.LJExport.runtime.url;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import my.LJExport.runtime.Util;

public class UrlPatternMatcher
{
    private final List<Pattern> patterns = new ArrayList<>();
    
    public static UrlPatternMatcher fromResource(String path) throws Exception
    {
        String res = Util.loadResource(path).replace("\r", "");
        return new UrlPatternMatcher(Arrays.asList(res.split("\n")));
    }

    public UrlPatternMatcher(List<String> lines) 
    {
        for (String line : lines)
        {
            line = line.trim();

            if (!line.isEmpty() && !line.startsWith("#"))
                patterns.add(compilePattern(line));
        }
    }

    private Pattern compilePattern(String pattern)
    {
        boolean hasLeadingSubdomainWildcard = pattern.startsWith("*.");
        boolean hasTrailingWildcard = pattern.endsWith("*");

        // Strip wildcards for now
        if (hasLeadingSubdomainWildcard)
            pattern = pattern.substring(2); // remove "*."

        if (hasTrailingWildcard)
            pattern = pattern.substring(0, pattern.length() - 1); // remove "*"

        // Replace NNNNNNNN with placeholder
        pattern = pattern.replace("NNNNNNNN", "<<DIGITS>>");

        // Escape the rest literally
        pattern = Pattern.quote(pattern);

        // Restore wildcards
        if (hasLeadingSubdomainWildcard)
            pattern = "([^.]+\\.)*" + pattern;

        if (hasTrailingWildcard)
            pattern = pattern + ".*";

        pattern = pattern.replace("<<DIGITS>>", "\\\\d+");

        return Pattern.compile("^" + pattern + "$");
    }

    public boolean contains(String url) throws Exception
    {
        String normalizedUrl = normalizeUrl(url);

        for (Pattern p : patterns)
        {
            if (p.matcher(normalizedUrl).matches())
                return true;
        }

        return false;
    }

    private static String normalizeUrl(String fullUrl) throws Exception
    {
        if (Util.True)
        {
            String np = Util.stripProtocol(fullUrl);
            np = UrlUtil.lowercaseFirstSegment(np);
            return np;
        }
        else
        {
            URL url = new URL(fullUrl);
            String host = url.getHost().toLowerCase();
            String path = url.getPath();
            return host + path;
        }
    }
}