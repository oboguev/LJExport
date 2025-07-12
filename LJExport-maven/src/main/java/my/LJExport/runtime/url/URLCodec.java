package my.LJExport.runtime.url;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class URLCodec
{
    // Encodes input per RFC 3986
    public static String encode(String input)
    {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray())
        {
            if (isUnreserved(c))
            {
                result.append(c);
            }
            else
            {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes)
                {
                    result.append('%');
                    result.append(String.format("%02X", b));
                }
            }
        }

        return result.toString();
    }

    // RFC 3986 "unreserved" characters
    private static boolean isUnreserved(char c)
    {
        return (c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~';
    }

    /* ================================================================================= */

    // Decodes percent-encoded input (assumes UTF-8)
    public static String decode(String input)
    {
        byte[] buffer = new byte[input.length()];
        int pos = 0;

        for (int i = 0; i < input.length();)
        {
            char c = input.charAt(i);
            if (c == '%')
            {
                if (i + 2 >= input.length())
                    throw new IllegalArgumentException("Incomplete percent-encoding at: " + i);

                int hex = Integer.parseInt(input.substring(i + 1, i + 3), 16);
                buffer[pos++] = (byte) hex;
                i += 3;
            }
            else
            {
                buffer[pos++] = (byte) c;
                i++;
            }
        }

        return new String(buffer, 0, pos, StandardCharsets.UTF_8);
    }

    /* ================================================================================= */

    public static String decodeMixed(String input)
    {
        if (!input.contains("%"))
            return input;
        
        StringBuilder result = new StringBuilder();
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        int length = input.length();
        int i = 0;

        while (i < length)
        {
            char ch = input.charAt(i);

            if (ch == '%' && i + 2 < length && isHexDigit(input.charAt(i + 1)) && isHexDigit(input.charAt(i + 2)))
            {
                byteBuffer.reset();

                // Collect valid %XX sequences
                while (i + 2 < length && input.charAt(i) == '%' &&
                        isHexDigit(input.charAt(i + 1)) && isHexDigit(input.charAt(i + 2)))
                {

                    int byteVal = hexToByte(input.charAt(i + 1), input.charAt(i + 2));
                    byteBuffer.write(byteVal);
                    i += 3;
                }

                // Decode collected bytes as UTF-8
                try
                {
                    result.append(byteBuffer.toString("UTF-8"));
                }
                catch (UnsupportedEncodingException ex)
                {
                    throw new RuntimeException(ex.getLocalizedMessage(), ex);
                }
            }
            else
            {
                result.append(ch);
                i++;
            }
        }

        return result.toString();
    }

    // Checks if a character is a valid hexadecimal digit
    private static boolean isHexDigit(char c)
    {
        return ('0' <= c && c <= '9') ||
                ('A' <= c && c <= 'F') ||
                ('a' <= c && c <= 'f');
    }

    // Converts two hex digits to a byte
    private static int hexToByte(char high, char low)
    {
        return (Character.digit(high, 16) << 4) | Character.digit(low, 16);
    }

    /* ================================================================================= */

    public static String encodeReserved(String s, String reserved)
    {
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < s.length(); i++)
        {
            char ch = s.charAt(i);
            if (reserved.indexOf(ch) >= 0 || ch < 32 || ch == 255 || ch == 65533)
            {
                // Encode character as UTF-8 bytes and then %XX encode each byte
                byte[] bytes = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes)
                    result.append(String.format("%%%02X", b & 0xFF));
            }
            else
            {
                result.append(ch);
            }
        }
        
        return result.toString();
    }
    
    public static String encodeFilename(String s)
    {
        return encodeReserved(s, FILESYS_RESERVED_CHARS);
    }

    private static final String FILESYS_RESERVED_CHARS = "|\\/:*?\"<>";
}
