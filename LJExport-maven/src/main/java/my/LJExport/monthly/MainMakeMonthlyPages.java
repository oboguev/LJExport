package my.LJExport.monthly;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.Util;

/*
 * Создать помесячные страницы с записями
 */
public class MainMakeMonthlyPages
{
    // private static String Users = "alex_vergin,asriyan,blog_10101,hokma,sergeytsvetkov";
    private static String Users = "blog_10101";

    public static void main(String[] args)
    {
        try
        {
            for (String user : Users.split(","))
            {
                user = user.trim();
                if (user.length() != 0)
                {
                    new MainMakeMonthlyPages().processUser(user);
                }
            }
        }
        catch (Exception ex)
        {
            Main.err("** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }

    private void processUser(String user) throws Exception
    {
        Config.User = user;
        Config.mangleUser();
        processUserSection("pages", true);
        processUserSection("reposts", false);
    }

    private void processUserSection(String whichDir, boolean required) throws Exception
    {
        String pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + whichDir;
        String monthlyPagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "monthly-" + whichDir;

        File fp = new File(pagesDir).getCanonicalFile();
        if (!fp.exists() || !fp.isDirectory())
            return;
        
        Main.out("");
        Main.out(String.format(">>> Processing %s for user %s", whichDir, Config.User));

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
                            month);
                    mp.process();
                }
            }
        }

        Main.out(String.format(">>> Completed processing %s for user %s", whichDir, Config.User));
    }

    /* ===================================================================== */

    private List<String> listNumericFolders(String root) throws Exception
    {
        return listNumericFiles(root, "", true);
    }

    private List<String> listNumericFiles(String root, String dotExtension, boolean isDir) throws Exception
    {
        File fproot = new File(root);

        List<Pair> pairs = new ArrayList<>();

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
            int number;
            try
            {
                number = Integer.parseInt(fnbase);
            }
            catch (Exception ex)
            {
                continue;
            }

            pairs.add(new Pair(number, fnbase));
        }

        // Sort by number
        pairs.sort(Comparator.comparingInt(p -> p.number));

        // Collect sorted values
        List<String> list = new ArrayList<>();
        for (Pair p : pairs)
            list.add(p.fnbase + (dotExtension != null ? dotExtension : ""));
        return list;
    }

    /* ===================================================================== */

    static class Pair
    {
        int number;
        String fnbase;

        Pair(int number, String fnbase)
        {
            this.number = number;
            this.fnbase = fnbase;
        }
    }
}
