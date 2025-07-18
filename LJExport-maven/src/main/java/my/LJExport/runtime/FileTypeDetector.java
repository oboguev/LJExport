package my.LJExport.runtime;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;

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
        MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
        MimeType mimeTypeInfo = allTypes.forName(mimeType);
        String extension = mimeTypeInfo.getExtension(); // e.g. ".svg"
        return extension;
    }
}
