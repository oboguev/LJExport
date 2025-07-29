package my.LJExport.runtime.browsers;

import java.io.File;

import my.LJExport.runtime.Util;

public class GeckoDriverFinder
{
    public static String findGeckoDriverInPath()
    {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty())
        {
            return null;
        }

        String[] paths = pathEnv.split(File.pathSeparator);
        String exeName = Util.isWindowsOS() ? "geckodriver.exe" : "geckodriver";

        for (String dir : paths)
        {
            File file = new File(dir, exeName);
            if (file.isFile() && file.canExecute())
            {
                return file.getAbsolutePath();
            }
        }

        return null; // Not found
    }

    // Example usage
    public static void main(String[] args)
    {
        String pathToDriver = findGeckoDriverInPath();
        if (pathToDriver != null)
        {
            System.out.println("Found geckodriver: " + pathToDriver);
            System.setProperty("webdriver.gecko.driver", pathToDriver);
        }
        else
        {
            System.err.println("geckodriver not found in PATH");
        }
    }
}
