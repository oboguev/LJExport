package my.LJExport.runtime.url;

import java.util.Base64;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.LJExport.runtime.file.FileTypeDetector;

public class EmbeddedDataURL
{
    public final String mediaType;
    public final String contextFileExtension;
    public final byte[] data;

    private EmbeddedDataURL(String mediaType, String contextFileExtension, byte[] data)
    {
        this.mediaType = mediaType;
        this.contextFileExtension = contextFileExtension;
        this.data = data;
    }

    /**
     * Parses a data: URL (e.g. from an <img src="..."> attribute) and returns
     * an EmbeddedDataURL object with media type and decoded binary data.
     *
     * @param imgSrcValue The string value of the "src" attribute, must start with "data:"
     * @return EmbeddedDataURL object, or null if the input is not a valid data URL
     */
    public static EmbeddedDataURL decodeImgSrc(String imgSrcValue) throws Exception
    {
        if (imgSrcValue == null || !imgSrcValue.startsWith("data:"))
            return null;

        // Regex: data:[<mediatype>][;base64],<data>
        Pattern pattern = Pattern.compile("^data:([^;,]+)?(?:;(base64))?,(.*)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(imgSrcValue);

        if (!matcher.matches())
            return null;
        
        boolean missingMediaType = false;

        String mediaType = matcher.group(1);
        if (mediaType == null || mediaType.isEmpty())
        {
            mediaType = "text/plain"; // default per RFC 2397
            missingMediaType = true;
        }

        boolean isBase64 = matcher.group(2) != null;
        String dataPart = matcher.group(3);

        byte[] rawData;
        try
        {
            if (isBase64)
            {
                rawData = Base64.getDecoder().decode(dataPart);
            }
            else
            {
                String decoded = URLDecoder.decode(dataPart, StandardCharsets.UTF_8.name());
                rawData = decoded.getBytes(StandardCharsets.UTF_8);
            }
            
            String contextFileExtension = null;
            if (missingMediaType)
            {
                contextFileExtension = FileTypeDetector.fileExtensionFromActualFileContent(rawData, null);
            }
            else
            {
                contextFileExtension = FileTypeDetector.fileExtensionFromMimeType(mediaType);
            }

            return new EmbeddedDataURL(mediaType, contextFileExtension, rawData);
        }
        catch (IllegalArgumentException | java.io.UnsupportedEncodingException e)
        {
            return null;
        }
    }
}