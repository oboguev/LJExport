package my.LJExport.runtime.url;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import my.LJExport.runtime.Util;

/**
 * Matches URLs against an exclusion list that may contain
 *  • exact URLs, or
 *  • prefix patterns ending with '*'.
 *
 * Normalization rules
 *  • Strip "http://" or "https://" (case-insensitive)
 *  • Lower-case the host portion only
 *  • Leave path / query string unchanged
 *
 * Example patterns:
 *      http://example.com/bad.gif
 *      http://example.com/tracker*
 *      https://example.com/images/*     // OK: protocol ignored
 */
public final class UrlSetMatcher
{
    /** Exact (fully normalized) URLs that must be blocked. */
    private final Set<String> exactMatches;

    /** Prefixes to block; each ends with '/' or any character before the '*'. */
    private final List<String> prefixMatches;

    private UrlSetMatcher chain = null;

    public static UrlSetMatcher loadFile(String filePath) throws Exception
    {
        return new UrlSetMatcher(Util.read_set_from_file(filePath));
    }

    public static UrlSetMatcher empty() throws Exception
    {
        return new UrlSetMatcher(new HashSet<String>());
    }

    public void chain(UrlSetMatcher next)
    {
        if (chain == null)
            chain = next;
        else
            chain.chain(next);
    }

    public UrlSetMatcher(Set<String> rawPatterns)
    {
        Objects.requireNonNull(rawPatterns, "pattern set must not be null");

        Set<String> exact = new HashSet<>(rawPatterns.size());
        List<String> prefixes = new ArrayList<>();

        for (String raw : rawPatterns)
        {
            if (raw == null || raw.isEmpty())
                continue; // should not occur
            String norm = normalise(raw);

            if (norm.endsWith("*"))
            {
                prefixes.add(norm.substring(0, norm.length() - 1));
            }
            else
            {
                exact.add(norm);
            }
        }

        this.exactMatches = Collections.unmodifiableSet(exact);
        this.prefixMatches = Collections.unmodifiableList(new ArrayList<>(prefixes));
    }

    /**
     * Returns {@code true} if {@code url} is to be skipped.
     *
     * @param url
     *            any absolute or protocol-relative URL
     */
    public boolean match(String url)
    {
        if (url == null || url.isEmpty())
        {
            if (chain != null)
                return chain.match(url);
            else
                return false;
        }

        String norm = normalise(url);

        if (exactMatches.contains(norm))
        {
            return true;
        }
        for (String prefix : prefixMatches)
        {
            if (norm.startsWith(prefix))
            {
                return true;
            }
        }

        if (chain != null)
            return chain.match(url);
        else
            return false;
    }

    public boolean matchOR(String url1, String url2)
    {
        return match(url1) || match(url2);
    }

    public boolean matchLocal(String url)
    {
        if (url != null && url.startsWith("/"))
            return match("http://no-host.no-domain" + url);
        else
            return false;
    }

    // ---------------------------------------------------------------------

    /** Strip protocol, lower-case host, keep everything else verbatim. */
    private static String normalise(String url)
    {
        String s = stripProtocol(url);

        int slash = s.indexOf('/');
        if (slash < 0)
        {
            // host only
            return s.toLowerCase(Locale.ROOT);
        }
        String host = s.substring(0, slash).toLowerCase(Locale.ROOT);
        String suffix = s.substring(slash); // path + query (case-preserved)
        return host + suffix;
    }

    private static String stripProtocol(String s)
    {
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://"))
            return s.substring(7);
        if (lower.startsWith("https://"))
            return s.substring(8);
        return s;
    }
}
