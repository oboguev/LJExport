package my.LJExport.runtime.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/*
 * Build HTTP body for form POST request
 */
public class FormPost
{
    public static String body(Map<String, String> params)
    {
        StringBuilder sb = new StringBuilder();
        
        for (String key : params.keySet())
        {
            if (sb.length() != 0)
                sb.append("&");
            sb.append(escape(key) + "=" + escape(params.get(key)));
        }
        
        return sb.toString();
    }
    
    private static String escape(String s)
    {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
