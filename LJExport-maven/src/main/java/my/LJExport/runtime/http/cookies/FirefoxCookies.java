package my.LJExport.runtime.http.cookies;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;

import my.LJExport.runtime.Util;

/*
 * Read cookies from Firefox profile
 */
public class FirefoxCookies
{
    public static CookieStore loadCookiesFromFirefox() throws Exception
    {
        return loadCookiesFromFirefox(findActiveFirefoxProfile().getCanonicalPath());
    }

    public static CookieStore loadCookiesFromFirefox(String profilePath) throws Exception
    {
        String dbPath = profilePath + File.separator + "cookies.sqlite";

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try
        {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT host, name, value, path, expiry, isSecure, isHttpOnly FROM moz_cookies");

            CookieStore cookieStore = new BasicCookieStore();
            while (rs.next())
            {
                String domain = rs.getString("host");
                String name = rs.getString("name");
                String value = rs.getString("value");
                String path = rs.getString("path");
                long expiry = rs.getLong("expiry");
                boolean isSecure = rs.getInt("isSecure") != 0;
                boolean isHttpOnly = rs.getInt("isHttpOnly") != 0;

                BasicClientCookie cookie = new BasicClientCookie(name, value);
                cookie.setDomain(domain);
                cookie.setPath(path);
                cookie.setExpiryDate(new Date(expiry * 1000L)); // expiry is in seconds
                cookie.setSecure(isSecure);
                cookie.setAttribute("httponly", isHttpOnly ? "true" : "false");

                cookieStore.addCookie(cookie);
            }

            return cookieStore;
        }
        finally
        {
            Util.safeClose(rs);
            Util.safeClose(stmt);
            Util.safeClose(conn);
        }
    }

    /* =================================================================================== */

    public static File findActiveFirefoxProfile() throws Exception
    {
        File iniFile = getProfilesIniPath();
        if (!iniFile.exists())
            throw new FileNotFoundException("Firefox profiles.ini not found: " + iniFile.getAbsolutePath());

        IniData ini = parseIni(iniFile);

        // Step 1: Check Install section
        Optional<String> installProfilePath = findInstallDefaultProfile(ini);
        if (installProfilePath.isPresent())
        {
            File profileDir = resolveProfilePath(installProfilePath.get(), true);
            if (profileDir.exists())
                return profileDir;
            else
                throw new FileNotFoundException("Profile dir from [Install] not found: " + profileDir.getAbsolutePath());
        }

        // Step 2: Fall back to [ProfileN] with Default=1
        List<ProfileEntry> profiles = findProfileEntries(ini);

        List<ProfileEntry> defaults = new ArrayList<>();
        for (ProfileEntry p : profiles)
        {
            if (p.isDefault)
            {
                defaults.add(p);
            }
        }

        if (defaults.isEmpty())
        {
            throw new IllegalStateException("No default Firefox profile found in [ProfileN] sections");
        }
        else if (defaults.size() > 1)
        {
            throw new IllegalStateException("More than one Default=1 profile found");
        }

        File profileDir = resolveProfilePath(defaults.get(0).path, defaults.get(0).isRelative);
        if (!profileDir.exists())
        {
            throw new FileNotFoundException("Default profile path not found: " + profileDir.getAbsolutePath());
        }

        return profileDir;
    }

    private static File getProfilesIniPath()
    {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win"))
        {
            return new File(System.getenv("APPDATA"), "Mozilla/Firefox/profiles.ini");
        }
        else
        {
            return new File(System.getProperty("user.home"), ".mozilla/firefox/profiles.ini");
        }
    }

    private static File getProfilesBaseDir()
    {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win"))
        {
            return new File(System.getenv("APPDATA"), "Mozilla/Firefox");
        }
        else
        {
            return new File(System.getProperty("user.home"), ".mozilla/firefox");
        }
    }

    private static Optional<String> findInstallDefaultProfile(IniData ini)
    {
        for (String section : ini.sections.keySet())
        {
            if (section.startsWith("Install"))
            {
                String path = ini.get(section, "Default");
                if (path != null && !path.isEmpty())
                {
                    return Optional.of(path);
                }
            }
        }
        return Optional.empty();
    }

    private static List<ProfileEntry> findProfileEntries(IniData ini)
    {
        List<ProfileEntry> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : ini.sections.entrySet())
        {
            String section = entry.getKey();
            if (section.matches("Profile\\d+"))
            {
                Map<String, String> map = entry.getValue();
                ProfileEntry p = new ProfileEntry();
                p.name = map.get("Name");
                p.path = map.get("Path");
                p.isRelative = "1".equals(map.get("IsRelative"));
                p.isDefault = "1".equals(map.get("Default"));
                result.add(p);
            }
        }
        return result;
    }

    private static File resolveProfilePath(String path, boolean isRelative)
    {
        File base = getProfilesBaseDir();
        return isRelative ? new File(base, path) : new File(path);
    }

    // Data structure for INI parsing
    private static class IniData
    {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();

        public void put(String section, String key, String value)
        {
            sections.computeIfAbsent(section, k -> new LinkedHashMap<>()).put(key, value);
        }

        public String get(String section, String key)
        {
            Map<String, String> sec = sections.get(section);
            return (sec != null) ? sec.get(key) : null;
        }
    }

    private static IniData parseIni(File file) throws IOException
    {
        IniData ini = new IniData();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        String currentSection = null;

        while ((line = reader.readLine()) != null)
        {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#"))
                continue;

            if (line.startsWith("[") && line.endsWith("]"))
            {
                currentSection = line.substring(1, line.length() - 1).trim();
            }
            else if (currentSection != null && line.contains("="))
            {
                String[] parts = line.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();
                ini.put(currentSection, key, value);
            }
        }

        reader.close();
        return ini;
    }

    private static class ProfileEntry
    {
        @SuppressWarnings("unused")
        String name;
        String path;
        boolean isRelative;
        boolean isDefault;
    }
}
