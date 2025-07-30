package my.WebArchiveOrg;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.url.UrlUtil;

public class ArchiveOrgUrl
{
    /*
     * Synopsis of archive.org URLs: 
     * 
     * https://web.archive.org/web/TIMESTAMP/ORIGINAL_URL
     * 
     *     Shows replay UI page.
     *     TIMESTAMP format: YYYYMMDDhhmmss
     *     Example: https://web.archive.org/web/20060215134634/http://www.trilateral.org/library/crisis_of_democracy.pdf
     * 
     * https://web.archive.org/web/TIMESTAMPif_/ORIGINAL_URL
     * 
     *     Direct download link for programmatic download of media (images, PDFs), but not HTML.
     *     Skips UI and serves the file.
     *     Examples: https://web.archive.org/web/20150324141543if_/http://samlib.ru/img/w/wasilxew_wjacheslaw_wasilxewich/ukragit91/referendum3.jpg
     *               https://web.archive.org/web/20060215134634if_/http://www.trilateral.org:80/library/crisis_of_democracy.pdf
     *     For images will return HTML with a single IMG.SRC link to im_ URL (below)          
     *     
     * https://web.archive.org/web/TIMESTAMPim_/ORIGINAL_URL
     * 
     *     Direct download link for programmatic download of images
     *     Skips UI and serves the file.
     *     Example: https://web.archive.org/web/20231201081812im_/https://1.bp.blogspot.com/_h_hLztz7W0s/Sq0s6CwFrJI/AAAAAAAADX4/xfV04qkGa1A/s1600/CheKa.JPG
     *     
     * https://web.archive.org/web/TIMESTAMPid_/ORIGINAL_URL
     * 
     *     Direct download link for programmatic download of HTML, but not media (images or PDFs).
     *     Directly serves HTML content without UI toolbar.
     *     Example:  https://web.archive.org/web/20221120034225id_/http://oboguev.net/misc/prisoed-yu-r.html
     *     
     *  https://web.archive.org/web/2id_/ORIGINAL_URL   
     *     
     *     Directly serves the latest HTML content without UI toolbar.
     *     Example:  https://web.archive.org/web/2id_/http://oboguev.net/misc/prisoed-yu-r.html
     *     Works reliably for HTML or images only, not for PDF or other MIMEs.
     * 
     * Correct form is inner http:// rather than http:/ although the latter may work sometimes too.
     * 
     *************************************************************************
     * 
     * There is also a search URL returning JSON:
     * 
     *     https://archive.org/wayback/available?url=http://www.trilateral.org:80/library/crisis_of_democracy.pdf
     *     
     * It may require the use of port.
     * Try combinations http, https, http:80 and https:443.     
     * 
     *************************************************************************
     *
     * https://archive.org/help/wayback_api.php
     * https://archive.readme.io/reference/website-snapshots
     * https://gist.github.com/say4n/869705e1081a2bdb276eb187476805bb
     * https://archive.org/developers/tutorial-get-snapshot-wayback.html
     * https://www.postman.com/api-evangelist/archive-org/documentation/4xvfnrj/wayback-api
     * 
     * https://publication.osintambition.org/5-basic-techniques-for-automating-investigations-using-the-wayback-machine-archive-org-3d1f2b8247d2
     * https://archive.org/developers/wayback-cdx-server.html
     * https://support.archive-it.org/hc/en-us/articles/115001790023-Access-Archive-It-s-Wayback-index-with-the-CDX-C-API
     * https://github.com/internetarchive/wayback/blob/master/wayback-cdx-server/README.md
     * https://github.com/internetarchive/wayback/tree/master/wayback-cdx-server
     *     Example: http://web.archive.org/cdx/search/cdx?url=archive.org&fl=timestamp,mimetype&output=json
     *
     */

    public static final String ARCHIVE_PREFIX_HTTPS = "https://web.archive.org/web/";
    public static final String ARCHIVE_PREFIX_HTTP = "http://web.archive.org/web/";

    public static final String ARCHIVE_SERVER = "https://web.archive.org";

    private static final String ArchiveOrgLatestCaptureWebRoot = "https://web.archive.org/web/2id_/";

    public static boolean isArchiveOrgUrl(String url)
    {
        String lc = url.toLowerCase();
        return lc.startsWith(ARCHIVE_PREFIX_HTTP) || lc.startsWith(ARCHIVE_PREFIX_HTTPS);
    }

    /**
     * Checks whether the given URL is a simple archive.org timestamp URL of the form:
     * https://web.archive.org/web/TIMESTAMP/ORIGINAL_URL
     * 
     * It excludes forms like:
     * - https://web.archive.org/web/TIMESTAMPif_/...
     * - https://web.archive.org/web/TIMESTAMPid_/...
     * - https://web.archive.org/web/TIMESTAMPim_/...
     * - https://web.archive.org/web/2id_/...
     * - any non-archive.org URLs
     *
     * @param url the URL to check
     * @return true if the URL is in simple TIMESTAMP form; false otherwise
     */
    /**
     * Checks whether the given URL is a simple archive.org timestamp URL of the form:
     * http[s]://web.archive.org/web/TIMESTAMP/ORIGINAL_URL
     *
     * It excludes forms like:
     * - TIMESTAMPif_/
     * - TIMESTAMPid_/
     * - 2id_/
     * - or any non-archive.org or malformed URLs
     *
     * @param url the URL to check
     * @return true if the URL is in simple TIMESTAMP form; false otherwise
     */
    /**
     * Checks whether the given URL is a simple archive.org timestamp URL of the form:
     * http[s]://web.archive.org/web/TIMESTAMP/ORIGINAL_URL
     *
     * It accepts only numeric TIMESTAMP without "id_", "if_", or any suffixes.
     * Rejects:
     * - TIMESTAMPid_/...
     * - TIMESTAMPif_/...
     * - 2id_/...
     * - Non-archive.org URLs
     *
     * @param url the URL to check
     * @return true if the URL is in pure TIMESTAMP form; false otherwise
     */
    public static boolean isArchiveOrgSimpleTimestampUrl(String url)
    {
        if (url == null || !isArchiveOrgUrl(url))
            return false;

        String prefix;
        if (url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTPS))
            prefix = ARCHIVE_PREFIX_HTTPS;
        else if (url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTP))
            prefix = ARCHIVE_PREFIX_HTTP;
        else
            return false;

        int pos = url.indexOf('/', prefix.length());
        if (pos == -1)
            return false;

        String timestamp = url.substring(prefix.length(), pos);

        // Only allow 14-digit numeric timestamps
        return timestamp.matches("\\d{14}");
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

    /*
     * Changes:
     *    HTTP:/xxx    → http://xxx
     *    Https:/xxx   → https://xxx
     *    http:/xxx    → http://xxx
     *    https:/xxx   → https://xxx
     * Leaves valid URLs (http://, https://) or invalid formats unchanged.
     */
    public static String fixSingleSlashInProtocol(String url)
    {
        if (url == null)
            return null;

        Matcher matcher = fixSingleSlashInProtocolPattern.matcher(url);
        if (matcher.find())
        {
            String protocol = matcher.group(1).toLowerCase(); // Force lowercase protocol
            return matcher.replaceFirst(protocol + "://" + matcher.group(2));
        }

        return url;
    }

    // (?i) = case-insensitive matching for protocol
    private static final Pattern fixSingleSlashInProtocolPattern = Pattern.compile("^(?i)(https?):/([^/])");

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
     * Returns {@code true} if the supplied {@code path} is a Wayback-Machine capture URL in one of the two recognised variants.
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

    public static String getLatestCaptureUrl(String original_href)
    {
        return ArchiveOrgUrl.ArchiveOrgLatestCaptureWebRoot + original_href;
    }

    /**
     * Converts a standard archive.org URL to a direct download URL.
     *
     * @param archiveOrgUrl Archive.org URL in format https://web.archive.org/web/TIMESTAMP/ORIGINAL_URL
     * @param isHtml true if you want HTML without toolbar (use "id_"), false for binary/media (use "if_")
     * @return direct download URL with "id_" or "if_" suffix inserted
     * @throws IllegalArgumentException if URL is not in expected format
     */
    /**
     * Converts a standard archive.org URL to a direct download URL.
     *
     * @param archiveOrgUrl Archive.org URL in format https://web.archive.org/web/TIMESTAMP/ORIGINAL_URL
     * @param isHtml true if you want HTML without toolbar (use "id_"), false for binary/media (use "if_")
     * @return direct download URL with "id_" or "if_" suffix inserted
     * @throws IllegalArgumentException if URL is not in expected format
     */
    public static String toDirectDownloadUrl(String archiveOrgUrl, boolean isHtml)
    {
        if (archiveOrgUrl == null)
            throw new IllegalArgumentException("Input URL is null");

        String url = archiveOrgUrl;

        if (!isArchiveOrgUrl(url))
            throw new IllegalArgumentException("Not an archive.org URL: " + url);

        String prefix;
        if (url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTPS))
            prefix = ARCHIVE_PREFIX_HTTPS;
        else if (url.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTP))
            prefix = ARCHIVE_PREFIX_HTTP;
        else
            throw new IllegalArgumentException("Archive URL must begin with supported prefix");

        int pos = url.indexOf('/', prefix.length());
        if (pos == -1)
            throw new IllegalArgumentException("Cannot locate TIMESTAMP portion in URL: " + url);

        String timestamp = url.substring(prefix.length(), pos);
        String suffix = url.substring(pos + 1);

        if (timestamp.endsWith("if_") || timestamp.endsWith("id_") || timestamp.endsWith("im_"))
            return url; // already direct download

        if (!isArchiveOrgTimestamp(timestamp))
            throw new IllegalArgumentException("Not a valid TIMESTAMP in URL: " + timestamp);

        return prefix + timestamp + (isHtml ? "id_/" : "if_/") + suffix;
    }

    public static String directDownloadUrl(String originalUrl, String timestamp, boolean isHtml)
    {
        return ARCHIVE_PREFIX_HTTPS + String.format("%s%s/%s", timestamp, isHtml ? "id_" : "if_", originalUrl);
    }

    private static final DateTimeFormatter ARCHIVE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    public static String timestamp(Instant instant)
    {
        return ARCHIVE_TIMESTAMP_FORMATTER.format(instant);
    }

    public static String timestamp(int yyyy, int month, int dd, int hh, int minute, int ss)
    {
        return timestamp(forTime(yyyy, month, dd, hh, minute, ss));
    }

    public static Instant forTime(int yyyy, int month, int dd, int hh, int minute, int ss)
    {
        LocalDateTime ldt = LocalDateTime.of(yyyy, month, dd, hh, minute, ss);
        return ldt.toInstant(ZoneOffset.UTC);
    }

    // https://web.archive.org/web/TIMESTAMPif_/ORIGINAL_URL
    public static String latestMediaUrl(String originalUrl, String timestamp)
    {
        return String.format("%s%sif_/%s", ARCHIVE_PREFIX_HTTPS, timestamp, originalUrl);
    }

    public static String decodeArchiveUrl(String archiveUrl)
    {
        String prefix;
        if (archiveUrl.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTP))
        {
            prefix = ARCHIVE_PREFIX_HTTP;
        }
        else if (archiveUrl.toLowerCase().startsWith(ARCHIVE_PREFIX_HTTPS))
        {
            prefix = ARCHIVE_PREFIX_HTTPS;
        }
        else
        {
            // Not an archive.org URL — decode everything
            return UrlUtil.decodeUrl(archiveUrl);
        }

        String rest = archiveUrl.substring(prefix.length());
        int sep = rest.indexOf('/');
        if (sep < 0)
            return archiveUrl; // malformed

        String timestamp = rest.substring(0, sep);
        String encodedOriginalUrl = rest.substring(sep + 1);

        String decodedOriginalUrl = UrlUtil.decodeUrl(encodedOriginalUrl);
        return prefix + timestamp + "/" + decodedOriginalUrl;
    }

    /* ====================================================================================== */

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

        {
            String in1 = "https://web.archive.org/web/20060215134634/http://www.trilateral.org/library/crisis_of_democracy.pdf";
            String in2 = "https://web.archive.org/web/20150324141543if_/http://samlib.ru/img/w/wasilxew_wjacheslaw_wasilxewich/ukragit91/referendum3.jpg";
            String in3 = "https://web.archive.org/web/20221120034225id_/http://oboguev.net/misc/prisoed-yu-r.html";

            printDirectDownloadUrl(in1, true);
            printDirectDownloadUrl(in1, false);

            printDirectDownloadUrl(in2, true);
            printDirectDownloadUrl(in2, false);

            printDirectDownloadUrl(in3, true);
            printDirectDownloadUrl(in3, false);
        }
    }

    private static void printDirectDownloadUrl(String in, boolean isHtml)
    {
        String out = toDirectDownloadUrl(in, false);
        Util.out("");
        Util.out("isHtml = " + isHtml);
        Util.out(in);
        Util.out(out);
    }
}
