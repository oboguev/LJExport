package my.WebArchiveOrg.customize;

import java.util.regex.*;

public class Exclude
{
    /*
    * Exclude URLs ending in 
    * 
    * /nationalism.org/forum/71920r
    * /nationalism.org/forum/delete?72816,56
    * /nationalism.org/forum/move?72816,56
    * /nationalism.org/forum/read?72826,56
    * /nationalism.org/forum/read?72816,56e
    * /nationalism.org/forum/edit?56,72816
    * 
    * /nationalism.org/forum/~1/71920r
    * /nationalism.org/forum/~1/delete?72816,56
    * /nationalism.org/forum/~1/move?72816,56
    * /nationalism.org/forum/~1/read?72826,56
    * /nationalism.org/forum/~1/read?72816,56e
    * /nationalism.org/forum/~1/edit?56,72816
    * 
    * nationalism.org/forumread?63393,56  
    * nationalism.org/forumread?63528  
    * nationalism.org/forumuserpeek?28350
    * 
    * Also with www.nationalism.org instead of nationalism.org.
    */

    private static final Pattern NATIONALISM_ORG_FORUM_CONTROL_ENDING_PATTERN = Pattern.compile(
            "nationalism\\.org/forum(?:/~1)?/" +
                    "(" +
                    "\\d+[a-zA-Z]{1}|" + // must end with at least one letter (e.g. 60079r, 72816e)
                    "(delete|move|read|edit)\\?\\d+(,[a-zA-Z0-9]+)?" +
                    ")$");

    private static final Pattern NATIONALISM_ORG_FORUM_MISC_PATTERN = Pattern.compile(
            "nationalism\\.org/(forumread|forumuserpeek)\\?\\d+(,\\d+)?$");

    public static boolean isNationalismOrgForumControlURL(String url)
    {
        if (url == null)
            return false;

        int idx = url.indexOf("nationalism.org/forum");
        if (idx == -1)
            return false;

        String tail = url.substring(idx);
        return NATIONALISM_ORG_FORUM_CONTROL_ENDING_PATTERN.matcher(tail).matches() ||
                NATIONALISM_ORG_FORUM_MISC_PATTERN.matcher(tail).matches();
    }
}
