package my.LJExport.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import my.LJExport.Main;

public class UrlDurationHistory
{
    private static List<HistoryEntry> history = new ArrayList<>();
    
    public static synchronized void onComplete(String url, long ms)
    {
        if (ms >= 2500)
        {
            history.add(new HistoryEntry(ms, url));
        }
    }
    
    private static synchronized void sort()
    {
        Collections.sort(history);
    }
    
    public static synchronized void display()
    {
        sort();
        
        Main.out("");
        Main.out("============================================================");
        Main.out("URLs by duration (ms):");
        Main.out("");
        for (HistoryEntry he : history)
        {
            Main.out(String.format("%6d %s", he.ms, he.url));
        }
    }

    public static class HistoryEntry implements Comparable<HistoryEntry>
    {
        public final long ms;
        public final String url;

        public HistoryEntry(long ms, String url)
        {
            this.ms = ms;
            this.url = url;
        }

        @Override
        public int compareTo(HistoryEntry o)
        {
            return (int) (this.ms - o.ms);
        }
        
    }
}
