package my.LJExport.runtime.url;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;

/*
 * Unwrap redirection links
 */
public class AwayLinks
{
    /*
     * Unwrap the link taken from JSOUP.getAttribute and then decoded.
     * Result needs to be re-encoded before inserting into JSOUP.setAttribute  
     */
    public static String unwrapAwayLinkDecoded(String decoded_href) throws Exception
    {
        while (isWrapped(decoded_href))
            decoded_href = unwrapOneLevel(decoded_href);
        return decoded_href;
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

        String newref = AwayLinks.unwrapAwayLinkEncoded(href);
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

    private static boolean isWrapped(String decoded_href)
    {
        if (decoded_href == null)
            return false;

        return Util.startsWith(decoded_href, null, wrap_prefixes_1) || Util.startsWith(decoded_href, null, wrap_prefixes_fb);
    }

    private static String unwrapOneLevel(String decoded_href) throws Exception
    {
        MutableObject<String> prefix = new MutableObject<>();

        if (Util.startsWith(decoded_href, prefix, wrap_prefixes_1))
        {
            decoded_href = decoded_href.substring(prefix.getValue().length());
            decoded_href = fixOverencoding(decoded_href);
            decoded_href = UrlFixCP1251.fixUrlCp1251Sequences(decoded_href);
            return decoded_href;
        }
        else if (Util.startsWith(decoded_href, prefix, wrap_prefixes_fb))
        {
            String u = UrlUtil.extractQueryParameter(decoded_href, "u");
            if (u == null || !Util.startsWith(u, null, "http://", "https://"))
                throw new Exception("Malstructured wrap link");
            u = UrlFixCP1251.fixUrlCp1251Sequences(u);
            return u;
        }
        else
        {
            return decoded_href;
        }
    }

    private static String fixOverencoding(String href)
    {
        if (Util.startsWith(href.toLowerCase(), null, "https%3a", "http%3a"))
            return URLDecoder.decode(href.replace("+", "%2B"), StandardCharsets.UTF_8);
        else
            return href;
    }

}
