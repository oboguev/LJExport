package my.LJExport.runtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class YYYY_MM_DD
{
    public int yyyy;
    public int mm;
    public int dd;

    public YYYY_MM_DD(int yyyy, int mm, int dd)
    {
        this.yyyy = yyyy;
        this.mm = mm;
        this.dd = dd;
    }

    @Override
    public String toString()
    {
        return String.format("%04d-%02d-%02d", yyyy, mm, dd);
    }

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter LONG_US_DATE_TIME = DateTimeFormatter.ofPattern("MMMM d yyyy, HH:mm", Locale.ENGLISH);

    /**
     * Parses either
     * <pre>
     * yyyy-MM-dd HH:mm:ss      (e.g. 2004-12-08 10:00:00)
     * MMMM d yyyy, HH:mm       (e.g. August 15 2020, 10:38)
     * </pre>
     * and returns its Y-M-D components.
     *
     * @throws IllegalArgumentException if the text matches neither pattern
     */
    public static YYYY_MM_DD from(String text)
    {
        LocalDateTime dateTime = tryParse(text, ISO_DATE_TIME);
        
        if (dateTime == null)
            dateTime = tryParse(text, LONG_US_DATE_TIME);
        
        if (dateTime == null)
            throw new IllegalArgumentException("Unrecognized date/time syntax: " + text);
        
        return new YYYY_MM_DD(
                dateTime.getYear(),
                dateTime.getMonthValue(),
                dateTime.getDayOfMonth());
    }

    private static LocalDateTime tryParse(String text, DateTimeFormatter fmt)
    {
        try
        {
            return LocalDateTime.parse(text.trim(), fmt);
        }
        catch (DateTimeParseException ex)
        {
            return null;
        }
    }
}
