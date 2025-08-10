package my.LJExport.runtime.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.utils.XMLReaderUtils;

import my.LJExport.Config;
import my.LJExport.runtime.Util;

// image/png => png
// application/octet-stream => null

public class FileTypeDetector
{
    // private final static String MIME_OCTET_STREAM = "application/octet-stream";
    private final static String MIME_OCTET_STREAM = MediaType.OCTET_STREAM.toString();

    private static Integer maxtTikaThreads = null;

    /*
     * Maximum number of threads that can simultaneously call Tika
     */
    public static void prepareThreading(int nMaxTikaThreads) throws Exception
    {
        // System.setProperty("XMLReaderUtils.POOL_SIZE", String.format("%d", nTikaMaxThreads + 10));

        if (maxtTikaThreads == null || maxtTikaThreads < nMaxTikaThreads)
        {
            XMLReaderUtils.setPoolSize(nMaxTikaThreads + 10);
            maxtTikaThreads = nMaxTikaThreads;
        }
    }

    public static String fileExtensionFromActualFileContent(byte[] fileBytes, String pathExtension) throws Exception
    {
        String detectedMimeType = mimeTypeFromActualFileContent(fileBytes, pathExtension);

        if (detectedMimeType == null)
            return null;

        if (!detectedMimeType.equalsIgnoreCase(MIME_OCTET_STREAM))
            return fileExtensionFromMimeType(detectedMimeType);

        return null;
    }

    private static final Tika tika = new Tika();
    private static final MimeTypes allMimeTypes = MimeTypes.getDefaultMimeTypes();

    public static String mimeTypeFromActualFileContent(byte[] fileBytes, String pathExtension) throws Exception
    {
        if (fileBytes == null)
            return null;

        String detectedMimeType = tika.detect(fileBytes);
        if (!detectedMimeType.equalsIgnoreCase(MIME_OCTET_STREAM))
            return detectedMimeType;

        if (Config.False)
        {
            detectedMimeType = detectMimeTypeWithNio(fileBytes);
            if (detectedMimeType != null && !detectedMimeType.equalsIgnoreCase(MIME_OCTET_STREAM))
                return detectedMimeType;
        }

        if (isDjvu(fileBytes))
            return "image/vnd.djvu";

        if (isRAR(fileBytes))
            return "application/vnd.rar";

        if (pathExtension != null && pathExtension.equalsIgnoreCase("txt"))
        {
            if (shareOfUnprintableBytes(fileBytes) * 100 < 0.5)
                return "text/plain";
        }

        if (fileBytes.length > 100 && isAsciiText(fileBytes))
            return "plain/text";

        if (shareOfUnprintableBytes(fileBytes) * 100 < 0.5)
        {
            String fileAsciiText = new String(fileBytes, java.nio.charset.StandardCharsets.US_ASCII);
            if (fileAsciiText.contains("<style>") && fileAsciiText.contains("<div "))
                return "text/plain";
        }

        return MIME_OCTET_STREAM;
    }

    private static String detectMimeTypeWithNio(byte[] fileBytes) throws Exception
    {
        Path tempFile = Files.createTempFile("mimecheck-", ".tmp");
        try
        {
            Files.write(tempFile, fileBytes);
            return Files.probeContentType(tempFile);
        }
        finally
        {
            Files.deleteIfExists(tempFile);
        }
    }

    public static String fileExtensionFromMimeType(String mimeType, String url) throws Exception
    {
        try
        {
            return fileExtensionFromMimeType(mimeType);
            
        }
        catch (Exception ex)
        {
            throw new Exception("While processing URL " + url, ex);
        }
    }

    public static String fileExtensionFromMimeType(String mimeType) throws Exception
    {
        if (mimeType == null || mimeType.equalsIgnoreCase(MIME_OCTET_STREAM))
            return null;

        String lc = mimeType.toLowerCase();

        switch (lc)
        {
        /*
         * Invalid fromat of server Content-Type header  
         */
        case "jpg":
        case "jpeg":
            return "jpg";
        }

        MimeType mimeTypeInfo = allMimeTypes.forName(mimeType);
        String extension = mimeTypeInfo.getExtension(); // e.g. ".svg"

        if (extension != null)
        {
            extension = extension.toLowerCase();
            if (extension.startsWith("."))
                extension = extension.substring(1);

            switch (extension)
            {
            case "gtar":
                extension = "tar";
                break;

            case "tiff":
                extension = "tif";
                break;
            }

            if (extension.equals("bin") || extension.isBlank())
                extension = null;

        }

        if (extension == null)
        {
            switch (lc)
            {
            case "image/x-djvu":
            case "image/vnd.djvu":
                return "djvu";

            case "application/vnd.rar":
                return "rar";

            default:
                break;
            }

            if (lc.startsWith("application/x-rar-compressed"))
                return "rar";

            if (lc.startsWith("text/"))
                return "txt";
        }

        return extension;
    }

    /* ==================================================================================== */

    private static final Map<String, String> canonicalExtensionMap = new HashMap<>();
    private static final Set<String> commonExtensions;

    static
    {
        // Image formats
        canonicalExtensionMap.put("jpg", "jpg");
        canonicalExtensionMap.put("jpeg", "jpg");
        canonicalExtensionMap.put("png", "png");
        canonicalExtensionMap.put("gif", "gif");
        canonicalExtensionMap.put("bmp", "bmp");
        canonicalExtensionMap.put("dib", "bmp");
        canonicalExtensionMap.put("tif", "tiff");
        canonicalExtensionMap.put("tiff", "tiff");
        canonicalExtensionMap.put("webp", "webp");
        canonicalExtensionMap.put("ico", "ico");
        canonicalExtensionMap.put("icon", "ico");
        canonicalExtensionMap.put("svg", "svg");

        // Audio formats
        canonicalExtensionMap.put("mp3", "mp3");
        canonicalExtensionMap.put("wav", "wav");
        canonicalExtensionMap.put("wave", "wav");
        canonicalExtensionMap.put("aif", "aiff");
        canonicalExtensionMap.put("aiff", "aiff");
        canonicalExtensionMap.put("ogg", "ogg");
        canonicalExtensionMap.put("oga", "ogg");
        canonicalExtensionMap.put("flac", "flac");
        canonicalExtensionMap.put("m4a", "m4a");
        canonicalExtensionMap.put("mpa", "mp2");
        canonicalExtensionMap.put("mp2", "mp2");

        // Video formats
        canonicalExtensionMap.put("mp4", "mp4");
        canonicalExtensionMap.put("m4v", "mp4");
        canonicalExtensionMap.put("mov", "mov");
        canonicalExtensionMap.put("qt", "mov");
        canonicalExtensionMap.put("avi", "avi");
        canonicalExtensionMap.put("mpg", "mpeg");
        canonicalExtensionMap.put("mpeg", "mpeg");
        canonicalExtensionMap.put("mkv", "mkv");
        canonicalExtensionMap.put("mk3d", "mkv");
        canonicalExtensionMap.put("mka", "mkv");
        canonicalExtensionMap.put("flv", "flv");
        canonicalExtensionMap.put("f4v", "flv");
        canonicalExtensionMap.put("webm", "webm");

        // HTML
        canonicalExtensionMap.put("html", "html");
        canonicalExtensionMap.put("htm", "html");
        canonicalExtensionMap.put("xhtml", "html");
        canonicalExtensionMap.put("shtml", "html");
        canonicalExtensionMap.put("xhtm", "html");
        canonicalExtensionMap.put("shtm", "html");

        // archives
        canonicalExtensionMap.put("tgz", "gz");
        canonicalExtensionMap.put("gz", "gz");

        commonExtensions = makeCommonExtensions();
    }

    public static boolean isEquivalentExtensions(String ext1, String ext2)
    {
        if (ext1 == null || ext2 == null)
            return false;

        ext1 = ext1.toLowerCase(Locale.ROOT);
        ext2 = ext2.toLowerCase(Locale.ROOT);
        if (ext1.equals(ext2))
            return true;

        String norm1 = canonicalExtensionMap.get(ext1);
        String norm2 = canonicalExtensionMap.get(ext2);
        return norm1 != null && norm2 != null && norm1.equals(norm2);
    }

    public static boolean isImagePath(String path)
    {
        if (path == null)
            return false;

        String lc = path.toLowerCase();

        return lc.endsWith(".avif") ||
               lc.endsWith(".bmp") ||
               lc.endsWith(".dib") ||
               lc.endsWith(".gif") ||
               lc.endsWith(".ico") ||
               lc.endsWith(".icon") ||
               lc.endsWith(".jpeg") ||
               lc.endsWith(".jpg") ||
               lc.endsWith(".png") ||
               lc.endsWith(".svg") ||
               lc.endsWith(".tif") ||
               lc.endsWith(".tiff") ||
               lc.endsWith(".webp");
    }

    public static boolean isImageExtension(String ext)
    {
        if (ext == null)
            return false;

        switch (ext.toLowerCase())
        {
        case "avif":
        case "bmp":
        case "dib":
        case "gif":
        case "ico":
        case "icon":
        case "jpeg":
        case "jpg":
        case "png":
        case "svg":
        case "tif":
        case "tiff":
        case "webp":
            return true;

        default:
            return false;
        }
    }

    public static boolean isHtmlExtension(String ext)
    {
        if (ext == null)
            return false;

        switch (ext.toLowerCase())
        {
        case "html":
        case "htm":
        case "xhtml":
        case "shtml":
        case "xhtm":
        case "shtm":
            return true;

        default:
            return false;
        }
    }

    public static boolean isServletExtension(String ext)
    {
        if (ext == null)
            return false;

        switch (ext.toLowerCase())
        {
        case "action":
        case "api":
        case "asp":
        case "aspx":
        case "cgi":
        case "dll":
        case "do":
        case "exe":
        case "fcgi":
        case "jsp":
        case "php":
        case "php3":
        case "php4":
        case "phtml":
        case "pl":
        case "py":
        case "rb":
        case "svc":
            return true;

        default:
            return false;
        }
    }

    /* ==================================================================================== */

    public static Set<String> commonExtensions()
    {
        return commonExtensions;
    }

    private static Set<String> makeCommonExtensions()
    {
        Set<String> xs = new HashSet<>();

        xs.addAll(canonicalExtensionMap.keySet());
        xs.addAll(canonicalExtensionMap.values());
        xs.addAll(Config.DownloadFileTypes);

        xs.add("7z");
        xs.add("gz");
        xs.add("rar");
        xs.add("tar");
        xs.add("tgz");
        xs.add("xz");
        xs.add("zip");

        xs.add("djvu");
        xs.add("doc");
        xs.add("docx");
        xs.add("odt");
        xs.add("pdf");
        xs.add("rtf");
        xs.add("txt");

        xs.add("htm");
        xs.add("html");
        xs.add("xhtml");
        xs.add("php");

        xs.add("avif");
        xs.add("bmp");
        xs.add("gif");
        xs.add("jpeg");
        xs.add("jpg");
        xs.add("png");
        xs.add("svg");
        xs.add("tif");
        xs.add("tiff");
        xs.add("webp");

        xs.add("mp4");
        xs.add("qt");

        return xs;
    }

    /* ==================================================================================== */

    /*
     * Share of bytes with unprintable characters, 0 to 1.
     * Can be used on KOI-8 or Win-1251 text files.
     */
    private static double shareOfUnprintableBytes(byte[] content)
    {
        if (content == null || content.length == 0)
            return 0.0; // define empty content as fully printable

        int unprintable = 0;
        int total = content.length;

        for (int i = 0; i < total; i++)
        {
            int b = content[i] & 0xFF;

            // Accept ASCII printable and whitespace
            if ((b >= 32 && b <= 126) || b == 9 || b == 10 || b == 13)
                continue;

            // Accept common printable range for KOI8-R and CP1251
            if (b >= 128 && b <= 255)
                continue;

            if (Util.False && b == 127)
                continue;

            unprintable++;
        }

        return (double) unprintable / total;
    }

    private static boolean isDjvu(byte[] d)
    {
        if (d.length < 88)
            return false;

        return d[0] == 'A' && d[1] == 'T' && d[2] == '&' && d[3] == 'T' &&
               d[12] == 'D' && d[13] == 'J' && d[14] == 'V';
    }

    private static boolean isRAR(byte[] d)
    {
        return isRAR50(d) || isRAR47(d);
    }

    private static boolean isRAR50(byte[] d)
    {
        return d.length >= 100 && beginsWith(d, 0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00);
    }

    private static boolean isRAR47(byte[] d)
    {
        return d.length >= 20 && beginsWith(d, 0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00);
    }

    private static boolean beginsWith(byte[] d, int... sig)
    {
        if (d.length < sig.length)
            return false;

        for (int k = 0; k < sig.length; k++)
        {
            if (d[k] != sig[k])
                return false;
        }

        return true;
    }

    public static boolean isAsciiText(byte[] d)
    {
        if (d == null)
            return false;

        for (byte b : d)
        {
            int value = b & 0xFF;
            if (value >= 0x20 && value <= 0x7E || value == 0x09 || value == 0x0A || value == 0x0D)
                continue; // valid
            return false; // invalid byte
        }

        return true;
    }
}
