package my.LJExport.runtime;

import java.util.UUID;

/**
 * Generates short, cryptographically-strong* random strings in the ranges {@code a-z} and {@code 0-9}.
 *
 * <p>
 * Each call consumes the full 128 bits of a freshly generated {@link java.util.UUID} and turns them into base-36 characters using
 * rejection sampling, so every bit of entropy is used and bias is avoided.
 * </p>
 *
 * <p>
 * (*Strictly speaking {@code UUID.randomUUID()} is backed by {@code SecureRandom} on modern JDKs, so its strength is suitable for
 * most non-cryptographic uses. If you need FIPS-level guarantees, inject your own {@code SecureRandom}.)
 * </p>
 */

/*
 * Why this design?
 * Deterministic API – Four concise static methods with self-explanatory names.
 * 
 * Uniform distribution – Every character is drawn with equal probability by using 6-bit slices 
 * and rejecting the out-of-range values (36-63).
 * 
 * Entropy efficiency – Up to 128 bits are consumed per call; if, exceptionally, more are required 
 * (after many rejections), a fresh UUID is fetched.
 * 
 * Thread-safe & allocation-free – No shared mutable state and only a single StringBuilder allocation per call.
 */
public final class RandomString
{

    /** The allowed character set: a–z then 0–9. */
    private static final char[] ALPHABET = initAlphabet();

    /** Prevent instantiation. */
    private RandomString()
    {
    }

    // ---------- public API ----------

    public static String rs3()
    {
        return generate(3);
    }

    public static String rs4()
    {
        return generate(4);
    }

    public static String rs5()
    {
        return generate(5);
    }

    public static String rs6()
    {
        return generate(6);
    }

    public static String rs(int n)
    {
        return generate(n);
    }

    // ---------- internal helpers ----------

    private static char[] initAlphabet()
    {
        char[] out = new char[36];

        for (int i = 0; i < 26; i++)
        {
            // a … z
            out[i] = (char) ('a' + i);
        }
        for (int i = 0; i < 10; i++)
        {
            // 0 … 9
            out[26 + i] = (char) ('0' + i);
        }

        return out;
    }

    /**
     * Generates a string of {@code len} characters. Uses 6-bit chunks from a UUID, discarding values ≥ 36 to keep the distribution
     * uniform.
     */
    private static String generate(final int len)
    {
        if (len < 1 || len > 6)
            throw new IllegalArgumentException("Length must be 1–6");

        UUID uuid = UUID.randomUUID();
        long[] pool = { uuid.getMostSignificantBits(), uuid.getLeastSignificantBits() };
        int poolIdx = 0;
        long bitBuffer = pool[poolIdx];
        int bitsLeft = 64;

        StringBuilder sb = new StringBuilder(len);

        while (sb.length() < len)
        {

            // Refill the buffer if necessary
            if (bitsLeft < 6)
            {
                if (++poolIdx == pool.length)
                { // rare: exhausted 128 bits
                    uuid = UUID.randomUUID(); // start fresh
                    pool = new long[] {
                            uuid.getMostSignificantBits(),
                            uuid.getLeastSignificantBits()
                    };
                    poolIdx = 0;
                }
                bitBuffer = pool[poolIdx];
                bitsLeft = 64;
            }

            final int index = (int) (bitBuffer & 0x3F); // take next 6 bits
            bitBuffer >>>= 6;
            bitsLeft -= 6;

            if (index < ALPHABET.length)
            { // reject 36–63
                sb.append(ALPHABET[index]);
            }
        }

        return sb.toString();
    }
}