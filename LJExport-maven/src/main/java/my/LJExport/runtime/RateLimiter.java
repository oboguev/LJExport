package my.LJExport.runtime;

import java.util.concurrent.locks.ReentrantLock;

import my.LJExport.Config;

public class RateLimiter
{
    public static final RateLimiter LJ_PAGES = new RateLimiter(Config.RateLimit_Livejournal_PageLoad);
    public static final RateLimiter LJ_IMAGES = new RateLimiter(Config.RateLimit_Livejournal_Images);

    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastReturnTime = System.currentTimeMillis();

    private int delay = 0;
    
    private static Boolean RandomizeDelay = true; 

    public RateLimiter(int ms)
    {
        delay = ms;
    }

    public void setRateLimit(int ms)
    {
        delay = ms;
    }

    public void limitRate()
    {
        if (RandomizeDelay)
        {
            // Apply ±30% jitter to reduce bot-like constant-interval appearance 
            // for example, 1200ms delay will vary between ~840 and 1560 ms, which is much more human-like
            int jitter = (int) (delay * 0.3);
            int randomizedDelay = delay - jitter + (int) (Math.random() * jitter * 2);
            limitRate(randomizedDelay);
        }
        else
        {
            limitRate(delay);
        }
    }

    @SuppressWarnings("unused")
    private void limitRateMinMax(int msmin, int msmax)
    {
        int ms = delay;
        ms = Math.max(ms, msmin);
        ms = Math.min(ms, msmax);
        limitRate(ms);
    }

    private void limitRate(int ms)
    {
        lock.lock();

        try
        {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastReturn = currentTime - lastReturnTime;

            if (timeSinceLastReturn < ms)
            {
                try
                {
                    Thread.sleep(ms - timeSinceLastReturn);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            lastReturnTime = System.currentTimeMillis();
        }
        finally
        {
            lock.unlock();
        }
    }
}