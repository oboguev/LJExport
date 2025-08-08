package my.LJExport.runtime.url;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.lj.LJUtil;

/*
 * Unwrap redirection links
 */
public class AwayLink
{
    /*
     * Unwrap the link taken from JSOUP.getAttribute and then decoded.
     * Result needs to be re-encoded before inserting into JSOUP.setAttribute  
     */
    public static String unwrapDecoded(String decoded_href) throws Exception
    {
        if (decoded_href == null)
            return null;

        for (String prev = decoded_href;;)
        {
            String current = unwrapOneLevel(prev);
            if (current.equals(prev))
                return prev;
            prev = current;
        }
    }

    public static List<String> unwrapDecodedToList(String decoded_href, boolean innerFirst) throws Exception
    {
        if (decoded_href == null)
            return null;

        List<String> list = new ArrayList<>();
        list.add(decoded_href);

        for (String prev = decoded_href;;)
        {
            String current = unwrapOneLevel(prev);
            if (current.equals(prev))
                break;
            list.add(current);
            prev = current;
        }

        if (innerFirst)
            Collections.reverse(list);

        return list;
    }

    /*
     * Unwrap the link taken from JSOUP.getAttribute raw as-is i.e. encoded.
     * Result needs to be reinserted into JSOUP.setAttribute as-is since it is encoded.  
     */
    public static String unwrapEncoded(String encoded_href) throws Exception
    {
        String initial_encoded_href = encoded_href;

        if (encoded_href == null)
            return null;
        
        encoded_href = encoded_href.trim();
        
        if (!Util.startsWithIgnoreCase(encoded_href, null, "http://", "https://", "http3a//", "https3a//"))
            return encoded_href;

        String decoded_href;
        try
        {
            decoded_href = UrlUtil.decodeHtmlAttrLink(encoded_href);
        }
        catch (Exception ex)
        {
            // typically means incorrect undecodable %XX sequence in encoded_href,
            // for example %D1%D at the end of a truncated URL
            return initial_encoded_href;
        }
        
        String unwrapped_decoded_href = unwrapDecoded(decoded_href);
        if (unwrapped_decoded_href.equals(decoded_href))
            return initial_encoded_href;

        String unwrapped_encoded_href = UrlUtil.encodeUrlForHtmlAttr(unwrapped_decoded_href, true);
        return unwrapped_encoded_href;
    }

    public static boolean unwrap(Node n, String attr) throws Exception
    {
        if (!(n instanceof Element))
            return false;

        String href = JSOUP.getAttribute(n, attr);
        if (href == null)
            return false;
        
        href = href.trim();

        String original_href = href;
        boolean updated = false;

        String newref = unwrapEncoded(href);
        if (!newref.equals(href))
        {
            JSOUP.updateAttribute(n, attr, newref);

            if (JSOUP.getAttribute(n, "original-" + attr) == null)
                JSOUP.setAttribute(n, "original-" + attr, original_href);

            updated = true;
        }

        return updated;
    }

    private static final String[] wrap_prefixes_1 = {
            "https://www.livejournal.com/away?to=",
            "https://www.livejournal.com/away/?to=",
            "https://vk.com/away.php?to=",
            "https://vk.com/away.php/?to=",
            // ..............................
            "http://www.livejournal.com/away?to=",
            "http://www.livejournal.com/away/?to=",
            "http://vk.com/away.php?to=",
            "http://vk.com/away.php/?to="
    };

    private static final String[] wrap_prefixes_fb = {
            "https://www.facebook.com/l.php?",
            "http://www.facebook.com/l.php?",
            "https://l.facebook.com/l.php?",
            "http://l.facebook.com/l.php?",
            "https://l.instagram.com/?",
            "http://l.instagram.com/?",
            "https://l.instagram.com?",
            "http://l.instagram.com?"
    };

    private static String unwrapOneLevel(String decoded_href) throws Exception
    {
        if (decoded_href == null)
            return null;

        decoded_href = decoded_href.trim();
        
        String anchor = Util.getAnchor(decoded_href);
        decoded_href = Util.stripAnchor(decoded_href);

        MutableObject<String> prefix = new MutableObject<>();
        String xurl;

        if (Util.startsWith(decoded_href, prefix, wrap_prefixes_1))
        {
            decoded_href = decoded_href.substring(prefix.get().length());
            return post(decoded_href, anchor);
        }

        if (Util.startsWith(decoded_href, prefix, wrap_prefixes_fb))
        {
            String u = UrlUtil.extractQueryParameter(decoded_href, "u");
            if (u == null || !Util.startsWithIgnoreCase(u, null, "http://", "https://", "http%3a//", "https%3a//"))
                throw new Exception("Malstructured wrap link");
            return post(u, anchor);
        }

        xurl = unwrapImagesGoogleCom(decoded_href);
        if (!xurl.equals(decoded_href))
            return post(xurl, anchor);

        xurl = unwrapLivejournalImgPrxSt(decoded_href);
        if (!xurl.equals(decoded_href))
            return post(xurl, anchor);

        xurl = unwrapInfonarodRuAaway(decoded_href);
        if (!xurl.equals(decoded_href))
            return post(xurl, anchor);

        xurl = urnwrapFbcdnExternalSafeImage(decoded_href);
        if (!xurl.equals(decoded_href))
            return post(xurl, anchor);

        xurl = urnwrapGoogleDocsViewer(decoded_href);
        if (!xurl.equals(decoded_href))
            return post(xurl, anchor);

        xurl = urnwrapLiveComImageproxy(decoded_href);
        if (!xurl.equals(decoded_href))
            return post(xurl, anchor);

        xurl = unwrapGoogleUrl(decoded_href);
        if (!xurl.equals(decoded_href))
            return post(xurl, anchor);

        xurl = unwrapDreamwidthProxy(decoded_href);
        if (!xurl.equals(decoded_href))
            return post(xurl, anchor);

        return Util.withAnchor(decoded_href, anchor);
    }

    private static String post(String url, String anchor)
    {
        url = fixOverencoding(url);
        url = Util.withAnchor(url, anchor);
        url = UrlFixCP1251.fixUrlCp1251Sequences(url);
        return url;
    }

    private static String fixOverencoding(String href)
    {
        if (Util.startsWith(href.toLowerCase(), null, "https%3a", "http%3a"))
            return UrlUtil.decodeUrl(href);
        else
            return href;
    }

    /* =================================================================== */

    public static String unwrapImagesGoogleCom(String url)
    {
        if (!lc_contains_all(url, "images.google.com", "/imgres?"))
            return url;

        try
        {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();

            if (host == null || path == null)
                return url;

            // Normalize host
            String lowerHost = host.toLowerCase();
            if (!(lowerHost.equals("images.google.com") || lowerHost.endsWith(".images.google.com")))
                return url;

            if (!path.equals("/imgres"))
                return url;

            String query = uri.getRawQuery(); // use rawQuery to preserve % encoding
            if (query == null)
                return url;

            // Find imgurl parameter
            Pattern p = Pattern.compile("(^|&)" + "imgurl=([^&]+)");
            Matcher m = p.matcher(query);
            if (m.find())
            {
                String encodedImgUrl = m.group(2);
                return UrlUtil.decodeUrl(encodedImgUrl);
            }

            return url;
        }
        catch (Exception e)
        {
            return url;
        }
    }

    public static boolean isWrapedImagesGoogleCom(String url)
    {
        String result = unwrapImagesGoogleCom(url);
        return !result.equals(url);
    }

    /* =================================================================== */

    // https://www.google.com/url?q=https://pbs.twimg.com/media/CuUjbVqUsAATLSw.jpg&sa=U&ved=0ahUKEwiKh52Chpb9AhXTi1wKHS28DycQ5hMIBQ&usg=AOvVaw2BbqSzqbnWqhrdUA4x2xQA

    public static String unwrapGoogleUrl(String url)
    {
        if (!lc_contains_all(url, "google", "/url?"))
            return url;

        try
        {
            URL xurl = new URL(url);
            String host = xurl.getHost();
            String path = xurl.getPath();
            if (host == null || path == null)
                return url;
            host = host.trim().toLowerCase();
            if (isGoogleHost(host) && path.equals("/url"))
                return unwrapQueryParameterRedirect(url, "q");
        }
        catch (Exception ex)
        {
            return url;
        }

        return url;
    }

    private static final Pattern GOOGLE_HOST_PATTERN = Pattern.compile("^(www\\.)?google\\.[a-z]{2,3}(\\.[a-z]{2})?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Returns true if the host is a known Google domain like google.com, www.google.ru, google.co.uk, etc.
     */
    public static boolean isGoogleHost(String host)
    {
        if (host == null)
            return false;

        return GOOGLE_HOST_PATTERN.matcher(host).matches();
    }

    /* =================================================================== */

    public static boolean isWrapedLivejournalImgPrxSt(String url)
    {
        try
        {
            String xurl = LJUtil.decodeImgPrxStLink(url);
            return !xurl.equals(url);
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    /*
     * Decodes https://imgprx.livejournal.net/st/a5qxcIzYm6irxnS1DFddEl8-2t1CYWAJYj-i7bfFaKo/img1.labirint.ru/books/168777/big.jpg
     * into    https://img1.labirint.ru/books/168777/big.jpg
     */
    public static String unwrapLivejournalImgPrxSt(String url)
    {
        try
        {
            return LJUtil.decodeImgPrxStLink(url);
        }
        catch (Exception ex)
        {
            return url;
        }
    }

    /* =================================================================== */

    public static String unwrapInfonarodRuAaway(String url)
    {
        String prefix = "http://infonarod.ru/away.php?";

        if (!Util.startsWithIgnoreCase(url, null, prefix))
            return url;

        return unwrapQueryParameterRedirect(url, "url");
    }

    private static String unwrapQueryParameterRedirect(String url, String qpname)
    {
        String redir;
        try
        {
            redir = UrlUtil.extractQueryParameter(url, qpname);
            if (redir != null)
            {
                redir = UrlUtil.decodeUrl(redir);
                if (Util.startsWithIgnoreCase(redir, null, "https://", "http://"))
                    return UrlUtil.encodeMinimal(redir);
            }
        }
        catch (Exception ex)
        {
            Util.noop();
        }

        return url;
    }

    private static boolean lc_contains_all(String url, String... matches)
    {
        String lc = url.toLowerCase();
        for (String s : matches)
        {
            if (!lc.contains(s))
                return false;
        }

        return true;
    }

    /* =================================================================== */

    public static String urnwrapFbcdnExternalSafeImage(String url) throws Exception
    {
        if (!lc_contains_all(url, ".xx.fbcdn.net", "/safe_image.php?"))
            return url;

        String prefix = "https://external.xx.fbcdn.net/safe_image.php?";
        boolean match = false;

        if (Util.startsWith(url, null, prefix))
            match = true;

        if (!match)
        {
            URL xurl = new URL(url);
            String host = xurl.getHost();
            String path = xurl.getPath();
            if (host == null || path == null)
                return url;
            host = host.toLowerCase();

            if (host.startsWith("external") && host.endsWith(".xx.fbcdn.net") && path.equals("/safe_image.php"))
                match = true;
        }

        if (match)
            return unwrapQueryParameterRedirect(url, "url");
        else
            return url;
    }

    /* =================================================================== */

    public static String urnwrapGoogleDocsViewer(String url) throws Exception
    {
        if (!lc_contains_all(url, "docs.google.com/viewer"))
            return url;

        URL xurl = new URL(url);
        String host = xurl.getHost();
        String path = xurl.getPath();
        if (host == null || path == null)
            return url;
        host = host.toLowerCase();

        if (host.equals("docs.google.com") && path.equals("/viewer"))
            return unwrapQueryParameterRedirect(url, "url");
        else
            return url;
    }

    /* =================================================================== */

    public static String urnwrapLiveComImageproxy(String url) throws Exception
    {
        if (!lc_contains_all(url, ".live.com/handlers/imageproxy.mvc?"))
            return url;

        URL xurl = new URL(url);
        String host = xurl.getHost();
        String path = xurl.getPath();
        if (host == null || path == null)
            return url;
        host = host.toLowerCase();

        if (host.endsWith(".live.com") && path.equals("/handlers/imageproxy.mvc"))
            return unwrapQueryParameterRedirect(url, "url");
        else
            return url;
    }

    /* =================================================================== */

    /**
     * Converts a proxied Dreamwidth image URL to its original form. https://p.dreamwidth.org/AAA/BBB/REMAINDER -> https://REMAINDER
     * 
     * https://p.dreamwidth.org/31fb6eddb6f7/3144591-346937/i1133.photobucket.com/albums/m588/technolirik/Sommer%202015/IMG_7236_zpscimjc0yw.jpg~original
     * https://p.dreamwidth.org/bd3c8005671e/3144591-636208/dlib.rsl.ru/viewer/pdf?docId=01004913781&page=147&rotate=0&negative=0
     * https://p.dreamwidth.org/325de0142beb/3144591-344516/l-files.livejournal.net/userhead/1512?v=1416213861
     * https://p.dreamwidth.org/85b2c158373e/3144591-399915/l-stat.livejournal.net/img/community.gif?v=556?v=137.1
     */
    public static String unwrapDreamwidthProxy(String url)
    {
        MutableObject<String> prefix = new MutableObject<String>();

        if (!Util.startsWithIgnoreCase(url, prefix, "https://p.dreamwidth.org/", "http://p.dreamwidth.org/"))
            return url;

        // Trim the prefix
        String remainder = url.substring(prefix.get().length());

        // Skip first segment (AAA)
        int firstSlash = remainder.indexOf('/');
        if (firstSlash < 0)
            throw new IllegalArgumentException("Malformed proxied URL (no AAA): " + url);

        // Skip second segment (BBB)
        int secondSlash = remainder.indexOf('/', firstSlash + 1);
        if (secondSlash < 0)
            throw new IllegalArgumentException("Malformed proxied URL (no BBB): " + url);

        // What remains is the original URL path
        String original = remainder.substring(secondSlash + 1);

        return "https://" + original;
    }
}
