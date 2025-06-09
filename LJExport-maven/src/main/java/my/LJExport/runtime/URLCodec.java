package my.LJExport.runtime;

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

    // RFC 3986 "unreserved" characters
    private static boolean isUnreserved(char c)
    {
        return (c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~';
    }
}
