package my.LJExport.runtime.lj;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import my.LJExport.Config;

public class LJExportInformation
{
    public static final String IsDownloadedFromWebArchiveOrg = "is-downloaded-from-web-archive-org";
    
    public static Properties load() throws Exception
    {
        Properties props = new Properties();
        
        File fp = new File(path()).getCanonicalFile();
        if (fp.exists())
        {
            try (FileInputStream in = new FileInputStream(fp.getCanonicalPath())) 
            {
                props.load(in);
            }
        }
        
        return props;
    }

    public static void save(Properties props) throws Exception
    {
        File fp = new File(path()).getCanonicalFile();
        try (FileOutputStream out = new FileOutputStream(fp.getCanonicalPath())) {
            props.store(out, "LJExport Configuration");
        }        
    }

    private static String path()
    {
        return Config.DownloadRoot + File.separator + Config.User + File.separator + "ljexport-information.ini";
    }
}
