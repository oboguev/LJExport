package my.LJExport.runtime.http;

import java.util.concurrent.locks.ReentrantLock;

import my.LJExport.Config;
import my.LJExport.Main;

public class RateLimiter
{
    public static final RateLimiter LJ_PAGES = new RateLimiter("LJ page loading", Config.RateLimit_LiveJournal_PageLoad)
            .setCoolOff(Config.RateLimit_LiveJournal_PageLoad_CoolOff_Requests, Config.RateLimit_LiveJournal_PageLoad_CoolOff_Interval);
    
    public static final RateLimiter LJ_IMAGES = new RateLimiter("LJ image loading", Config.RateLimit_LiveJournal_Images);

    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastReturnTime = System.currentTimeMillis();
    private final String name;
    private volatile long delay = 0;
    private volatile long requestCount = 0;

    private long cooloffRequestCount = 0;
    private long cooloffInterval = 0;

    private static final Boolean RandomizeDelay = true;

    public RateLimiter(String name, int ms)
    {
        this.name = name;
        this.delay = ms;
    }

    public RateLimiter setRateLimit(int ms)
    {
        delay = ms;
        return this;
    }

    public RateLimiter setCoolOff(long cooloffRequestCount, long cooloffInterval)
    {
        this.cooloffRequestCount = cooloffRequestCount;
        this.cooloffInterval = cooloffInterval;
        return this;
    }

    public void limitRate()
    {
        if (RandomizeDelay)
        {
            limitRate(randomizedDelay(delay));
        }
        else
        {
            limitRate(delay);
        }
    }
    
    private long randomizedDelay(long ms)
    {
        // Apply ±30% jitter to reduce bot-like constant-interval appearance 
        // for example, 1200ms delay will vary between ~840 and 1560 ms, which is much more human-like
        long jitter = (long) (ms * 0.3);
        long randomizedDelay = ms - jitter + (int) (Math.random() * jitter * 2);
        return randomizedDelay;
    }

    @SuppressWarnings("unused")
    private void limitRateMinMax(long msmin, long msmax)
    {
        long ms = delay;
        ms = Math.max(ms, msmin);
        ms = Math.min(ms, msmax);
        limitRate(ms);
    }

    private void limitRate(long ms)
    {
        boolean showCooloff = false;

        lock.lock();

        try
        {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastReturn = currentTime - lastReturnTime;

            requestCount++;
            
            if (cooloffRequestCount != 0 && (requestCount % cooloffRequestCount) == 0)
            {
                long actualCooloffInterval = randomizedDelay(cooloffInterval);
                ms += actualCooloffInterval;
                
                if (actualCooloffInterval > 30 * 1000)
                {
                    Main.out(String.format("Waiting for %s cool-off interval %d seconds ...", name, actualCooloffInterval / 1000));
                    showCooloff = true;
                }
            }

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
            if (showCooloff)
                Main.out(String.format("Resuming after %s cool-off interval", name));
            
            lock.unlock();
        }
    }
}