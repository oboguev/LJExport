package my.LJExport.runtime;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/*
 * Восстанавливать текст в кодировке Windows-1251 скопированный как UTF-8
 */
public class RussianTextRecovery
{
    // Pattern to detect likely garbled Russian text (sequences of ? and some valid UTF-8 chars)
    private static final Pattern GARBLED_RUSSIAN_PATTERN = Pattern.compile("([?]{2,}|[ÐÂÐ¾Ð°-Ñ][^a-zA-Z0-9\\s]*)");

    // Check if text is likely garbled Russian
    public static boolean isLikelyGarbledRussian(String text)
    {
        if (text == null || text.isEmpty())
            return false;

        // Check for common garbled sequences
        return GARBLED_RUSSIAN_PATTERN.matcher(text).find();
    }

    // Recover garbled Russian text
    public static String recoverGarbledRussian(String garbledText)
    {
        if (garbledText == null || !isLikelyGarbledRussian(garbledText))
            return garbledText;

        try
        {
            // Get the bytes that were incorrectly interpreted as UTF-8
            byte[] misinterpretedBytes = garbledText.getBytes(StandardCharsets.UTF_8);

            // Reinterpret those bytes as the original Windows-1251 encoding
            return new String(misinterpretedBytes, Charset.forName("windows-1251"));
        }
        catch (Exception e)
        {
            // If recovery fails, return original text
            return garbledText;
        }
    }
}