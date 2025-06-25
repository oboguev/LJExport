package my.LJExport.runtime;

public class PasswordScrambler
{
    // should be constant across scramble/unscramble
    private static final String SECRET_KEY = "e4b337d0-ec60-4f2d-92b2-f5dc8a3eb620";

    public static String scramble(String input)
    {
        byte[] keyBytes = SECRET_KEY.getBytes();
        byte[] inputBytes = input.getBytes();
        byte[] outputBytes = new byte[inputBytes.length];

        for (int i = 0; i < inputBytes.length; i++)
        {
            outputBytes[i] = (byte) (inputBytes[i] ^ keyBytes[i % keyBytes.length]);
        }

        return bytesToHex(outputBytes);
    }

    public static String unscramble(String scrambled)
    {
        byte[] keyBytes = SECRET_KEY.getBytes();
        byte[] inputBytes = hexToBytes(scrambled);
        byte[] outputBytes = new byte[inputBytes.length];

        for (int i = 0; i < inputBytes.length; i++)
        {
            outputBytes[i] = (byte) (inputBytes[i] ^ keyBytes[i % keyBytes.length]);
        }

        return new String(outputBytes);
    }

    // Helper to convert byte[] to hex string
    private static String bytesToHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
        {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // Helper to convert hex string to byte[]
    private static byte[] hexToBytes(String hex)
    {
        int len = hex.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2)
        {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
