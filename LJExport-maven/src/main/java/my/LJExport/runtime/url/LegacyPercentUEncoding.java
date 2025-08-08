package my.LJExport.runtime.url;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import my.LJExport.runtime.Util;

/**
 * Converts legacy "%uXXXX" style escapes (old JS/IE) to RFC-compliant UTF-8 percent-encoding,
 * but only when XXXX is a 4-hex value within [minChar..maxChar].
 */
public class LegacyPercentUEncoding
{
    private final int minChar;
    private final int maxChar;
    
    public static String normalizeEncodedSafe(String encoded)
    {
        LegacyPercentUEncoding lpu = new LegacyPercentUEncoding();
        if (lpu.count(encoded) >= 3)
            encoded = lpu.normalizeEncodedAny(encoded);
        return encoded;
    }

    private LegacyPercentUEncoding()
    {
        this(0x0410, 0x0490);
    }

    /**
     * @param minChar inclusive lower bound (0x0000..0xFFFF)
     * @param maxChar inclusive upper bound (0x0000..0xFFFF)
     * @throws IllegalArgumentException if bounds are invalid or out of BMP range
     */
    private LegacyPercentUEncoding(int minChar, int maxChar)
    {
        if (minChar < 0x0000 || maxChar > 0xFFFF || minChar > maxChar)
        {
            throw new IllegalArgumentException("minChar/maxChar must be within 0x0000..0xFFFF and min<=max");
        }
        this.minChar = minChar;
        this.maxChar = maxChar;
    }

    /**
     * Counts occurrences of %uXXXX that are within the configured range.
     */
    public int count(String s)
    {
        Objects.requireNonNull(s, "s");
        int n = 0;
        final int len = s.length();
        for (int i = 0; i + 5 < len; i++)
        { // need at least 6 chars: % u XXXX
            if (s.charAt(i) == '%' && (s.charAt(i + 1) == 'u'))
            {
                int v = hex4OrMinus1(s, i + 2);
                if (v >= 0 && v >= minChar && v <= maxChar && !isSurrogate(v))
                {
                    n++;
                    i += 5; // skip over "%uXXXX"
                }
            }
        }
        return n;
    }

    /**
     * Rewrites %uXXXX (within range) to UTF-8 percent-encoding (%XX...).
     * Leaves all other text untouched.
     * 
     * The input URL must be in "encoded" status, in case it contains mixed %XX and %uXXXX.
     * Output is in the encoded status. 
     */
    public String normalizeEncodedAny(String s)
    {
        Objects.requireNonNull(s, "s");
        final int len = s.length();
        StringBuilder out = new StringBuilder(len + 16); // small headroom
        int i = 0;
        while (i < len)
        {
            if (i + 5 < len && s.charAt(i) == '%' && (s.charAt(i + 1) == 'u'))
            {
                int v = hex4OrMinus1(s, i + 2);
                if (v >= 0 && v >= minChar && v <= maxChar && !isSurrogate(v))
                {
                    // Convert this code point to UTF-8 bytes and percent-encode each byte
                    byte[] utf8 = new String(Character.toChars(v)).getBytes(StandardCharsets.UTF_8);
                    for (byte b : utf8)
                    {
                        out.append('%');
                        out.append(toHexDigit((b >>> 4) & 0xF));
                        out.append(toHexDigit(b & 0xF));
                    }
                    i += 6; // consumed "%uXXXX"
                    continue;
                }
            }
            out.append(s.charAt(i++));
        }
        return out.toString();
    }

    // ---- helpers ----

    private static boolean isSurrogate(int v)
    {
        return v >= 0xD800 && v <= 0xDFFF;
    }

    /**
     * Parses four hex digits at position 'pos'. Returns -1 if any char isn't hex.
     */
    private static int hex4OrMinus1(String s, int pos)
    {
        int v0 = hexValOrMinus1(s.charAt(pos));
        int v1 = hexValOrMinus1(s.charAt(pos + 1));
        int v2 = hexValOrMinus1(s.charAt(pos + 2));
        int v3 = hexValOrMinus1(s.charAt(pos + 3));
        if ((v0 | v1 | v2 | v3) < 0)
            return -1;
        return (v0 << 12) | (v1 << 8) | (v2 << 4) | v3;
        // Note: This accepts only lowercase "%u". If you ever encounter "%U", change the check to:
        // if (s.charAt(i) == '%' && (s.charAt(i+1) == 'u' || s.charAt(i+1) == 'U')) {...}
    }

    private static int hexValOrMinus1(char c)
    {
        if (c >= '0' && c <= '9')
            return c - '0';
        if (c >= 'A' && c <= 'F')
            return 10 + (c - 'A');
        if (c >= 'a' && c <= 'f')
            return 10 + (c - 'a');
        return -1;
    }

    private static char toHexDigit(int nibble)
    {
        return (char) (nibble < 10 ? ('0' + nibble) : ('A' + (nibble - 10)));
    }
}
