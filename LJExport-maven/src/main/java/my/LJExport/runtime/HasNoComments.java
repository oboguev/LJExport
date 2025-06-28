package my.LJExport.runtime;

import java.util.Set;

public class HasNoComments
{
    private static Set<String> hasNoComments;
    
    public static boolean rurlHasNoComments(String rurl) throws Exception
    {
        String href = LJUtil.userBase() + "/" + rurl;
        return hrefHasNoComments(href);
    }
    
    private static boolean hrefHasNoComments(String href) throws Exception
    {
        synchronized (HasNoComments.class)
        {
            if (hasNoComments == null)
                hasNoComments = Util.read_set("has-no-comments.txt");
        }

        String flip = Util.flipProtocol(href);
        if (hasNoComments.contains(href) || hasNoComments.contains(flip))
            return true;
        
        return false;
    }
}
