package my.LJExport.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import my.LJExport.Config;

/**
 * Enumerate downloaded users
 */
public class EnumUsers
{
    public enum Options
    {
        DEFAULT, ALLOW_DOTS_DASHES
    }

    public static List<String> allUsers(Options... options) throws Exception
    {
        List<String> users = new ArrayList<>();
        File fpRoot = new File(Config.DownloadRoot).getCanonicalFile();

        for (File fp : fpRoot.listFiles())
        {
            if (!fp.isDirectory())
                continue;

            String user = fp.getName();

            if (!contains(Options.ALLOW_DOTS_DASHES, options))
            {
                if (user.contains(".") || user.contains("-"))
                    continue;
            }
            
            if (user.startsWith("@"))
                continue;

            File fpPages = new File(fp, "pages");
            if (!fpPages.exists() || !fpPages.isDirectory())
                continue;

            users.add(user);
        }

        // known non-user directories with different content and layout
        users.remove("@debug");
        users.remove("@temp");
        users.remove("@admin");
        users.remove("colonelcassad.my_comments");
        users.remove("d_olshansky.comments");
        users.remove("d_olshansky.ljsearch");
        users.remove("krylov.comments");
        users.remove("oboguev.comments");
        users.remove("olshansky");
        users.remove("udod99.comments");
        users.remove("udod99.journal");
        users.remove("udod99-epub");
        users.remove("udod99-v1");
        users.remove("udod99-v2");

        Collections.sort(users);

        return users;
    }

    public static List<String> allUsers(String fromUser, Options... options) throws Exception
    {
        List<String> users = allUsers(options);

        if (fromUser != null)
        {
            // Find the index of the target user
            int index = users.indexOf(fromUser);

            // Throw exception if not found
            if (index == -1)
                throw new IllegalArgumentException("User '" + fromUser + "' not found");

            // Remove all elements before the found user
            users.subList(0, index).clear();
        }

        return users;
    }

    private static boolean contains(Options optionToFind, Options... options)
    {
        if (options == null || optionToFind == null)
            return false;

        for (Options option : options)
        {
            if (option == optionToFind)
                return true;
        }

        return false;
    }
}
