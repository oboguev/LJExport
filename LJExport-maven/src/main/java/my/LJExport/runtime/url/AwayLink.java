package my.LJExport.runtime.url;

import java.net.URI;
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
    public static String unwrapAwayLinkDecoded(String decoded_href) throws Exception
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

    /*
     * Unwrap the link taken from JSOUP.getAttribute raw as-is i.e. encoded.
     * Result nees to be reinserted into JSOUP.setAttribute as-is since it is encoded.  
     */
    public static String unwrapAwayLinkEncoded(String encoded_href) throws Exception
    {
        if (encoded_href == null)
            return null;

        String initial_encoded_href = encoded_href;

        String decoded_href = UrlUtil.decodeHtmlAttrLink(encoded_href);
        String unwrapped_decoded_href = unwrapAwayLinkDecoded(decoded_href);
        if (unwrapped_decoded_href.equals(decoded_href))
            return initial_encoded_href;

        String unwrapped_encoded_href = UrlUtil.encodeUrlForHtmlAttr(unwrapped_decoded_href);
        return unwrapped_encoded_href;
    }

    public static boolean unwrapAwayLink(Node n, String attr) throws Exception
    {
        if (!(n instanceof Element))
            return false;

        String href = JSOUP.getAttribute(n, attr);
        if (href == null)
            return false;

        String original_href = href;
        boolean updated = false;

        String newref = AwayLink.unwrapAwayLinkEncoded(href);
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
            "https://vk.com/away.php/?to="
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
        
        MutableObject<String> prefix = new MutableObject<>();
        String xurl;

        if (Util.startsWith(decoded_href, prefix, wrap_prefixes_1))
        {
            decoded_href = decoded_href.substring(prefix.getValue().length());
            decoded_href = fixOverencoding(decoded_href);
            decoded_href = UrlFixCP1251.fixUrlCp1251Sequences(decoded_href);
            return decoded_href;
        }

        if (Util.startsWith(decoded_href, prefix, wrap_prefixes_fb))
        {
            String u = UrlUtil.extractQueryParameter(decoded_href, "u");
            if (u == null || !Util.startsWith(u, null, "http://", "https://"))
                throw new Exception("Malstructured wrap link");
            u = UrlFixCP1251.fixUrlCp1251Sequences(u);
            return u;
        }

        xurl = unwrapImagesGoogleCom(decoded_href);
        if (!xurl.equals(decoded_href))
        {
            xurl = UrlFixCP1251.fixUrlCp1251Sequences(xurl);
            return xurl;
        }

        xurl = unwrapLivejournalImgPrxSt(decoded_href);
        if (!xurl.equals(decoded_href))
        {
            xurl = UrlFixCP1251.fixUrlCp1251Sequences(xurl);
            return xurl;
        }

        return decoded_href;
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
}
