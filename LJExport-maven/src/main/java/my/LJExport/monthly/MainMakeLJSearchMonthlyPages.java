package my.LJExport.monthly;

import my.LJExport.Config;
import my.LJExport.Main;

/*
 * Создать помесячные страницы с записями в папке загруженной с LJSearch (https://ljsear.ch).
 * 
 * Use stack size: -Xss16m
 */
public class MainMakeLJSearchMonthlyPages extends MainMakeMonthlyPages
{
    // private static String Users = "udod99.journal,d_olshansky.ljsearch";
    // private static String Users = "udod99.journal";
    private static String Users = "d_olshansky.ljsearch";

    public static void main(String[] args)
    {
        try
        {
            boolean divider = false;

            for (String user : Users.split(","))
            {
                user = user.trim();
                if (user.length() != 0)
                {
                    if (Config.False)
                    {
                        Main.out(user);
                    }
                    else
                    {
                        if (divider)
                        {
                            Main.out("");
                            Main.out("===========================================================================");
                            Main.out("");
                        }
                        new MainMakeLJSearchMonthlyPages().processUser(user, true);
                        divider = true;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Main.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }

        Main.playCompletionSound();
    }
}
