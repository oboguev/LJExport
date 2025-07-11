package my.WebArchiveOrg;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchiveOrgUrl
{
    private static final String ARCHIVE_PREFIX_HTTPS = "https://web.archive.org/web/";
    private static final String ARCHIVE_PREFIX_HTTP = "https://web.archive.org/web/";

    public static boolean isArchiveOrgUrl(String url)
    {
        String lc = url.toLowerCase();
        return lc.startsWith(ARCHIVE_PREFIX_HTTP) || lc.startsWith(ARCHIVE_PREFIX_HTTPS);
    }

    /*
     * Check for matching except the timestamp, e.g.:
     * https://web.archive.org/web/20160404043545/http://nationalism.org/aziopa/berdyayev2.htm
     * https://web.archive.org/web/20160411084012/http://nationalism.org/
     */
    public static boolean urlMatchesRoot(String url, String archiveOrgRoot, boolean allowWwwDiffer)
    {
        if (url == null || archiveOrgRoot == null)
            return false;

        if (!url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTP) && !url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTPS))
            return false;

        if (!archiveOrgRoot.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTP)
                && !archiveOrgRoot.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTPS))
            return false;

        String urlRest = extractArchivedUrlPart(url);
        String rootRest = extractArchivedUrlPart(archiveOrgRoot);

        if (urlRest == null || rootRest == null)
            return false;

        if (allowWwwDiffer)
        {
            // Normalize www. for comparison
            urlRest = stripWww(urlRest);
            rootRest = stripWww(rootRest);
        }

        return urlRest.startsWith(rootRest);
    }

    @SuppressWarnings("unused")
    private static String stripWww(String s)
    {
        // Remove "www." prefix case-insensitively from host part
        int schemeSep = s.indexOf("://");
        if (schemeSep == -1)
            return s;

        String scheme = s.substring(0, schemeSep + 3);
        String rest = s.substring(schemeSep + 3);

        if (rest.regionMatches(true, 0, "www.", 0, 4))
            rest = rest.substring(4);

        return scheme + rest;
    }

    public static String urlExtractRoot(String url, String archiveOrgRoot)
    {
        String timestamp = extractTimestamp(url);
        String rootRest = extractArchivedUrlPart(archiveOrgRoot);

        if (timestamp == null || rootRest == null)
            return null;

        return ARCHIVE_PREFIX_HTTPS + timestamp + "/" + rootRest;
    }

    public static String urlRelativePath(String url, String archiveOrgRoot)
    {
        String urlRest = extractArchivedUrlPart(url);
        String rootRest = extractArchivedUrlPart(archiveOrgRoot);

        if (urlRest == null || rootRest == null)
            return null;

        // Normalize www.
        String normUrl = stripWww(urlRest);
        String normRoot = stripWww(rootRest);

        if (!normUrl.startsWith(normRoot))
            return null;

        String relative = normUrl.substring(normRoot.length());
        return relative.startsWith("/") ? relative.substring(1) : relative;
    }

    // Extract "http(s)://..." portion after the timestamp
    public static String extractArchivedUrlPart(String url)
    {
        String remainder = null;

        if (url == null)
        {
            return null;
        }
        else if (url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTP))
        {
            remainder = url.substring(ARCHIVE_PREFIX_HTTP.length());
        }
        else if (url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTPS))
        {
            remainder = url.substring(ARCHIVE_PREFIX_HTTPS.length());
        }
        else
        {
            return null;
        }

        int slash = remainder.indexOf('/');
        if (slash == -1)
            return null;

        String result = remainder.substring(slash + 1);

        result = fixSingleSlashInProtocol(result);

        /*
         * May have nesting, such as:
         *     https://web.archive.org/web/20160411084012/https://web.archive.org/web/20160411084012im_/http://nationalism.org/banners/rugreen.gif
         *     https://web.archive.org/web/20160426180114im_/http://web.archive.org/web/20041217144122/www.exile.ru/129/feature1.gif
         */

        while (result.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTP) || result.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTPS))
        {
            result = extractArchivedUrlPart(result);
            result = fixSingleSlashInProtocol(result);
        }

        return result;
    }

    private static final Pattern fixSingleSlashInProtocolPattern = Pattern.compile("^(https?):/([^/])", Pattern.CASE_INSENSITIVE);

    /*
     * Change http:/xxx -> http://xxx
     *        https:/xxx -> https://xxx
     */
    public static String fixSingleSlashInProtocol(String url)
    {
        if (url == null)
            return null;

        Matcher matcher = fixSingleSlashInProtocolPattern.matcher(url);
        if (matcher.find())
            return matcher.replaceFirst(matcher.group(1).toLowerCase() + "://" + matcher.group(2));
        else
            return url;
    }

    // Extract timestamp part between archive prefix and archived URL
    private static String extractTimestamp(String url)
    {
        String remainder = null;

        if (url == null)
        {
            return null;
        }
        else if (url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTP))
        {
            remainder = url.substring(ARCHIVE_PREFIX_HTTP.length());
        }
        else if (url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTPS))
        {
            remainder = url.substring(ARCHIVE_PREFIX_HTTPS.length());
        }
        else
        {
            return null;
        }

        int slash = remainder.indexOf('/');
        if (slash == -1)
            return null;

        return remainder.substring(0, slash);
    }

    /**
     * Utility methods for recognizing Wayback-Machine (archive.org) capture URLs.
     *
     * <p>
     * Two URL shapes are recognized (after collapsing duplicate “/” characters):
     *
     * <pre>
     * Variant 1: /web/20160426180858/http:/REMAINDER
     *            /web/20160426180858/https:/REMAINDER
     *
     * Variant 2: /web/20160426180858cs_/http:/REMAINDER
     *            /web/20160426180858cs_/https:/REMAINDER
     * </pre>
     *
     * In Variant 2 the extra “cs_” can be any two lower-case letters followed by “_”. The timestamp (with or without the suffix) is
     * validated separately so the logic can be tweaked later if needed.
     */

    /** “/web/” + 14-digit timestamp + optional “aa_” + “/” + protocol */
    private static final Pattern ARCHIVE_ORG_URI_PATTERN = Pattern.compile(
            "^/web/(\\d{14})([a-z]{2}_)?/(https?:/).+",
            Pattern.CASE_INSENSITIVE);

    /**
     * Returns {@code true} iff the supplied {@code path} is a Wayback-Machine capture URL in one of the two recognised variants.
     */
    public static boolean isArchiveOrgUriPath(String path)
    {
        if (path == null || path.isEmpty())
            return false;

        // Collapse any “//” to "/"
        String normalised = path.replaceAll("/{2,}", "/");

        Matcher m = ARCHIVE_ORG_URI_PATTERN.matcher(normalised);
        if (!m.matches())
            return false;

        // Concatenate the 14-digit timestamp with the optional “aa_” suffix.
        String tsSegment = m.group(1) + (m.group(2) == null ? "" : m.group(2));
        return isArchiveOrgTimestamp(tsSegment);
    }

    /**
     * Validates a Wayback timestamp:
     * <ul>
     * <li>first 14 chars are digits (<code>yyyyMMddHHmmss</code>)</li>
     * <li>year ∈ [1990, 2100]</li>
     * <li>optionally followed by exactly two lower-case letters and “_”</li>
     * </ul>
     */
    private static boolean isArchiveOrgTimestamp(String s)
    {
        if (s == null || s.length() < 14)
        {
            return false;
        }

        // 1) 14 digits.
        for (int i = 0; i < 14; i++)
        {
            char c = s.charAt(i);
            if (c < '0' || c > '9')
            {
                return false;
            }
        }

        // 2) Plausible year.
        int year = Integer.parseInt(s.substring(0, 4));
        if (year < 1990 || year > 2100)
        {
            return false;
        }

        // 3) No suffix.
        if (s.length() == 14)
        {
            return true;
        }

        // 4) “aa_” suffix (two a-z + “_”).
        return s.length() == 17
                && Character.isLowerCase(s.charAt(14))
                && Character.isLowerCase(s.charAt(15))
                && s.charAt(16) == '_';
    }

    // Example usage
    public static void main(String[] args)
    {
        {
            String root = "https://web.archive.org/web/20160411084012/http://nationalism.org/";
            String url = "https://web.archive.org/web/20160404043545/http://nationalism.org/aziopa/berdyayev2.htm";
            System.out.println(urlMatchesRoot(url, root, false)); // true        
        }

        {
            String root = "https://web.archive.org/web/20160411084012/http://nationalism.org/";
            String url = "https://web.archive.org/web/20160404043545/http://www.nationalism.org/aziopa/berdyayev2.htm";
            System.out.println(urlMatchesRoot(url, root, true)); // true        
        }
    }
}
