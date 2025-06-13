package my.LJExport.tools;

import java.io.File;
import java.util.List;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.FileBackedMap;
import my.LJExport.runtime.Util;

/*
 * Преобразовать файл map-href-file.txt 
 * из старого формата с абсолютными путями файлов
 * в новый формат с относительными путями. 
 */
public class MainRelativizeLinksMap
{
    // private static String User = "alex_vergin";
    // private static String User = "bash_m_ak";
    // private static String User = "blog_10101";
    // private static String User = "genby";
    // private static String User = "nikital2014";
    // private static String User = "nilsky_nikolay";
    private static String User = "sergeytsvetkov";

    private static final String MapFileName = "map-href-file.txt";
    private static final String nl = "\n";
    private String linksDir;

    public static void main(String[] args)
    {
        try
        {
            MainRelativizeLinksMap self = new MainRelativizeLinksMap();
            self.do_user(User);
        }
        catch (Exception ex)
        {
            err("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }

    }

    private MainRelativizeLinksMap()
    {
    }

    private static void out(String s)
    {
        Main.out(s);
    }

    private static void err(String s)
    {
        Main.err(s);
    }

    private void do_user(String user) throws Exception
    {
        Config.User = user;
        Config.mangleUser();

        linksDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "links";

        out(">>> Processing download links for user " + Config.User);

        String mapFilePath = linksDir + File.separator + MapFileName;
        File fp = new File(mapFilePath);
        if (!fp.exists())
        {
            out(">>> Map file does not exist");
            return;
        }

        String originalContent = Util.readFileAsString(mapFilePath);
        List<String> list = Util.asList(originalContent.replace("\r\n", "\n"), "\n");
        String key = null;
        String value = null;
        int k = 0;
        StringBuilder sb = new StringBuilder();

        for (String s : list)
        {
            s = s.trim();
            if (s.length() == 0)
                continue;
            k++;

            // out("[" + k + "] " + s);

            if (k == 1)
            {
                key = s;
            }
            else if (k == 2)
            {
                value = s;

            }
            else if (k == 3)
            {
                if (!s.equals(FileBackedMap.SEPARATOR))
                    throw new Exception("Unexpected map file structure (no separator)");

                String prefix = linksDir + File.separator;
                if (!value.startsWith(prefix))
                    throw new Exception("Unexpected map file structure (mismatching prefix)");

                sb.append(key + nl);
                sb.append(mapValue(value, prefix) + nl);
                sb.append(FileBackedMap.SEPARATOR + nl);

                key = null;
                value = null;
                k = 0;
            }
        }

        if (k != 0)
            throw new Exception("Unexpected map file structure");

        Util.writeToFileSafe(mapFilePath + ".sav", originalContent);
        Util.writeToFileSafe(mapFilePath, sb.toString());

        out(">>> Completed processing download links for user " + Config.User);
    }

    private String mapValue(String value, String prefix) throws Exception
    {
        if (!value.startsWith(prefix))
            throw new Exception("Unexpected map file structure (mismatching prefix)");

        value = value.substring(prefix.length());
        value = value.replace("\\", "/");

        return value;
    }
}
