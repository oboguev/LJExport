package my.LJExport.runtime.browsers;

public class BrowserVersion
{
    public String brand;
    public String versionString;
    public Integer[] version;

    @Override
    public String toString()
    {
        return brand + " + " + versionString + " + " + java.util.Arrays.toString(version);
    }

    public static BrowserVersion parse(String userAgent)
    {
        BrowserVersion result = new BrowserVersion();

        String[][] browserSignatures = {
                { "Firefox/", "Firefox" },
                { "Chrome/", "Chrome" },
                { "Edg/", "Edge" },
                { "Safari/", "Safari" } // Safari must come last due to presence in Chrome UA
        };

        for (String[] pair : browserSignatures)
        {
            String token = pair[0];
            int index = userAgent.indexOf(token);
            if (index >= 0)
            {
                int start = index + token.length();
                int end = start;
                while (end < userAgent.length() &&
                        (Character.isDigit(userAgent.charAt(end)) || userAgent.charAt(end) == '.'))
                {
                    end++;
                }
                result.brand = pair[1];
                result.versionString = userAgent.substring(start, end);
                result.version = parseVersionArray(result.versionString);
                return result;
            }
        }

        // Unknown
        result.brand = "Unknown";
        result.versionString = "";
        result.version = new Integer[0];
        return result;
    }

    /* ==================================================================================================== */

    private static Integer[] parseVersionArray(String versionStr)
    {
        String[] parts = versionStr.split("\\.");
        Integer[] version = new Integer[parts.length];
        for (int i = 0; i < parts.length; i++)
        {
            try
            {
                version[i] = Integer.parseInt(parts[i]);
            }
            catch (NumberFormatException e)
            {
                version[i] = 0;
            }
        }
        return version;
    }
    
    /* ==================================================================================================== */

    public static void main(String[] args)
    {
        String[] testUAs = {
                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
        };

        for (String ua : testUAs)
        {
            BrowserVersion bv = BrowserVersion.parse(ua);
            System.out.println(ua);
            System.out.println(" => " + bv);
        }
    }
}
