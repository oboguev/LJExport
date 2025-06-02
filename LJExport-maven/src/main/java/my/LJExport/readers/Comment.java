package my.LJExport.readers;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class Comment
{
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

    // can be "deleted"
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

        if (c.level == 1 && !c.loaded && !c.isDeleted())
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
        
        return doExpand || loaded == null || loaded == false;
    }

    private static void throwRuntimeException(String msg)
    {
        throw new RuntimeException(msg);
    }
    
    // merge data for comment after expansion request
    public void merge(Comment c)
    {
        if (parent == null || c.parent == null || !parent.equals(c.parent))
            throwRuntimeException("Comment data merge: mismatching parent");
        
        if (loaded == null || c.loaded == null)
            throwRuntimeException("Comment data merge: missing loaded status");
        
        if (loaded && c.loaded)
        {
            checkMatch(thread_url, c.thread_url, "thread_url");
            checkMatch(uname, c.uname, "uname");
            checkMatch(commenter_journal_base, c.commenter_journal_base, "commenter_journal_base");
            checkMatch(ctime, c.ctime, "ctime");
            checkMatch(level, c.level, "level");
            checkMatch(userpic, c.userpic, "userpic");
            checkMatch(subject, c.subject, "subject");
            checkMatch(article, c.article, "article");
            checkMatch(shown, c.shown, "shown");
            checkMatch(collapsed, c.collapsed, "collapsed");
            checkMatch(leafclass, c.leafclass, "leafclass");
        }
        else if (!loaded && c.loaded)
        {
            thread_url = mergeValue(thread_url, c.thread_url, "thread_url");
            uname = mergeValue(uname, c.uname, "uname");
            commenter_journal_base = mergeValue(commenter_journal_base, c.commenter_journal_base, "commenter_journal_base");
            ctime = mergeValue(ctime, c.ctime, "ctime");
            checkMatch(level, c.level, "level");
            userpic = mergeValue(userpic, c.userpic, "userpic");
            subject = mergeValue(subject, c.subject, "subject");
            article = mergeValue(article, c.article, "article");
            shown = c.shown;
            collapsed = c.collapsed;
            leafclass = c.leafclass;
        }
    }
    
    private void checkMatch(Object f1, Object f2, String fname)
    {
        if (f1 == null && f2 == null)
            return;
        if (f1 == null || f2 == null || !f1.equals(f2))
            throwRuntimeException("Comment data merge: diverging field " + fname);
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
}
