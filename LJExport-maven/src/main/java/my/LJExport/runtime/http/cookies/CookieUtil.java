package my.LJExport.runtime.http.cookies;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;

public class CookieUtil
{
    /**
     * Copy cookies from 'from' store to 'to' store,
     * if their domain matches any of the provided domain suffixes.
     *
     * @param from Source CookieStore
     * @param to Target CookieStore
     * @param domains Domain suffixes to match (e.g. "a.com", "example.org")
     */
    public static void copySelectCookies(CookieStore from, CookieStore to, String... domains)
    {
        if (from == null || to == null || domains == null || domains.length == 0)
            return;

        // Normalize domains to lowercase
        Set<String> domainSuffixes = new HashSet<>();
        for (String d : domains)
        {
            if (d != null && !d.isEmpty())
                domainSuffixes.add(d.toLowerCase(Locale.ROOT));
        }

        for (Cookie cookie : from.getCookies())
        {
            String domain = cookie.getDomain();
            if (domain == null)
                continue;

            String domainNorm = domain.startsWith(".") ? domain.substring(1) : domain;
            domainNorm = domainNorm.toLowerCase(Locale.ROOT);

            if (matchesDomainSuffix(domainNorm, domainSuffixes))
            {
                to.addCookie(cookie);
            }
        }
    }

    public static void copyFacebookCookies(CookieStore from, CookieStore to)
    {
        copySelectCookies(from, to, "facebook.com", "fbcdn.net", "messenger.com", "facebook.net");
    }

    /**
     * Check whether cookie domain matches any of the domain suffixes.
     * "xxx.a.com" matches "a.com"
     * ".xxx.a.com" also matches "a.com"
     */
    private static boolean matchesDomainSuffix(String cookieDomain, Set<String> suffixes)
    {
        for (String suffix : suffixes)
        {
            if (cookieDomain.equals(suffix))
                return true;
            if (cookieDomain.endsWith("." + suffix))
                return true;
        }
        return false;
    }
}
