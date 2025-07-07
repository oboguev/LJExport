package my.WebArchiveOrg;

public class ArchiveOrgUrl
{
    private static final String ARCHIVE_PREFIX = "https://web.archive.org/web/";

    /*
     * Check for matching except the timestamp, e.g.:
     * https://web.archive.org/web/20160404043545/http://nationalism.org/aziopa/berdyayev2.htm
     * https://web.archive.org/web/20160411084012/http://nationalism.org/
     */
    public static boolean urlMatchesRoot(String url, String archiveOrgRoot, boolean allowWwwDiffer)
    {
        if (url == null || archiveOrgRoot == null)
            return false;

        if (!url.startsWith(ARCHIVE_PREFIX) || !archiveOrgRoot.startsWith(ARCHIVE_PREFIX))
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

        return ARCHIVE_PREFIX + timestamp + "/" + rootRest;
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
    private static String extractArchivedUrlPart(String url)
    {
        if (url == null || !url.startsWith(ARCHIVE_PREFIX))
            return null;
        String remainder = url.substring(ARCHIVE_PREFIX.length());
        int slash = remainder.indexOf('/');
        if (slash == -1)
            return null;
        String result = remainder.substring(slash + 1);
        return result;
    }

    // Extract timestamp part between archive prefix and archived URL
    private static String extractTimestamp(String url)
    {
        if (url == null || !url.startsWith(ARCHIVE_PREFIX))
            return null;
        String remainder = url.substring(ARCHIVE_PREFIX.length());
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
