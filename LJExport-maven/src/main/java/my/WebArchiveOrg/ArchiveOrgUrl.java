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
