package my.LJExport.runtime.url;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

import my.LJExport.runtime.Util;

/**
 * Utility class for detecting and fixing percent-encoded byte sequences in URLs
 * that are invalid in UTF-8 but valid in Windows-1251 encoding.
 *
 * <p>This class scans the entire URL as a flat string and attempts to re-encode
 * contiguous %XX%XX... sequences that represent CP1251-encoded Cyrillic text,
 * converting them to valid UTF-8 percent-encoded sequences.
 *
 * <p>If the entire run of %XX bytes is not valid UTF-8, but valid CP1251, it is
 * transcoded to UTF-8 and fully percent-encoded (even for ASCII) to preserve opacity.
 */
public class UrlFixCP1251
{
    /**
     * Fixes a URL that may contain CP1251-encoded percent-escaped text, converting it to valid UTF-8 encoding.
     *
     * <p>This method scans the input for all contiguous percent-encoded byte sequences,
     * checks whether they decode as valid UTF-8. If not, it attempts to decode them
     * as Windows-1251 and re-encode the resulting string into UTF-8 percent-encoding.
     * 
     * Preserves full percent-encoding if the original run was entirely encoded.
     *
     * @param url the original URL string
     * @return the updated URL string with corrected UTF-8 encoding if needed; otherwise, returns the original
     */
    public static String fixUrlCp1251Sequences(String url)
    {
        if (url == null || !url.contains("%"))
            return url;

        Pattern pattern = Pattern.compile("(%[0-9A-Fa-f]{2})+");
        Matcher matcher = pattern.matcher(url);
        StringBuffer result = new StringBuffer();

        while (matcher.find())
        {
            String original = matcher.group();
            byte[] bytes = decodePercentSequence(original);
            if (bytes == null)
            {
                matcher.appendReplacement(result, Matcher.quoteReplacement(original));
                continue;
            }

            if (isValidUtf8(bytes))
            {
                matcher.appendReplacement(result, Matcher.quoteReplacement(original));
                continue;
            }

            try
            {
                // Decode using CP1251
                String decoded = new String(bytes, Charset.forName("windows-1251"));

                // Re-encode every character to UTF-8 %XX form
                byte[] utf8bytes = decoded.getBytes(StandardCharsets.UTF_8);
                StringBuilder fullEncoded = new StringBuilder();
                for (byte b : utf8bytes)
                {
                    fullEncoded.append('%');
                    fullEncoded.append(String.format("%02X", b & 0xFF));
                }

                matcher.appendReplacement(result, Matcher.quoteReplacement(fullEncoded.toString()));
            }
            catch (Exception e)
            {
                matcher.appendReplacement(result, Matcher.quoteReplacement(original));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static byte[] decodePercentSequence(String sequence)
    {
        try
        {
            int len = sequence.length() / 3;
            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++)
            {
                int idx = i * 3 + 1;
                bytes[i] = (byte) Integer.parseInt(sequence.substring(idx, idx + 2), 16);
            }
            return bytes;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static boolean isValidUtf8(byte[] bytes)
    {
        try
        {
            Charset.forName("UTF-8").newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /* ======================================================================== */

    // Example usage
    public static void main(String[] args)
    {
        if (Util.True)
        {
            String input = "http://ru.wikipedia.org/wiki/%CF%E0%EC%FF%F2%ED%E8%EA";
            String output = fixUrlCp1251Sequences(input);
            System.out.println("Input:  " + input);
            System.out.println("Output: " + output);

        }

        if (Util.True)
        {
            String input = "%2F%7A%7A%7A%2F%E9%E9%E9%2F%7A%7A%7A%E9%E9%E9%3F%7A%7A%7A";
            String output = fixUrlCp1251Sequences(input);
            System.out.println("Input:  " + input);
            System.out.println("Output: " + output);
        }
    }
}
