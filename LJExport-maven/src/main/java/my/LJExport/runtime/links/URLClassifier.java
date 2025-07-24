package my.LJExport.runtime.links;

import java.net.URI;
import java.util.regex.Pattern;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class URLClassifier
{
    private static final Pattern WIKIPEDIA_PATTERN = Pattern.compile("^/(wiki/)(File|Image):.+", Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMONS_PATTERN = Pattern.compile("^/wiki/File:.+", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRADITIO_PATTERN = Pattern.compile("^/(Файл:).+", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PDRS_PATTERN = Pattern.compile("^/pedia/(Изображение:).+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /*
     * Returns true for "trap" pages like
     *     https://en.wikipedia.org/wiki/File:FD2.png
     *     https://en.wikipedia.org/wiki/Image:Upper_Paleolihic_Art_in_Europe.gif
     *     https://traditio.wiki/Файл:Окопная_правда.jpg 
     *     https://pdrs.dp.ua/pedia/Изображение:США1.jpg
     * that actually return HTML and not an image.
     */

    public static boolean isNonDocumentURL(String url)
    {
        if (url == null || url.isEmpty())
            return false;

        try
        {
            URI uri = new URI(url);
            return isNonDocumentURL(uri);
        }
        catch (Exception ex)
        {
            // Malformed URL, treat as not a known non-document trap
            return false;
        }
    }

    public static boolean isNonDocumentURL(URI uri)
    {
        if (uri == null)
            return false;

        String host = uri.getHost();
        if (host == null)
            return false;

        String path = uri.getRawPath(); // still percent-encoded
        if (path == null)
            return false;

        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);

        if (host.matches("(?i)^[a-z]+\\.wikipedia\\.org") && WIKIPEDIA_PATTERN.matcher(decodedPath).matches())
            return true;

        if (host.equalsIgnoreCase("commons.wikimedia.org") && COMMONS_PATTERN.matcher(decodedPath).matches())
            return true;

        if (host.equalsIgnoreCase("traditio.wiki") && TRADITIO_PATTERN.matcher(decodedPath).matches())
            return true;

        if (host.equalsIgnoreCase("pdrs.dp.ua") && PDRS_PATTERN.matcher(decodedPath).matches())
            return true;

        return false;
    }
}
