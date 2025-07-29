package my.LJExport.runtime.http.cookies;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;

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

    /**
     * Deletes all cookies from the given CookieStore whose domain matches any of the specified domains,
     * including subdomains.
     *
     * @param store   The CookieStore from which to delete cookies.
     * @param domains List of domain suffixes (e.g. "aaa.com") whose cookies should be removed.
     */

    /**
     * Deletes cookies from the Apache CookieStore whose domain matches any of the specified domains,
     * including subdomains.
     *
     * @param store   the Apache HttpClient CookieStore
     * @param domains one or more domain suffixes (e.g. "aaa.com") to match and remove cookies for
     */
    public static void deleteSelectCookies(CookieStore store, String... domains)
    {
        if (store == null || domains == null || domains.length == 0)
        {
            return;
        }

        Set<String> targetDomains = new HashSet<>();
        for (String d : domains)
        {
            if (d != null)
            {
                targetDomains.add(d.toLowerCase(Locale.ROOT).replaceFirst("^\\.+", ""));
            }
        }

        // Collect cookies to delete
        List<Cookie> toRemove = new ArrayList<>();
        for (Cookie cookie : store.getCookies())
        {
            String cookieDomain = cookie.getDomain();
            if (cookieDomain == null)
                continue;

            String normalized = cookieDomain.toLowerCase(Locale.ROOT).replaceFirst("^\\.+", "");
            for (String domain : targetDomains)
            {
                if (normalized.equals(domain) || normalized.endsWith("." + domain))
                {
                    toRemove.add(cookie);
                    break;
                }
            }
        }

        // Remove by re-adding a cookie with same name/domain/path and expired date
        for (Cookie c : toRemove)
        {
            BasicClientCookie expired = new BasicClientCookie(c.getName(), null);
            expired.setDomain(c.getDomain());
            expired.setPath(c.getPath());
            expired.setExpiryDate(new Date(0)); // Expire immediately
            store.addCookie(expired);
        }
    }

    public static void copyFacebookCookies(CookieStore from, CookieStore to)
    {
        copySelectCookies(from, to, "facebook.com", "fbcdn.net", "messenger.com", "facebook.net");
    }

    public static void copyLievjournalCookies(CookieStore from, CookieStore to)
    {
        copySelectCookies(from, to, "livejournal.com", "livejournal.net", "olegmakarenko.ru", "dreamwidth.org");
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
