package my.LJExport.runtime;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.mutable.MutableBoolean;

/*
 * Generate safe file name
 */
public class SafeFileName
{
    // Common filename-invalid characters on Windows and also problematic in Linux
    private static final String ILLEGAL_CHARS = "*?<>\\/:\"|";
    private static final int MAX_PATH = 255; // Typical filesystem max filename length
    private static final int MAX_FILENAME_LENGTH = MAX_PATH - 50;

    public static String composeFileName(String seed, String suffix)
    {
        return composeFileName(seed, suffix, null);
    }
    
    public static String composeFileName(String seed, String suffix, MutableBoolean isGuid)
    {
        if (isGuid != null)
            isGuid.setFalse();
        
        // Edge case for "." and ".."
        if (seed.equals(".") || seed.equals("..") || seed.isEmpty())
            return guidFileName(suffix, isGuid);

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < seed.length(); i++)
        {
            char ch = seed.charAt(i);
            if (ILLEGAL_CHARS.indexOf(ch) >= 0 || ch <= 31)
            {
                // Control characters and illegal symbols are encoded
                byte[] bytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes)
                {
                    sb.append(String.format("%%%02X", b));
                }
            }
            else
            {
                sb.append(ch);
            }
        }

        String result = sb.toString();

        // Check total length
        if ((result + suffix).length() > MAX_FILENAME_LENGTH)
            return guidFileName(suffix, isGuid);

        return result + suffix;
    }

    private static String guidFileName(String suffix, MutableBoolean isGuid)
    {
        String v = guidFileName(suffix);
        if (isGuid != null)
            isGuid.setTrue();
        return v;
    }

    public static String guidFileName(String suffix)
    {
        return "x-" + Util.uuid() + suffix;
    }
    
    // usage example 
    public static void example_main(String[] args)
    {
        System.out.println(composeFileName("abc", ".html"));
        System.out.println(composeFileName("книга", ".html"));
        System.out.println(composeFileName("file?.txt", ".html"));
        System.out.println(composeFileName("..", ".html"));
    }
}
