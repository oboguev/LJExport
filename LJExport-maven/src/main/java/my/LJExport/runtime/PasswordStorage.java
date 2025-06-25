package my.LJExport.runtime;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import my.LJExport.Config;

public class PasswordStorage
{
    public static String getPassword() throws Exception
    {
        if (Config.LoginPassword != null)
            return Config.LoginPassword;

        String userHome = System.getProperty("user.home");
        Path passwordFilePath;

        if (Util.isWindowsOS())
        {
            passwordFilePath = Paths.get(userHome, "AppData", "Local", "LJExport", "LJExport.settings");
        }
        else
        {
            passwordFilePath = Paths.get(userHome, ".config", "LJExport", "LJExport.settings");
        }

        File fp = passwordFilePath.toFile().getCanonicalFile();

        if (fp.exists())
        {
            String s = Util.readFileAsString(fp.getCanonicalPath());
            s = s.trim();
            if (!s.startsWith("pw="))
                throw new Exception("Incorrect file " + fp.getCanonicalPath());
            s = Util.stripStart(s, "pw=");
            s = s.trim();
            s = PasswordScrambler.unscramble(s);
            Config.LoginPassword = s;
            return s;
        }
        else
        {
            Config.promptLoginPassword();

            String s = PasswordScrambler.scramble(Config.LoginPassword);

            File fpDir = fp.getParentFile();
            if (!fpDir.exists())
                fpDir.mkdirs();

            Util.writeToFileSafe(fp.getCanonicalPath(), "pw=" + s);
            return Config.LoginPassword;
        }
    }
}
