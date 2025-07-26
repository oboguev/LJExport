package my.LJExport.runtime.http;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MiscUrls
{
    public static String unwrapImagesGoogleCom(String url)
    {
        try
        {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();

            if (host == null || path == null)
                return url;

            // Normalize host
            String lowerHost = host.toLowerCase();
            if (!(lowerHost.equals("images.google.com") || lowerHost.endsWith(".images.google.com")))
                return url;

            if (!path.equals("/imgres"))
                return url;

            String query = uri.getRawQuery(); // use rawQuery to preserve % encoding
            if (query == null)
                return url;

            // Find imgurl parameter
            Pattern p = Pattern.compile("(^|&)" + "imgurl=([^&]+)");
            Matcher m = p.matcher(query);
            if (m.find())
            {
                String encodedImgUrl = m.group(2);
                return URLDecoder.decode(encodedImgUrl, StandardCharsets.UTF_8.name());
            }

            return url;
        }
        catch (Exception e)
        {
            return url;
        }
    }
}