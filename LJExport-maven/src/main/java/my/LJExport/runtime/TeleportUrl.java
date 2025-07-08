package my.LJExport.runtime;

public class TeleportUrl
{
    public static String ungarbleTeleportUrl(String url) throws Exception
    {
        if (url != null)
        {
            url = ungarbleTeleportPro(url);
            url = ungarbleJavaScriptTeleport(url);
        }

        return url;
    }

    /*
     * Recover URLs garbled in a specific way by Teleport Pro.
     * 
     * https://web.archive.org/web/20030914114836/http://nationalism.org/library/publicism/holmogorov/holmogorov-specnaz-2002-01-1.htm%20%20%5Cn%5CnThis%20file%20was%20not%20retrieved%20by%20Teleport%20Pro,%20because%20it%20is%20addressed%20on%20a%20domain%20or%20path%20outside%20the%20boundaries%20set%20for%20its%20Starting%20Address.%20%20%5Cn%5CnDo%20you%20want%20to%20open%20it%20from%20the%20server?%27))window.location=%27http://nationalism.org/library/publicism/holmogorov/holmogorov-specnaz-2002-01-1.htm
     * https://web.archive.org/web/20110102033831/http://nationalism.org/forum/06/1081.shtml%20%20%5Cn%5CnThis%20file%20was%20not%20retrieved%20by%20Teleport%20Pro,%20because%20it%20is%20addressed%20on%20a%20domain%20or%20path%20outside%20the%20boundaries%20set%20for%20its%20Starting%20Address.%20%20%5Cn%5CnDo%20you%20want%20to%20open%20it%20from%20the%20server?%27))window.location=%27http://nationalism.org/forum/06/1081.shtml
     * https://web.archive.org/web/20031028005746/http://nationalism.org/pioneer/intellfenomen_1.htm%20%20%5Cn%5CnThis%20file%20was%20not%20retrieved%20by%20Teleport%20Pro,%20because%20it%20is%20addressed%20on%20a%20domain%20or%20path%20outside%20the%20boundaries%20set%20for%20its%20Starting%20Address.%20%20%5Cn%5CnDo%20you%20want%20to%20open%20it%20from%20the%20server?%27))window.location=%27http://nationalism.org/pioneer/intellfenomen_1.htm
     */
    private static String ungarbleTeleportPro(String url)
    {
        if (url == null)
            return null;

        // Look for the first occurrence of "%20" followed by the Teleport Pro notice
        int garbleIndex = url.indexOf("%20%20%5Cn%5CnThis%20file%20was%20not%20retrieved%20by%20Teleport%20Pro");
        if (garbleIndex != -1)
        {
            // Cut off everything after the legitimate URL
            return url.substring(0, garbleIndex);
        }

        // Fallback: look for the embedded JavaScript URL assignment
        int redirectIndex = url.indexOf("window.location=%27http");
        if (redirectIndex != -1)
        {
            int start = url.indexOf("http", redirectIndex);
            int end = url.indexOf("'", start);
            if (start != -1 && end != -1)
            {
                return url.substring(0, url.indexOf("http")) + url.substring(start, end);
            }
        }

        // No garble pattern detected, return the original URL
        return url;
    }

    /*
     * Recover URL from:
     * 
     * javascript:if(confirm('https://web.archive.org/web/20160426180858/http://nationalism.org/forum/read.php?f=1&i=247&t=149  \n\nThis file was not retrieved by Teleport Pro, because it was unavailable, or its retrieval was aborted, or the project was stopped too soon.  \n\nDo you want to open it from the server?'))window.location='https://web.archive.org/web/20160426180858/http://nationalism.org/forum/read.php?f=1&i=247&t=149'
     * javascript:if(confirm('https://web.archive.org/web/20030731173030/http://nationalism.org/bratstvo/oprichnik/23/eliseev.htm  \n\nThis file was not retrieved by Teleport Pro, because it is addressed on a domain or path outside the boundaries set for its Starting Address.  \n\nDo you want to open it from the server?'))window.location='https://web.archive.org/web/20030731173030/http://nationalism.org/bratstvo/oprichnik/23/eliseev.htm'
     * javascript:if(confirm('https://web.archive.org/web/20030701041900/http://forum.nationalism.org/07/read.php?f=1&i=16&t=16  \n\nThis file was not retrieved by Teleport Pro, because it is addressed on a domain or path outside the boundaries set for its Starting Address.  \n\nDo you want to open it from the server?'))window.location='https://web.archive.org/web/20030701041900/http://forum.nationalism.org/07/read.php?f=1&i=16&t=16'
     * javascript:if(confirm('https://web.archive.org/web/20031025034701/http://info.study.ru/?open=3b4a9b4f.4c7a  \n\nThis file was not retrieved by Teleport Pro, because it is addressed on a domain or path outside the boundaries set for its Starting Address.  \n\nDo you want to open it from the server?'))window.location='https://web.archive.org/web/20031025034701/http://info.study.ru/?open=3b4a9b4f.4c7a#0'
     * javascript:if(confirm('https://web.archive.org/web/20031025034701/http://zavtra.ru/cgi/veil/data/zavtra/01/399/51.html  \n\nThis file was not retrieved by Teleport Pro, because it is addressed on a domain or path outside the boundaries set for its Starting Address.  \n\nDo you want to open it from the server?'))window.location='https://web.archive.org/web/20031025034701/http://zavtra.ru/cgi/veil/data/zavtra/01/399/51.html'
     */
    private static String ungarbleJavaScriptTeleport(String url) throws Exception
    {
        if (url == null)
            return null;
        if (!url.startsWith("javascript:if(confirm('"))
            return url;

        String fromConfirm = extractFromConfirm(url);
        String fromLocation = extractFromLocation(url);

        if (Util.stripAnchor(fromConfirm).equals(Util.stripAnchor(fromLocation)))
            return fromLocation;
        
        if (fromConfirm.startsWith(Util.stripAnchor(fromLocation) + "  \\n\\nThis file was not retrieved by Teleport Pro"))
            return fromLocation;
        
        throw new IllegalArgumentException("Mismatch between confirm and location URL:\n"
                + "Confirm:  " + fromConfirm + "\n"
                + "Location: " + fromLocation);
    }

    private static String extractFromConfirm(String url)
    {
        String prefix = "javascript:if(confirm('";
        int start = prefix.length();
        int end = url.indexOf("'))window.location='");

        if (start < 0 || end < 0 || end <= start)
            throw new IllegalArgumentException("Invalid confirm pattern in input: " + url);

        String confirmBlock = url.substring(start, end);
        int newlineIndex = confirmBlock.indexOf('\n');
        if (newlineIndex > 0)
            return confirmBlock.substring(0, newlineIndex).trim();

        return confirmBlock.trim();
    }

    private static String extractFromLocation(String url)
    {
        String marker = "'))window.location='";

        int start = url.indexOf(marker);
        if (start < 0)
            throw new IllegalArgumentException("Invalid location pattern in input: " + url);
        
        start += marker.length();
        int end = url.lastIndexOf('\'');
        if (end <= start)
            throw new IllegalArgumentException("Malformed window.location URL: " + url);
        
        return url.substring(start, end).trim();
    }
}
