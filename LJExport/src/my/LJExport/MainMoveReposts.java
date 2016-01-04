package my.LJExport;

import java.io.File;
import java.util.*;

import org.jsoup.nodes.*;

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

public class MainMoveReposts extends PageParser
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
        Set<String> pageFiles = enumerateFiles(pagesDir);

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
                    String dir = getFileDirectory(dst);

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

    Set<String> enumerateFiles(String root) throws Exception
    {
        Set<String> fset = new HashSet<String>();
        File f = new File(root);
        if (!f.exists() || !f.isDirectory())
            throw new Exception("Directory " + root + " does not exist");
        enumerateFiles(fset, root, null);
        return fset;
    }

    void enumerateFiles(Set<String> fset, String root, String subpath) throws Exception
    {
        String xroot = root;
        if (subpath != null)
            xroot += File.separator + subpath;
        File xrf = new File(xroot);
        File[] xlist = xrf.listFiles();
        if (xlist == null)
            throw new Exception("Unable to enumerate files under " + xroot);
        for (File xf : xlist)
        {
            if (xf.isDirectory())
            {
                if (subpath == null)
                    enumerateFiles(fset, root, xf.getName());
                else
                    enumerateFiles(fset, root, subpath + File.separator + xf.getName());
            }
            else if (xf.getName().toLowerCase().endsWith(".html"))
            {
                if (subpath == null)
                    fset.add(xf.getName());
                else
                    fset.add(subpath + File.separator + xf.getName());
            }
        }
    }

    String getFileDirectory(String filepath) throws Exception
    {
        File d = new File(filepath).getParentFile();
        return d.getCanonicalPath();
    }

    boolean isRepost(String filepath) throws Exception
    {
        pageSource = Util.readFileAsString(filepath);
        parseHtml(pageSource);
        return isRepost();
    }
}
