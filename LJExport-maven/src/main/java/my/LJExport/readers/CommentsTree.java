package my.LJExport.readers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.LJExport.Config;

public class CommentsTree
{
    private List<Comment> toplevelComments;
    private Map<String, Comment> thread2comment;

    public CommentsTree(List<Comment> list)
    {
        this.thread2comment = thread2comment(list);
        this.toplevelComments = new ArrayList<>();

        List<Comment> mores = new ArrayList<>();

        for (Comment c : list)
        {
            if (c.isMore())
            {
                mores.add(c);
                continue;
            }

            if (c.isEmptyPlaceholder())
                continue;

            if (c.isDeleted())
            {
                if (c.parent == null && c.thread == null)
                    continue;
                if (Config.False && c.parent != null && c.thread != null && c.parent.equals(c.thread))
                    continue;
            }

            if (c.level == 1)
            {
                toplevelComments.add(c);
            }
            else
            {
                Comment cparent = thread2comment.get(c.parent);
                if (cparent == null)
                    throwRuntimeException("Missing parent comment");

                if (c.level != cparent.level + 1)
                    throwRuntimeException("Child/parent comment levels are off");

                c.cParent = cparent;
                cparent.cChildren.add(c);
            }
        }

        for (Comment c : mores)
        {
            Comment cparent = thread2comment.get(c.parent);
            if (cparent == null)
                throwRuntimeException("Missing parent comment");
            cparent.doExpand = true;
        }

        for (Comment c : thread2comment.values())
        {
            if (c.isDeleted())
            {
                for (Comment cc : c.cChildren)
                {
                    if (Config.False && !cc.isDeleted())
                        throwRuntimeException("Deleted comment has un-deleted child comments");
                }

            }
        }
    }

    public Comment findFirstUnloadedOrToExpandComment()
    {
        for (Comment c : toplevelComments)
        {
            if (c.shouldLoadOrExpand())
                return c;

            Comment cc = findFirstUnloadedOrToExpandComment(c);
            if (cc != null)
                return cc;
        }

        return null;
    }

    private Comment findFirstUnloadedOrToExpandComment(Comment c)
    {
        if (c.shouldLoadOrExpand())
            return c;

        for (Comment cc : c.cChildren)
        {
            if (cc.shouldLoadOrExpand())
                return cc;

            Comment ccc = findFirstUnloadedOrToExpandComment(cc);
            if (ccc != null)
                return ccc;
        }

        return null;
    }

    public int countUnloadedOrUnexpandedComments()
    {
        int count = 0;
        for (Comment c : this.thread2comment.values())
        {
            if (c.shouldLoadOrExpand())
                count++;
        }

        return count;
    }

    public int totalComments()
    {
        return this.thread2comment.size();
    }

    private Map<String, Comment> thread2comment(List<Comment> list)
    {
        Map<String, Comment> m = new HashMap<>();

        for (Comment c : list)
        {
            if (c.isMore())
                continue;

            if (c.isEmptyPlaceholder())
                continue;

            if (c.thread == null)
                throwRuntimeException("Missing thread id");

            if (m.containsKey(c.thread))
                throwRuntimeException("Duplicate thread id " + c.thread);

            m.put(c.thread, c);
        }

        return m;
    }

    public void merge(Comment cload, List<Comment> list)
    {
        Comment c0 = list.get(0);
        if (cload.thread == null || c0.thread == null || !cload.thread.equals(c0.thread))
            throw new RuntimeException("While merging expanded comments: mismatching root comment thread id");
        if (cload.level == null || c0.level == null || !cload.level.equals(c0.level))
            throw new RuntimeException("While merging expanded comments: mismatching root comment level");

        List<Comment> mores = new ArrayList<>();

        for (Comment c : list)
        {
            if (c.isMore())
            {
                mores.add(c);
                continue;
            }

            if (c.isEmptyPlaceholder())
                continue;

            if (c.isDeleted())
            {
                if (c.parent == null && c.thread == null)
                    continue;
                if (Config.False && c.parent != null && c.thread != null && c.parent.equals(c.thread))
                    continue;
            }

            if (c.thread == null)
                throwRuntimeException("While merging expanded comments: missing comment thread id");

            Comment cto = thread2comment.get(c.thread);
            if (cto != null)
            {
                cto.merge(c);
            }
            else
            {
                if (c.level == 1)
                    throwRuntimeException("While merging expanded comments: unexpected top level comment");

                if (c.parent == null)
                    throwRuntimeException("While merging expanded comments: missing parent thread id");

                Comment cparent = thread2comment.get(c.parent);
                if (cparent == null)
                    throwRuntimeException("While merging expanded comments: missing parent thread");
                c.cParent = cparent;
                cparent.cChildren.add(c);
                thread2comment.put(c.thread, c);
            }
        }

        for (Comment c : mores)
        {
            Comment cparent = thread2comment.get(c.parent);
            if (cparent == null)
                throwRuntimeException("Missing parent comment");
            cparent.doExpand = true;
        }

        cload.doExpand = false;
        cload.attemptedToLoad = true;

        for (Comment c : thread2comment.values())
        {
            if (c.isDeleted())
            {
                for (Comment cc : c.cChildren)
                {
                    if (Config.False && !cc.isDeleted())
                        throwRuntimeException("Deleted comment has un-deleted child comments");
                }

            }
        }
    }
    
    public void checkHaveAllComments()
    {
        for (Comment c : thread2comment.values())
            c.checkHasData();
    }

    private static void throwRuntimeException(String msg)
    {
        throw new RuntimeException(msg);
    }
}
