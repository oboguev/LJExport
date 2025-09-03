package my.LJExport.runtime.lj;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
    /**
     * Extracts the "real" target from LiveJournal lj-toys embeds.
     * Works with:
     *  - Raw/encoded URLs (and HTML-escaped &amp;).
     *  - source+vid in normal query params.
     *  - Fallback: source/id mined from auth_token (e.g., ...&vk.com&165115840:HASH).
     *  - mode=lj-map with nested url= (returns sanitized nested URL).
     *
     * Returns a structured Target with:
     *   - source: youtube | vimeo | dailymotion | rutube | yandex | vk.com | map | unknown
     *   - id:     provider-specific identifier (if present)
     *   - url:    canonical public URL when we know a stable mapping (YouTube, Vimeo, etc.).
     *
     * Preferred input: the original encoded URL/string from HTML.
     */
    public static final class Target
    {
        public final String source; // e.g., "youtube", "vk.com", "yandex", "map", "unknown"
        public final String id; // provider-specific id (may be null)
        public final String url; // canonical external URL when buildable; else null
        public final Map<String, String> extras; // useful leftovers, e.g., raw nested map URL

        public Target(String source, String id, String url, Map<String, String> extras)
        {
            this.source = source;
            this.id = id;
            this.url = url;
            this.extras = extras == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
        }

        @Override
        public String toString()
        {
            return "Target{source=" + source + ", id=" + id + ", url=" + url + ", extras=" + extras + "}";
        }
    }

    /** Public entry: tries all strategies and returns Optional.empty() when nothing usable is present. */
    public static Optional<Target> extract(String ljToysUrl)
    {
        if (ljToysUrl == null || ljToysUrl.isEmpty())
            return Optional.empty();

        // 0) Unescape HTML '&amp;' but otherwise keep percent-encoding intact
        String unescaped = ljToysUrl.replace("&amp;", "&");

        URI uri = toSafeUri(unescaped).orElseGet(() -> toSafeUri("http://" + unescaped).orElse(null));
        if (uri == null)
            return Optional.empty();

        Map<String, String> q = parseQueryPreservingPlus(uri.getRawQuery());

        // 1) Special tool: map embeds like ?mode=lj-map&url=<encoded target>
        String mode = decodePreservePlus(q.get("mode"));
        if ("lj-map".equalsIgnoreCase(mode))
        {
            String nested = q.get("url"); // this is still percent-encoded (possibly multi-encoded)
            String nestedDecoded = deepDecodePercent(nested, 3); // be generous but bounded
            Optional<String> sanitized = sanitizeHttpUrl(nestedDecoded);
            if (sanitized.isPresent())
            {
                return Optional.of(new Target("map", null, sanitized.get(), Map.of("rawNested", nestedDecoded)));
            }
            else
            {
                // even if not a clean URL, return something informative
                return Optional.of(new Target("map", null, null, Map.of("rawNested", nestedDecoded)));
            }
        }

        // 2) Straight path: query params source + vid
        String sourceQ = decodePreservePlus(q.get("source"));
        String vidQ = decodePreservePlus(q.get("vid"));
        if (notBlank(sourceQ) && notBlank(vidQ))
        {
            String canonical = buildCanonicalUrl(sourceQ, vidQ);
            return Optional.of(new Target(sourceQ, vidQ, canonical, Map.of()));
        }

        // 3) Fallback: mine auth_token for &<source>&<id>[:signature]
        String authTokenRaw = q.get("auth_token");
        if (authTokenRaw != null)
        {
            String authDecoded = decodePreservePlus(authTokenRaw);
            // Decode %26 to '&' etc., then split on '&'
            List<String> parts = splitOnAmpersands(authDecoded);

            // Look for known source marker followed by an ID-ish token
            List<String> known = Arrays.asList("youtube", "vimeo", "dailymotion", "rutube", "yandex", "vk.com");
            for (int i = 0; i < parts.size(); i++)
            {
                String p = parts.get(i);
                String pl = p.toLowerCase(Locale.ROOT);
                if (known.contains(pl) && i + 1 < parts.size())
                {
                    String idMaybeWithHash = parts.get(i + 1);
                    String id = idMaybeWithHash.split(":", 2)[0]; // strip trailing ":signature"
                    String canonical = buildCanonicalUrl(pl, id);
                    return Optional.of(new Target(pl, id, canonical, Map.of("from", "auth_token")));
                }
            }

            // YouTube heuristic: if "youtube" appears anywhere, try to find a token that looks like a YouTube id
            if (parts.stream().anyMatch(s -> s.equalsIgnoreCase("youtube")))
            {
                for (String s : parts)
                {
                    String cand = s.split(":", 2)[0];
                    if (cand.matches("^[a-zA-Z0-9_-]{6,}$"))
                    {
                        String canonical = buildCanonicalUrl("youtube", cand);
                        return Optional.of(new Target("youtube", cand, canonical, Map.of("from", "auth_token-heuristic")));
                    }
                }
            }
        }

        // 4) Nothing recognized
        return Optional.empty();
    }

    // ---------------- helpers ----------------

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

    // Do not turn '+' into space; treat it literally (lj-tokens may contain '+')
    private static Map<String, String> parseQueryPreservingPlus(String rawQuery)
    {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty())
            return map;
        for (String p : rawQuery.split("&"))
        {
            if (p.isEmpty())
                continue;
            int i = p.indexOf('=');
            if (i < 0)
                map.put(p, "");
            else
                map.put(p.substring(0, i), p.substring(i + 1));
        }
        return map;
    }

    private static String decodePreservePlus(String s)
    {
        if (s == null)
            return null;
        String protectedPlus = s.replace("+", "%2B");
        return URLDecoder.decode(protectedPlus, StandardCharsets.UTF_8);
    }

    /** Try decoding several times (bounded) because nested encodings happen in the wild. */
    private static String deepDecodePercent(String s, int maxRounds)
    {
        if (s == null)
            return null;
        String prev = s, cur = s;
        for (int i = 0; i < maxRounds; i++)
        {
            cur = decodePreservePlus(prev);
            if (cur.equals(prev))
                break;
            prev = cur;
        }
        return cur;
    }

    private static boolean notBlank(String s)
    {
        return s != null && !s.isEmpty();
    }

    private static List<String> splitOnAmpersands(String authDecoded)
    {
        // First decode %xx, then split on '&'
        String fullyDecoded = decodePreservePlus(authDecoded);
        List<String> out = new ArrayList<>();
        for (String part : fullyDecoded.split("&"))
            if (!part.isEmpty())
                out.add(part);
        return out;
    }

    /** Build public URL when we know a stable mapping; else return null but keep source/id. */
    private static String buildCanonicalUrl(String source, String id)
    {
        if (source == null || id == null)
            return null;
        String s = source.toLowerCase(Locale.ROOT);
        switch (s)
        {
        case "youtube":
            return "https://www.youtube.com/watch?v=" + id;
        case "vimeo":
            return "https://vimeo.com/" + id;
        case "dailymotion":
            return "https://www.dailymotion.com/video/" + id;
        case "rutube":
            return "https://rutube.ru/video/" + id;

        // Yandex & VK: lj-toys uses internal identifiers; external canonical URL is not stable/obvious.
        // We still return source/id so the caller can decide how to handle.
        case "yandex":
        case "vk.com":
            return null;

        default:
            return null;
        }
    }

    /**
     * Very light sanitation: accept only http/https URIs, discard javascript/data/etc.
     * Optionally you can restrict host (e.g., to "www.openstreetmap.org").
     */
    private static Optional<String> sanitizeHttpUrl(String s)
    {
        if (s == null)
            return Optional.empty();
        try
        {
            URI u = new URI(s.trim());
            String scheme = u.getScheme();
            if (scheme == null)
                return Optional.empty();
            String low = scheme.toLowerCase(Locale.ROOT);
            if (!low.equals("http") && !low.equals("https"))
                return Optional.empty();

            // Example: if you want to allow only OpenStreetMap:
            // if (!"www.openstreetmap.org".equalsIgnoreCase(u.getHost())) return Optional.empty();

            return Optional.of(u.toString());
        }
        catch (URISyntaxException e)
        {
            return Optional.empty();
        }
    }

    // ------------------------- quick demo -------------------------

    public static void main(String[] args)
    {

        List<String> samples = List.of(
                                       // 1) YouTube with explicit source+vid
                                       "http://l.lj-toys.com/?auth_token=sessionless%3A1451815200%3Aembedcontent%3A68622%26504%261%26%26youtube%26HSXuNTwnxuI%3Aa137fa00cb29c81b7cf4980691d39340254117cc&source=youtube&vid=HSXuNTwnxuI&moduleid=504&preview=&journalid=68622&noads=1",

                                       // 2) VK.com with explicit source+vid
                                       "http://l.lj-toys.com/?auth_token=sessionless%3A1451808000%3Aembedcontent%3A68622%26456%261%26%26vk.com%26168410409%3A13bb74f34a53d0f83adb674df928c46779ba73b9&source=vk.com&vid=168410409&moduleid=456&preview=&journalid=68622&noads=1",

                                       // 3) Yandex with explicit source+vid
                                       "http://l.lj-toys.com/?auth_token=sessionless%3A1451818800%3Aembedcontent%3A68622%26116%261%26%26yandex%26lite%2Fx-garfield-x%2Fd29jy990gq.1003%2F%3Ada7c9da3daf3cdd57ee70abed4b012b548f15d93&source=yandex&vid=lite%2Fx-garfield-x%2Fd29jy990gq.1003%2F&moduleid=116&preview=&journalid=68622&noads=1",

                                       // 4) Internal LJ toys (no external source marker, should return empty)
                                       "http://l.lj-toys.com/?auth_token=sessionless%3A1451808000%3Aembedcontent%3A68622%262%261%26%3A4a0011cfad35470ec059ab2ac905afd3aebeec4c&moduleid=2&preview=&journalid=68622&noads=1",

                                       // 5) lj-map embed
                                       "http://l.lj-toys.com?mode=lj-map&url=http%3A%2F%2Fwww.openstreetmap.org%2F%3Flat%3D53.0%2C29.4%26lon%3D30.5%26zoom%3D10%26layers%3DM",

                                       // 6)
                                       "http://l.lj-toys.com/?auth_token=sessionless%3A1451815200%3Aembedcontent%3A68622%26504%261%26%26youtube%26HSXuNTwnxuI%3Aa137fa00cb29c81b7cf4980691d39340254117cc&source=youtube&vid=HSXuNTwnxuI&moduleid=504&preview=&journalid=68622&noads=1",
                                       "http://l.lj-toys.com/?auth_token=sessionless:1451815200:embedcontent:68622&504&1&&youtube&HSXuNTwnxuI:a137fa00cb29c81b7cf4980691d39340254117cc&source=youtube&vid=HSXuNTwnxuI&moduleid=504&preview=&journalid=68622&noads=1",
                                       "http://l.lj-toys.com/?auth_token=sessionless%3A1451815200%3Aembedcontent%3A68622%26504%261%26%26youtube%26HSXuNTwnxuI%3Aa137fa00cb29c81b7cf4980691d39340254117cc&amp;source=youtube&amp;vid=HSXuNTwnxuI&amp;moduleid=504&amp;preview=&amp;journalid=68622&amp;noads=1",
                                       "https://l.lj-toys.com/?auth_token=sessionless%3A1756094400%3Aembedcontent%3A248287%262832%26%26%26youtube%26ojeAiI1G1i0%3Aa21662dfc4d31ff372c40f443c0fd5dcaace61a4&amp;source=youtube&amp;vid=ojeAiI1G1i0&amp;moduleid=2832&amp;preview=&amp;journalid=248287&amp;noads="

        );
        for (String s : samples)
        {
            System.out.println(extract(s).orElse(new Target("unknown", null, null, Map.of())));
        }

    }
}
