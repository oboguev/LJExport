package my.LJExport.runtime.links.util;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.url.URLCodec;

public class LinkFilepathUtil
{
    public static String encodePathComponents(String ref)
    {
        StringBuilder sb = new StringBuilder();

        for (String pc : Util.asList(ref, "/"))
        {
            if (pc.length() != 0)
            {
                if (sb.length() != 0)
                    sb.append("/");
                sb.append(URLCodec.encode(pc));
            }
        }

        return sb.toString();
    }

    public static String decodePathComponents(String ref)
    {
        StringBuilder sb = new StringBuilder();

        for (String pc : Util.asList(ref, "/"))
        {
            if (pc.length() != 0)
            {
                if (sb.length() != 0)
                    sb.append("/");
                sb.append(URLCodec.decode(pc));
            }
        }

        return sb.toString();
    }
    
    public static String getMediaFileExtension(String fn)
    {
        int dotIndex = fn.lastIndexOf('.');

        // no extension or dot is at the end
        if (dotIndex == -1 || dotIndex == fn.length() - 1)
            return null;

        String ext = fn.substring(dotIndex + 1);
        if (ext.length() == 0 || ext.length() > 4)
            ext = null;
        
        // ### xhtml shtml
        
        return ext;
    }
}
