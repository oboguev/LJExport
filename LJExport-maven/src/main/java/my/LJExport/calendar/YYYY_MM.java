package my.LJExport.calendar;

public class YYYY_MM implements Comparable<YYYY_MM>
{
    public int yyyy;
    public int mm;

    public YYYY_MM(int yyyy, int mm)
    {
        this.yyyy = yyyy;
        this.mm = mm;
    }

    @Override
    public int compareTo(YYYY_MM o)
    {
        if (o == null)
            throw new NullPointerException("Cannot compare to null");

        // Compare by year first
        int yearDiff = Integer.compare(this.yyyy, o.yyyy);
        if (yearDiff != 0)
            return yearDiff;

        // If years are equal, compare by month
        return Integer.compare(this.mm, o.mm);
    }
}
