package my.LJExport.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;

// image/png => png
// application/octet-stream => null

public class FileTypeDetector
{
    // private final static String MIME_OCTET_STREAM = "application/octet-stream";
    private final static String MIME_OCTET_STREAM = MediaType.OCTET_STREAM.toString();

    public static String fileExtensionFromActualFileContent(byte[] fileBytes) throws Exception
    {
        String detectedMimeType = mimeTypeFromActualFileContent(fileBytes);
        return fileExtensionFromMimeType(detectedMimeType);
    }

    public static String mimeTypeFromActualFileContent(byte[] fileBytes) throws Exception
    {
        Tika tika = new Tika();
        String detectedMimeType = tika.detect(fileBytes);
        if (!detectedMimeType.equalsIgnoreCase(MIME_OCTET_STREAM))
            return detectedMimeType;

        detectedMimeType = detectMimeTypeWithNio(fileBytes);
        if (detectedMimeType != null)
            return detectedMimeType;

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

    public static String fileExtensionFromMimeType(String mimeType) throws Exception
    {
        if (mimeType.equalsIgnoreCase(MIME_OCTET_STREAM))
            return null;

        MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
        MimeType mimeTypeInfo = allTypes.forName(mimeType);
        String extension = mimeTypeInfo.getExtension(); // e.g. ".svg"
        if (extension != null)
        {
            extension = extension.toLowerCase();
            if (extension.startsWith("."))
                extension = extension.substring(1);
            if (extension.equals("bin") || extension.isBlank())
                extension = null;
        }
        return extension;
    }

    /* ==================================================================================== */

    private static final Map<String, String> canonicalExtensionMap = new HashMap<>();
    
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
    }

    public static boolean isEquivalentExtensions(String ext1, String ext2)
    {
        if (ext1 == null || ext2 == null)
            return false;
        ext1 = ext1.toLowerCase(Locale.ROOT);
        ext2 = ext2.toLowerCase(Locale.ROOT);
        String norm1 = canonicalExtensionMap.get(ext1);
        String norm2 = canonicalExtensionMap.get(ext2);
        return norm1 != null && norm2 != null && norm1.equals(norm2);
    }
}
