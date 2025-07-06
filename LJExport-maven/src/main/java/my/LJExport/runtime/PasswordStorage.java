package my.LJExport.runtime;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import my.LJExport.Config;
import my.LJExport.Main;

public class PasswordStorage
{
    public static String getPassword() throws Exception
    {
        if (Config.LoginPassword != null)
            return Config.LoginPassword;

        String userHome = System.getProperty("user.home");
        Path passwordFilePath;

        String settingsFllename = "LJExport.settings";
        if (Config.isDreamwidthOrg())
        {
            settingsFllename = "LJExport-dreamwidth.settings";
        }
        else if (Config.isRossiaOrg())
        {
            settingsFllename = "LJExport-rossia-org.settings";
        }

        if (Util.isWindowsOS())
        {
            passwordFilePath = Paths.get(userHome, "AppData", "Local", "LJExport", settingsFllename);
        }
        else
        {
            passwordFilePath = Paths.get(userHome, ".config", "LJExport", settingsFllename);
        }

        File fp = passwordFilePath.toFile().getCanonicalFile();

        if (fp.exists())
        {
            Main.out(">>> Reading login password from file " + fp.getCanonicalPath());
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
            Main.out(">>> Saved scrambled login password to file " + fp.getCanonicalPath());

            if (!Util.isWindowsOS())
            {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(fp.toPath(), perms);
            }

            return Config.LoginPassword;
        }
    }
}
