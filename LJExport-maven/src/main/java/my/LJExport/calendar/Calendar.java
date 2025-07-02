package my.LJExport.calendar;

import java.util.*;

import org.apache.http.HttpStatus;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.LJUtil;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.xml.JSOUP;

public class Calendar
{
    public static Vector<String> Records;
    private static Map<String, String> map_record_yyyy_mm;

    public static void init() throws Exception
    {
        Records = new Vector<String>();
        map_record_yyyy_mm = new HashMap<String, String>();
    }

    public static void index() throws Exception
    {
        Set<String> set_years = new HashSet<String>();
        Set<String> set_months = new HashSet<String>();
        Set<String> set_records = new HashSet<String>();

        StringBuilder sb = new StringBuilder();

        out(">>> Indexing top-level calendar ...");

        /*
         * LJ performs circular redirect to set a cookie.
         * Apache HTTP client is not smart enough to understand it and throws an exception.
         */
        Web.Response r;
        try
        {
            r = Web.get(LJUtil.userBase() + "/calendar");
        }
        catch (Exception ex)
        {
            r = Web.get(LJUtil.userBase() + "/calendar");
        }

        if (r.code != HttpStatus.SC_OK)
            throw new Exception("Unable to read user records calendar: " + Web.describe(r.code));

        List<String> hrefs = JSOUP.extractHrefs(r.body, r.finalUrl);
        for (String href : hrefs)
        {
            if (LJUtil.isJournalUrl(href, sb))
            {
                String url = Util.stripParametersAndAnchor(sb.toString());
                url = Util.stripLastChar(url, '/');
                if (isYearUrl(url) && isLoadableYear(url))
                    set_years.add(url);
                else if (isMonthUrl(url) && isLoadableMonth(url))
                    set_months.add(url);
            }
        }

        out(">>> Indexing yearly calendars ...");

        for (String yyyy : Util.sort(set_years))
        {
            out(">>>   Indexing [" + Config.User + "] calendar for year " + yyyy);
            r = Web.get(url_yyyy(yyyy));
            if (r.code != HttpStatus.SC_OK)
                throw new Exception("Unable to read user records calendar: " + Web.describe(r.code));

            hrefs = JSOUP.extractHrefs(r.body, r.finalUrl);
            for (String href : hrefs)
            {
                if (LJUtil.isJournalUrl(href, sb))
                {
                    String url = Util.stripParametersAndAnchor(sb.toString());
                    url = Util.stripLastChar(url, '/');
                    if (isMonthUrl(url) && isLoadableMonth(url))
                        set_months.add(url);
                }
            }
        }

        out(">>> Indexing monthly calendars ...");

        for (String yyyy_mm : Util.sort(set_months))
        {
            out(">>>   Indexing [" + Config.User + "] calendar for " + yyyy_mm);
            r = Web.get(url_yyyy_mm(yyyy_mm));
            if (r.code != HttpStatus.SC_OK)
                throw new Exception("Unable to read user records calendar: " + Web.describe(r.code));

            hrefs = JSOUP.extractHrefs(r.body, r.finalUrl);
            for (String href : hrefs)
            {
                if (LJUtil.isJournalRecordUrl(href, sb))
                {
                    String rurl = sb.toString();
                    set_records.add(rurl);
                    map_record_yyyy_mm.put(rurl, yyyy_mm);
                }
            }
        }

        Records = Util.sortAsVector(set_records);

        if (Config.RandomizeLoadOrder)
            Records = Util.randomize(Records);

        out(">>> Located " + Records.size() + " records");
    }

    private static boolean isYearUrl(String s) throws Exception
    {
        return s.length() == 4 && Character.isDigit(s.charAt(0)) && Character.isDigit(s.charAt(1))
               && Character.isDigit(s.charAt(2)) && Character.isDigit(s.charAt(3));
    }

    private static boolean isMonthUrl(String s) throws Exception
    {
        return s.length() == 7 && Character.isDigit(s.charAt(0)) && Character.isDigit(s.charAt(1))
               && Character.isDigit(s.charAt(2)) && Character.isDigit(s.charAt(3)) && s.charAt(4) == '/'
               && Character.isDigit(s.charAt(5)) && Character.isDigit(s.charAt(6));
    }

    private static boolean isLoadableYear(String s) throws Exception
    {
        if (Config.LoadSince == null && Config.LoadTo == null)
            return true;

        int yyyy = Integer.valueOf(s);

        if (Config.LoadSince != null && yyyy < Config.LoadSince.yyyy)
            return false;

        if (Config.LoadTo != null && yyyy > Config.LoadTo.yyyy)
            return false;

        return true;
    }

    private static boolean isLoadableMonth(String s) throws Exception
    {
        if (Config.LoadSince == null && Config.LoadTo == null)
            return true;

        int yyyy = Integer.valueOf(s.substring(0, 4));
        int mm = Integer.valueOf(s.substring(5));

        if (Config.LoadSince != null)
        {
            if (yyyy < Config.LoadSince.yyyy)
                return false;
            if (yyyy == Config.LoadSince.yyyy && mm < Config.LoadSince.mm)
                return false;
        }

        if (Config.LoadTo != null)
        {
            if (yyyy > Config.LoadTo.yyyy)
                return false;
            if (yyyy == Config.LoadTo.yyyy && mm > Config.LoadTo.mm)
                return false;
        }

        return true;
    }

    public static String get_record_yyyy_mm(String rurl) throws Exception
    {
        return map_record_yyyy_mm.get(rurl);
    }

    private static String url_yyyy(String yyyy) throws Exception
    {
        /*
         * Workaround for the bug in HttpClient incorrectly detecting circular redirect
         * when the "else" form of the URL is used. 
         */
        if (Config.User.charAt(0) == '_' && Util.lastChar(Config.User) == '_')
            return "http://users." + Config.Site + "/" + Config.User + "/" + yyyy + "/";
        else
            return LJUtil.userBase()  + "/calendar/" + yyyy;
    }

    private static String url_yyyy_mm(String yyyy_mm) throws Exception
    {
        /*
         * Workaround for the bug in HttpClient incorrectly detecting circular redirect
         * when the "else" form of the URL is used. 
         */
        if (Config.User.charAt(0) == '_' && Util.lastChar(Config.User) == '_')
            return "http://users." + Config.Site + "/" + Config.User + "/" + yyyy_mm + "/";
        else
            return LJUtil.userBase() + "/calendar/" + yyyy_mm;
    }

    public static void out(String s) throws Exception
    {
        Main.out(s);
    }
}
