package my.LJExport.runtime;

import java.util.ArrayList;
import java.util.List;

public class ActivityCounters
{
    public static class ActivityEntry
    {
        public ActivityEntry(String kind)
        {
            this.ts = System.currentTimeMillis();
            this.kind = kind;
        }

        public long ts;
        public String kind;
    }

    private static List<ActivityEntry> list = new ArrayList<>();

    public static synchronized void reset()
    {
        list.clear();
    }

    public static synchronized void startedWebRequest()
    {
        list.add(new ActivityEntry("web-request"));
    }

    public static synchronized void startedLJPageWebRequest()
    {
        list.add(new ActivityEntry("lj-page-request"));
    }

    public static synchronized void loadedPage()
    {
        list.add(new ActivityEntry("loaded-page"));
    }

    private static final long SUMMARY_PERIOD = 5 * 60 * 1000;

    public static synchronized String summary(Integer remainingPages)
    {
        while (list.size() != 0 && list.get(0).ts < System.currentTimeMillis() - SUMMARY_PERIOD)
            list.remove(0);

        double all_web_requests = 0;
        double ljpage_requests = 0;
        double loaded_pages = 0;
        long ts0 = 0;

        for (ActivityEntry entry : list)
        {
            if (ts0 == 0)
                ts0 = entry.ts;

            switch (entry.kind)
            {
            case "web-request":
                all_web_requests++;
                break;

            case "lj-page-request":
                ljpage_requests++;
                break;

            case "loaded-page":
                loaded_pages++;
                break;
            }
        }

        long ms = System.currentTimeMillis() - ts0;

        if (ms == 0)
        {
            all_web_requests = 0;
            loaded_pages = 0;
            ms = 1;
        }

        String eta = "";

        double all_web_requests_per_min = 60 * 1000 * all_web_requests / ms;
        double ljpage_requests_per_min = 60 * 1000 * ljpage_requests / ms;
        double loaded_pages_per_min = 60 * 1000 * loaded_pages / ms;

        if (loaded_pages_per_min >= 5 && remainingPages != null && remainingPages > 0)
        {
            double remainingMinutes = remainingPages / loaded_pages_per_min;
            eta = String.format(", ETA: %d min", (int) remainingMinutes );
        }

        return String.format("over last 5 mins: all web requests: %.2f/min, LJ page requests: %.2f/min, loaded pages: %.2f/min%s",
                all_web_requests_per_min,
                ljpage_requests_per_min,
                loaded_pages_per_min,
                eta);
    }
}
