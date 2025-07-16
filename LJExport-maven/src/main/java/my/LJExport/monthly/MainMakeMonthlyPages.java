package my.LJExport.monthly;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.EnumUsers;
import my.LJExport.runtime.Util;

/*
 * Создать помесячные страницы с записями
 * 
 * Use stack size: -Xss16m
 */
public class MainMakeMonthlyPages
{
    private static final String ALL_USERS = "<all>";
    private static final String AllUsersFromUser = null;
    // private static final String AllUsersFromUser = "olegnemen";

    // private static String Users = "alex_vergin,asriyan,blog_10101,hokma,sergeytsvetkov";
    // private static String Users = "alex_vergin";
    // private static final String Users = ALL_USERS;
    // private static String Users = "amfora,colonelcassad,fluffyduck2,genby,kot_begemott,lasido,sergeytsvetkov,von_hoffmann";
    // private static String Users = "hurtmann,maxim_sokolov,obsrvr,ru_nationalism,schegloff";
    // private static String Users = "elcour,meast_ru";
    // private static String Users = "udod99.lj-rossia-org";
    // private static String Users = "harmfulgrumpy.dreamwidth-org";
    private static String Users = "maxim_sokolov";

    public static void main(String[] args)
    {
        try
        {
            String users = Users;

            if (users.equals(ALL_USERS))
            {
                List<String> list = EnumUsers.allUsers(AllUsersFromUser, EnumUsers.Options.DEFAULT);
                users = String.join(",", list);
            }

            boolean divider = false;

            for (String user : users.split(","))
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
                        if (Main.isAborting())
                            break;

                        if (divider)
                        {
                            Main.out("");
                            Main.out("===========================================================================");
                            Main.out("");
                        }
                        new MainMakeMonthlyPages().processUser(user, false);
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

    protected void processUser(String user, boolean ljsearch) throws Exception
    {
        Config.User = user;
        Config.mangleUser();
        processUserSection("pages", ljsearch, true);
        processUserSection("reposts", ljsearch, false);
    }

    private void processUserSection(String whichDir, boolean ljsearch, boolean required) throws Exception
    {
        String pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + whichDir;
        String monthlyPagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "monthly-" + whichDir;

        File fp = new File(pagesDir).getCanonicalFile();
        if (!fp.exists() || !fp.isDirectory())
            return;

        Main.out("");
        Main.out(String.format(">>> Processing monthly %s for user %s", whichDir, Config.User));

        if (!Util.deleteDirectoryTree(monthlyPagesDir))
            throw new Exception("Was unable to delete " + monthlyPagesDir);

        List<String> years = listNumericFolders(pagesDir);
        for (String year : years)
        {
            String pagesYearDir = pagesDir + File.separator + year;
            List<String> months = listNumericFolders(pagesYearDir);
            for (String month : months)
            {
                String pagesMonthDir = pagesYearDir + File.separator + month;
                List<String> pageFileNames = listNumericFiles(pagesMonthDir, ".html", false);
                if (pageFileNames.size() != 0)
                {
                    Main.out(String.format("Processing [%s] %s-%s", Config.User, year, month));
                    MonthProcessor mp = new MonthProcessor(pagesMonthDir,
                            pageFileNames,
                            String.format("%s%s%s-%s", monthlyPagesDir + File.separator, year + File.separator, year, month),
                            year,
                            month,
                            whichDir,
                            ljsearch);
                    mp.process();
                }
            }
        }

        new BuildNavigationIndex(monthlyPagesDir).buildNavigation();

        Main.out(String.format(">>> Completed processing monthly %s for user %s", whichDir, Config.User));
    }

    /* ===================================================================== */

    private List<String> listNumericFolders(String root) throws Exception
    {
        return listNumericFiles(root, "", true);
    }

    private List<String> listNumericFiles(String root, String dotExtension, boolean isDir) throws Exception
    {
        File fproot = new File(root);

        List<Pair<Integer, String>> pairs = new ArrayList<>();

        for (File fp : fproot.listFiles())
        {
            String fn = fp.getName();

            if (dotExtension != null && dotExtension.length() != 0)
            {
                if (fn.endsWith(dotExtension) != fn.toLowerCase().endsWith(dotExtension.toLowerCase()))
                    throw new Exception("File name extension case is ambiguous");

                if (!fn.endsWith(dotExtension))
                    continue;
            }

            if (fp.isDirectory() != isDir)
                continue;

            String fnbase = fn;
            if (dotExtension != null && dotExtension.length() != 0)
                fnbase = Util.stripTail(fn, dotExtension);

            // eliminate non-numerics
            // for example 1000-1.html or 1000.1.html
            int number;
            try
            {
                number = Integer.parseInt(fnbase);
            }
            catch (Exception ex)
            {
                continue;
            }

            pairs.add(new Pair<Integer, String>(number, fnbase));
        }

        // Sort by number
        pairs.sort(Comparator.comparingInt(p -> p.number));

        // Collect sorted values
        List<String> list = new ArrayList<>();
        for (Pair<Integer, String> p : pairs)
            list.add(p.fnbase + (dotExtension != null ? dotExtension : ""));
        return list;
    }

    /* ===================================================================== */

    static private class Pair<TA, TB>
    {
        TA number;
        TB fnbase;

        Pair(TA number, TB fnbase)
        {
            this.number = number;
            this.fnbase = fnbase;
        }
    }
}