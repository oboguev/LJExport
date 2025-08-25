package my.LJExport.runtime.lj;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/*
 * Extract URL from LJ toys links
 */
public class LjToys
{
    private LjToys()
    {
    }

    /**
     * Extracts the target media URL from an lj-toys embed URL (or returns empty if not recognized).
     *
     * @param ljToysUrl the iframe src as a String. Prefer passing the original ENCODED value.
     *                  Both "http" and "https" are accepted. HTML &amp; entities are unescaped.
     *                  
     * @return Optional target URL (e.g., https://www.youtube.com/watch?v=HSXuNTwnxuI)
     */
    public static Optional<String> extractTargetUrl(String ljToysUrl)
    {
        if (ljToysUrl == null || ljToysUrl.isEmpty())
            return Optional.empty();

        // 1) Unescape common HTML entity for ampersand. (Avoid external deps.)
        String unescaped = ljToysUrl.replace("&amp;", "&");

        // 2) Build a URI safely. If it lacks a scheme, try to add http:// as a last resort.
        URI uri = toSafeUri(unescaped).orElseGet(() -> toSafeUri("http://" + unescaped).orElse(null));
        if (uri == null)
            return Optional.empty();

        // 3) Parse query map using the "raw" query (so we control decoding)
        Map<String, String> q = parseQueryPreservingPlus(uri.getRawQuery());

        // 4) First, try the straightforward way: source + vid
        String source = decodePercent(q.get("source"));
        String vid = decodePercent(q.get("vid"));

        Optional<String> direct = buildFromSourceAndVid(source, vid);
        if (direct.isPresent())
            return direct;

        // 5) Fallback: try to mine auth_token for "youtube" (or other) markers.
        String authTokenRaw = q.get("auth_token"); // still percent-encoded
        if (authTokenRaw != null)
        {
            String authDecoded = decodePercentPreservePlus(authTokenRaw);

            // The token often encodes '&' as %26; after decoding, we can split.
            // Example snippet inside token: "...&youtube&HSXuNTwnxuI:hash..."
            List<String> parts = splitAuthToken(authDecoded);

            // Try to detect source + id inside token
            Optional<String> fromToken = buildFromAuthToken(parts);
            if (fromToken.isPresent())
                return fromToken;
        }

        return Optional.empty();
    }

    // ------------------------- helpers -------------------------

    private static Optional<URI> toSafeUri(String s)
    {
        try
        {
            return Optional.of(new URI(s));
        }
        catch (URISyntaxException e)
        {
            return Optional.empty();
        }
    }

    /**
     * Parse query without treating '+' as space (browsers differ; lj-toys tokens may contain '+').
     * We split on '&', then split each pair on the first '='.
     * Values remain percent-encoded; caller decides how/what to decode.
     */
    private static Map<String, String> parseQueryPreservingPlus(String rawQuery)
    {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty())
            return map;

        String[] pairs = rawQuery.split("&");
        for (String p : pairs)
        {
            int eq = p.indexOf('=');
            if (eq < 0)
            {
                // key with no value
                if (!p.isEmpty())
                    map.put(p, "");
            }
            else
            {
                String k = p.substring(0, eq);
                String v = p.substring(eq + 1);
                map.put(k, v);
            }
        }
        return map;
    }

    /**
     * Percent-decode without converting '+' to space.
     * URLDecoder always treats '+' as space, so we first protect literal '+'.
     */
    private static String decodePercentPreservePlus(String s)
    {
        if (s == null)
            return null;

        // Protect literal '+'
        String protectedPlus = s.replace("+", "%2B");
        return URLDecoder.decode(protectedPlus, StandardCharsets.UTF_8);
    }

    /**
     * Normal percent-decoding (OK when value is known not to contain literal '+').
     */
    private static String decodePercent(String s)
    {
        if (s == null)
            return null;
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static Optional<String> buildFromSourceAndVid(String source, String vid)
    {
        if (source == null || source.isEmpty() || vid == null || vid.isEmpty())
            return Optional.empty();

        switch (source.toLowerCase(Locale.ROOT))
        {
        case "youtube":
            return Optional.of("https://www.youtube.com/watch?v=" + vid);
            
        case "vimeo":
            return Optional.of("https://vimeo.com/" + vid);
            
        case "dailymotion":
            // Dailymotion IDs typically map into /video/<id>
            return Optional.of("https://www.dailymotion.com/video/" + vid);
            
        case "rutube":
            return Optional.of("https://rutube.ru/video/" + vid);
            
        default:
            return Optional.empty();
        }
    }

    private static List<String> splitAuthToken(String authDecoded)
    {
        // After decode, it may contain embedded '&' segments.
        // Split on '&', drop empties.
        List<String> parts = new ArrayList<>();
        for (String s : authDecoded.split("&"))
        {
            if (!s.isEmpty())
                parts.add(s);
        }
        return parts;
    }

    private static Optional<String> buildFromAuthToken(List<String> parts)
    {
        if (parts.isEmpty())
            return Optional.empty();

        // We look for a known source keyword, then take the next token as the id (possibly "id:hash").
        Set<String> known = new HashSet<>(Arrays.asList("youtube", "vimeo", "dailymotion", "rutube"));
        for (int i = 0; i < parts.size(); i++)
        {
            String p = parts.get(i).toLowerCase(Locale.ROOT);
            if (known.contains(p) && i + 1 < parts.size())
            {
                String idMaybeWithHash = parts.get(i + 1);
                String id = idMaybeWithHash.split(":", 2)[0]; // strip trailing :signature if present
                return buildFromSourceAndVid(p, id);
            }
        }

        // As an extra lenient fallback: scan any token that looks like a YouTube id if "youtube" appears anywhere.
        boolean mentionsYouTube = parts.stream().anyMatch(s -> s.equalsIgnoreCase("youtube"));
        if (mentionsYouTube)
        {
            Pattern ytId = Pattern.compile("^[a-zA-Z0-9_-]{6,}$");
            for (String s : parts)
            {
                String candidate = s.split(":", 2)[0];
                if (ytId.matcher(candidate).matches())
                {
                    return Optional.of("https://www.youtube.com/watch?v=" + candidate);
                }
            }
        }

        return Optional.empty();
    }

    // ------------------------- quick demo -------------------------

    public static void main(String[] args)
    {
        String encoded = "http://l.lj-toys.com/?auth_token=sessionless%3A1451815200%3Aembedcontent%3A68622%26504%261%26%26youtube%26HSXuNTwnxuI%3Aa137fa00cb29c81b7cf4980691d39340254117cc&source=youtube&vid=HSXuNTwnxuI&moduleid=504&preview=&journalid=68622&noads=1";
        String htmlEscaped = encoded.replace("&", "&amp;");

        System.out.println(extractTargetUrl(encoded).orElse("<none>"));
        System.out.println(extractTargetUrl(htmlEscaped).orElse("<none>"));

        String decodedBad = "http://l.lj-toys.com/?auth_token=sessionless:1451815200:embedcontent:68622&504&1&&youtube&HSXuNTwnxuI:a137fa00cb29c81b7cf4980691d39340254117cc&source=youtube&vid=HSXuNTwnxuI&moduleid=504&preview=&journalid=68622&noads=1";
        System.out.println(extractTargetUrl(decodedBad).orElse("<none>"));
        
        String encoded2 = "http://l.lj-toys.com/?auth_token=sessionless%3A1451815200%3Aembedcontent%3A68622%26504%261%26%26youtube%26HSXuNTwnxuI%3Aa137fa00cb29c81b7cf4980691d39340254117cc&amp;source=youtube&amp;vid=HSXuNTwnxuI&amp;moduleid=504&amp;preview=&amp;journalid=68622&amp;noads=1";
        System.out.println(extractTargetUrl(encoded2).orElse("<none>"));
    }
}
