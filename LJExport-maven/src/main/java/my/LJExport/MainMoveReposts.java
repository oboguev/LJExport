package my.LJExport;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import my.LJExport.readers.PageParserPassive;
import my.LJExport.runtime.Util;

// import org.jsoup.nodes.*;

/*
 * This program moves repost records from Config.DownloadRoot/pages
 * to Config.DownloadRoot/repost.
 * 
 * A repost is a record which:
 * 
 *     - has been reposted
 *     - does not contain additional data in the body except the repost  
 *     - has no comments
 *     - title is blank or the same as the source record
 */

public class MainMoveReposts extends PageParserPassive
{
    private String pagesDir;
    private String repostsDir;

    public static void main(String[] args)
    {
        MainMoveReposts main = new MainMoveReposts();
        main.do_main(args);
    }

    private void do_main(String[] args)
    {
        try
        {
            StringTokenizer st = new StringTokenizer(Config.Users, ", \t\r\n");
            int nuser = 0;

            while (st.hasMoreTokens())
            {
                String user = st.nextToken();
                user = user.trim().replace("\t", "").replace(" ", "");
                if (!user.equals(""))
                {
                    if (nuser++ != 0)
                    {
                        out("");
                    }

                    if (Main.isAborting())
                        break;
                    
                    do_user(user);
                }
            }

        }
        catch (Exception ex)
        {
            err("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

    public void do_user(String user) throws Exception
    {
        offline = true;

        Config.User = user;
        out(">>> Processing reposts for user " + Config.User);

        pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "pages";
        repostsDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "reposts";

        Set<String> createdDirs = new HashSet<String>();
        List<String> pageFiles = Util.enumerateOnlyHtmlFiles(pagesDir);

        int nTotal = pageFiles.size();
        int nCurrent = 0;

        for (String s : pageFiles)
        {
            String src = pagesDir + File.separator + s;

            try
            {
                if (isRepost(src))
                {
                    String dst = repostsDir + File.separator + s;
                    String dir = Util.getFileDirectory(dst);

                    if (!createdDirs.contains(dir))
                    {
                        Util.mkdir(dir);
                        createdDirs.add(dir);
                    }

                    File fdst = new File(dst);
                    if (fdst.exists())
                        fdst.delete();

                    File fsrc = new File(src);
                    fsrc.renameTo(fdst);
                }

                if (0 == (++nCurrent % 100))
                {
                    double fpct = 100.0 * ((double) nCurrent / nTotal);
                    out(">>> Processed [" + Config.User + "] " + nCurrent + "/" + nTotal + " (" + String.format("%.2f", fpct)
                        + "%)");
                }
            }
            catch (Exception ex)
            {
                throw new Exception("Error while processing " + src, ex);
            }
        }

        out(">>> Completed processing reposts for user " + Config.User);
    }

    boolean isRepost(String filepath) throws Exception
    {
        pageSource = Util.readFileAsString(filepath);
        parseHtml(pageSource);
        return isRepost();
    }
}
