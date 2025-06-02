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

    private static void throwRuntimeException(String msg)
    {
        throw new RuntimeException(msg);
    }

    public void merge(Comment cload, List<Comment> list)
    {
        // ###
    }
}
