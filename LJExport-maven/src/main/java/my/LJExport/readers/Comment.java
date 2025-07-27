package my.LJExport.readers;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONObject;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.url.UrlUtil;

public class Comment
{
    public final static String DEFAULT_USERPIC = "https://l-stat.livejournal.net/img/userpics/userpic-user.png";
    public final static String DEFAULT_UNAME = "Anonymous";
    public final static String DEFAULT_USERHEAD_URL = "https://l-stat.livejournal.net/img/userinfo_v8.svg?v=17080&amp;v=847";
    public final static String ANONYMOUS_USER_USERPIC = "https://l-stat.livejournal.net/img/userpics/userpic-anonymous.png?v=15821";

    // such as "31873680"
    public String thread;

    // such as "31872912" or null 
    public String parent;

    public Boolean loaded;
    public boolean doExpand = false;

    public boolean attemptedToLoad = false;

    // can be "more"
    public String type;

    // such as "https://bantaputu.livejournal.com/1044624.html?thread=31873680#t31873680"
    public String thread_url;

    // comment poster username e.g. "twincat"
    public String uname;
    public String dname;

    public String profile_url;
    public String journal_url;
    public String userhead_url;

    // such as "https://twincat.livejournal.com/"
    public String commenter_journal_base;

    // such as "May 29 2025, 16:37:53"
    public String ctime;

    // top level = 1
    public Integer level;

    // such as "https://l-userpic.livejournal.com/50425886/4107583"
    public String userpic;

    // subject line of the comment
    public String subject;

    // text body of the comment (html)
    public String article;

    public Boolean shown;
    public Boolean collapsed;

    // can be "deleted", "screened", "suspended", "spammed"
    public String leafclass;

    public Comment cParent;
    public List<Comment> cChildren = new ArrayList<>();

    public static Comment from(JSONObject jo, boolean validate, int deltaLevel)
    {
        Comment c = new Comment();

        c.type = getString(jo, "type");
        c.thread = getString(jo, "thread");

        c.parent = getString(jo, "parent");
        if (c.parent != null && c.parent.equals("0"))
            c.parent = null;

        c.loaded = getBoolean(jo, "loaded");
        c.thread_url = getString(jo, "thread_url");
        c.uname = getString(jo, "uname");
        c.dname = getString(jo, "dname");
        c.commenter_journal_base = getString(jo, "commenter_journal_base");
        c.ctime = getString(jo, "ctime");
        c.level = getInteger(jo, "level");
        c.userpic = getString(jo, "userpic");
        c.subject = getString(jo, "subject");
        c.article = getString(jo, "article");
        c.shown = getBoolean(jo, "shown");
        c.collapsed = getBoolean(jo, "collapsed");
        c.leafclass = getString(jo, "leafclass");

        c.handle_thread_url();

        JSONArray uarray = getJSONArray(jo, "username");
        if (uarray != null)
        {
            if (uarray.length() == 0)
            {
                // do nothing
            }
            else if (uarray.length() == 1)
            {
                JSONObject ujo = getJSONArrayElement(uarray, "username", 0);
                if (ujo != null)
                {
                    c.profile_url = getString(ujo, "profile_url");
                    c.journal_url = getString(ujo, "journal_url");
                    c.userhead_url = getString(ujo, "userhead_url");
                }
            }
            else
            {
                throwRuntimeException("Comment has username array with unexpected length " + uarray.length());
            }
        }

        if (!validate)
            return c;

        if (c.type != null)
        {
            switch (c.type)
            {
            case "more":
                if (c.parent == null)
                    throwRuntimeException("Comment of type more does not have parent");

                break;
            default:
                throwRuntimeException("Comment type is not null or more");
            }
        }

        if (c.type != null && c.type.equals("more"))
        {
            // allow no thread id
        }
        else
        {
            if (c.thread == null)
                throwRuntimeException("Comment does not have threadid");
        }

        if (c.level == null || c.level <= 0)
            throwRuntimeException("Missing comment level");

        c.level += deltaLevel;

        if (c.level == 1 && c.parent != null)
            throwRuntimeException("Top-level comment has a parent");

        if (c.level == 1 && !c.loaded && !c.isDeleted() && !c.isScreened() && !c.isSpammed())
            throwRuntimeException("Top-level comment is not loaded");

        return c;
    }

    public boolean isMore()
    {
        return type != null && type.equals("more");
    }

    public boolean isDeleted()
    {
        return leafclass != null && leafclass.equals("deleted");
    }

    public boolean isScreened()
    {
        return leafclass != null && leafclass.equals("screened");
    }

    public boolean isSuspended()
    {
        return leafclass != null && leafclass.equals("suspended");
    }

    public boolean isSpammed()
    {
        return leafclass != null && leafclass.equals("spammed");
    }

    public boolean isEmptyPlaceholder()
    {
        return thread == null && uname == null && ctime == null && loaded == null;
    }

    private static String getString(JSONObject jo, String key)
    {
        if (jo.isNull(key))
            return null;

        Object v = jo.get(key);
        if (v == null)
            return null;
        else if (v instanceof String)
            return (String) v;
        else if (v instanceof Integer)
            return ((Integer) v).toString();
        else
        {
            throwRuntimeException("Incorect JSON value type for comment key " + key);
            return null;
        }
    }

    private static Integer getInteger(JSONObject jo, String key)
    {
        String s = getString(jo, key);
        if (s == null)
            return null;
        return Integer.parseInt(s);
    }

    private static Boolean getBoolean(JSONObject jo, String key)
    {
        Integer v = getInteger(jo, key);
        if (v == null)
            return null;
        else if (v.equals(0))
            return false;
        else if (v.equals(1))
            return true;
        else
        {
            throwRuntimeException("Incorect JSON value for comment key " + key);
            return null;
        }
    }

    private static JSONObject getJSONArrayElement(JSONArray array, String key, int index)
    {
        if (array.isNull(index))
            return null;

        Object v = array.get(index);
        if (v == null)
            return null;
        else if (v instanceof JSONObject)
            return (JSONObject) v;
        else
        {
            throwRuntimeException("Incorect JSON value type for comment key " + key + "[" + index + "]");
            return null;
        }
    }

    private static JSONArray getJSONArray(JSONObject jo, String key)
    {
        if (jo.isNull(key))
            return null;

        Object v = jo.get(key);
        if (v == null)
            return null;
        else if (v instanceof JSONArray)
            return (JSONArray) v;
        else
        {
            throwRuntimeException("Incorect JSON value type for comment key " + key);
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static JSONObject getJSONObject(JSONObject jo, String key)
    {
        if (jo.isNull(key))
            return null;

        Object v = jo.get(key);
        if (v == null)
            return null;
        else if (v instanceof JSONObject)
            return (JSONObject) v;
        else
        {
            throwRuntimeException("Incorect JSON value type for comment key " + key);
            return null;
        }
    }

    private void handle_thread_url()
    {
        if (thread_url == null)
            return;

        String key = "?thread=";
        int index = thread_url.indexOf(key);
        if (index == -1)
            throwRuntimeException("Comment has invalid thread_url (no ?thread=)");

        String s = thread_url.substring(index + key.length());
        String sa[] = s.split("#");
        if (sa.length != 2 || !sa[1].equals("t" + sa[0]))
            throwRuntimeException("Comment has invalid thread_url (not xxx#txxx)");

        if (thread == null)
            thread = sa[0];
        else if (!thread.equals(sa[0]))
            throwRuntimeException("Comment has invalid thread_url (mismatching thread)");
    }

    public boolean shouldLoadOrExpand()
    {
        if (attemptedToLoad)
            return false;

        if (doExpand)
            return true;

        if (isDeleted())
            return false;

        return loaded == null || loaded == false;
    }

    // merge data for comment after expansion request
    public void merge(Comment c)
    {
        if (parent == null && c.parent == null)
        {
            checkMatch(level, c.level, "level");
            if (level != 1)
                throwRuntimeException("Comment data merge: missing parent for top-level comment");

        }
        else if (parent == null || c.parent == null || !parent.equals(c.parent))
        {
            throwRuntimeException("Comment data merge: mismatching parent");
        }

        if (loaded == null || c.loaded == null)
            throwRuntimeException("Comment data merge: missing loaded status");

        if (loaded && c.loaded)
        {
            checkMatch(thread_url, c.thread_url, "thread_url");
            checkMatch(uname, c.uname, "uname");
            checkMatch(dname, c.dname, "dname");
            checkMatch(profile_url, c.profile_url, "profile_url");
            checkMatch(journal_url, c.journal_url, "journal_url");
            userhead_url = checkMatch_userhead_url(userhead_url, c.userhead_url, "userhead_url");
            checkMatch(commenter_journal_base, c.commenter_journal_base, "commenter_journal_base");
            checkMatch(ctime, c.ctime, "ctime");
            checkMatch(level, c.level, "level");
            checkMatch(userpic, c.userpic, "userpic");
            checkMatch(subject, c.subject, "subject");
            checkMatchArticle(article, c.article, "article");
            checkMatch(shown, c.shown, "shown");
            checkMatch(collapsed, c.collapsed, "collapsed");
            checkMatch(leafclass, c.leafclass, "leafclass");
        }
        else if (!loaded && c.loaded)
        {
            thread_url = mergeValue(thread_url, c.thread_url, "thread_url");
            uname = mergeValue(uname, c.uname, "uname");
            dname = mergeValue(dname, c.dname, "dname");
            profile_url = mergeValue(profile_url, c.profile_url, "profile_url");
            journal_url = mergeValue(journal_url, c.journal_url, "journal_url");
            userhead_url = mergeValue_userhead_url(userhead_url, c.userhead_url, "userhead_url");
            commenter_journal_base = mergeValue(commenter_journal_base, c.commenter_journal_base, "commenter_journal_base");
            ctime = mergeValue(ctime, c.ctime, "ctime");
            checkMatch(level, c.level, "level");
            userpic = mergeValue(userpic, c.userpic, "userpic");
            subject = mergeValue(subject, c.subject, "subject");
            article = mergeValueArticle(article, c.article, "article");
            shown = c.shown;
            collapsed = c.collapsed;
            leafclass = c.leafclass;
            loaded = true;
        }
    }

    private void checkMatch(Object f1, Object f2, String fname)
    {
        if (f1 == null && f2 == null)
            return;
        if (f1 == null || f2 == null || !f1.equals(f2))
            throwRuntimeException("Comment data merge: diverging field " + fname);
    }

    private String checkMatch_userhead_url(String f1, String f2, String fname)
    {
        if (f1 == null && f2 == null)
            return null;

        if (Util.True)
        {
            if (f1 == null || f2 == null)
                throwRuntimeException("Comment data merge: diverging field " + fname);
        }
        else
        {
            if (f2 == null)
                return f1;

            if (f1 == null)
                return f2;
        }

        if (f1.equals(f2))
            return f2;

        if (differOnlyInV(f1, f2))
            return f2;

        throwRuntimeException("Comment data merge: diverging field " + fname);
        return null;
    }

    private boolean differOnlyInV(String url1, String url2)
    {
        try
        {
            URI uri1 = new URI(url1);
            URI uri2 = new URI(url2);

            // If the URLs are exactly the same, return true
            if (uri1.equals(uri2))
                return true;

            // Compare scheme, host, port, and path
            if (!Objects.equals(uri1.getScheme(), uri2.getScheme()) ||
                    !Objects.equals(uri1.getHost(), uri2.getHost()) ||
                    uri1.getPort() != uri2.getPort() ||
                    !Objects.equals(uri1.getPath(), uri2.getPath()) ||
                    !Objects.equals(uri1.getFragment(), uri2.getFragment()))
            {
                return false;
            }

            // Compare all query parameters except 'v'
            Map<String, List<String>> q1 = parseQueryParams(uri1.getRawQuery());
            Map<String, List<String>> q2 = parseQueryParams(uri2.getRawQuery());

            q1.remove("v");
            q2.remove("v");

            return q1.equals(q2);
        }
        catch (RuntimeException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex.getLocalizedMessage(), ex);
        }
    }

    private Map<String, List<String>> parseQueryParams(String query) throws Exception
    {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (query == null || query.isEmpty())
            return map;

        for (String pair : query.split("&"))
        {
            int idx = pair.indexOf('=');
            String key = idx > 0 ? url_decode(pair.substring(0, idx)) : url_decode(pair);
            String value = idx > 0 && idx + 1 < pair.length() ? url_decode(pair.substring(idx + 1)) : "";
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }

    private String url_decode(String s) throws Exception
    {
        return UrlUtil.decodeUrl(s);
    }

    private void checkMatchArticle(Object f1, Object f2, String fname)
    {
        if (f1 == null && f2 == null)
            return;
        if (f1 == null || f2 == null)
            throwRuntimeException("Comment data merge: diverging field " + fname);

        if (f1.equals(f2))
            return;

        if (!(f1 instanceof String && f2 instanceof String))
            throwRuntimeException("Comment data merge: diverging field " + fname);

        /*
         * Sometimes one call returns original image URI
         * and then second call returns imgprx wrapper.
         * 
         * Or 1st calls returns imgprx wrapper with one values
         * and 2nd with another.
         */
        String sf1 = (String) f1;
        String sf2 = (String) f2;
        String lsf1 = sf1.toLowerCase();
        String lsf2 = sf2.toLowerCase();
        if (lsf1.contains("<img") && lsf2.contains("<img") ||
                lsf1.contains("data-auth-token") ||
                lsf2.contains("data-auth-token"))
        {
            int count = 0;
            if (lsf1.contains("imgprx.livejournal.net") || lsf1.contains("data-auth-token"))
                count++;
            if (lsf2.contains("imgprx.livejournal.net") || lsf2.contains("data-auth-token"))
                count++;
            if (count != 0)
            {
                String x1 = JSOUP.filterOutImageTags(sf1);
                String x2 = JSOUP.filterOutImageTags(sf2);
                if (x1.equals(x2))
                    return;
            }
        }

        throwRuntimeException("Comment data merge: diverging field article");
    }

    private String mergeValue(String v0, String v2, String fname)
    {
        if (v0 == null)
            return v2;

        if (v0.length() == 0 && v2 != null)
            return v2;

        checkMatch(v0, v2, fname);

        return v2;
    }

    private String mergeValue_userhead_url(String v0, String v2, String fname)
    {
        if (v0 == null)
            return v2;

        if (v0.length() == 0 && v2 != null)
            return v2;

        return checkMatch_userhead_url(v0, v2, fname);
    }

    private String mergeValueArticle(String v0, String v2, String fname)
    {
        if (v0 == null)
            return v2;

        if (v0.length() == 0 && v2 != null)
            return v2;

        if (v0.equals(v2))
            return v0;

        String lv0 = v0.toLowerCase();
        String lv2 = v2.toLowerCase();

        if (lv0.contains("<img") && lv2.contains("<img"))
        {
            int count = 0;

            if (lv0.contains("imgprx.livejournal.net"))
                count++;
            if (lv2.contains("imgprx.livejournal.net"))
                count++;

            if (count != 0)
            {
                String x0 = JSOUP.filterOutImageTags(v0);
                String x2 = JSOUP.filterOutImageTags(v2);
                if (x0.equals(x2))
                {
                    if (lv0.contains("imgprx.livejournal.net"))
                        return v2;
                    else
                        return v0;
                }
            }
        }

        throwRuntimeException("Comment data merge: diverging field article");
        return null;
    }

    public void checkHasData()
    {
        if (isDeleted() || isSpammed())
            return;

        if (attemptedToLoad)
            return;

        if (level <= 0)
            throwRuntimeException("Comment misses level");

        if (article == null)
        {
            if (isSuspended())
                return;

            if (canHaveEmptyArticle())
            {
                article = "";
            }
            else
            {
                throwRuntimeException("Comment misses field article");
            }
        }

        String msg = "Comment has blank field ";

        if (isBlank(thread_url))
            throwRuntimeException(msg + "thread_url");

        if (isBlank(uname))
            uname = DEFAULT_UNAME;
        else if (isBlank(commenter_journal_base))
            throwRuntimeException(msg + "commenter_journal_base");

        if (isBlank(ctime))
            throwRuntimeException(msg + "ctime");

        if (isBlank(userpic))
            userpic = DEFAULT_USERPIC;
    }

    private boolean canHaveEmptyArticle()
    {
        /*
         * Degenerate cases
         */

        if (leafclass == null &&
                loaded == Boolean.TRUE &&
                level != null && level.equals(1))
        {
            if (eq(uname, "livejournal"))
            {
                return true;
            }

            if (eq(thread_url, "https://sergeytsvetkov.livejournal.com/1285867.html?thread=24018155#t24018155") &&
                    eq(uname, "ppetrovichh"))
            {
                return true;
            }
        }

        if (leafclass == null &&
                type == null &&
                loaded == Boolean.TRUE &&
                shown == Boolean.TRUE &&
                level != null)
        {
            return true;
        }

        return false;
    }

    private boolean eq(String s1, String s2)
    {
        if (s1 == null && s2 == null)
            return true;
        else if (s1 != null && s2 != null && s1.equals(s2))
            return true;
        else
            return false;
    }

    private boolean isBlank(String s)
    {
        return s == null || s.length() == 0;
    }

    private static void throwRuntimeException(String msg)
    {
        throw new RuntimeException(msg);
    }
}
