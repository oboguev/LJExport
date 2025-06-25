package my.LJExport.runtime;

import java.util.concurrent.locks.ReentrantLock;

import my.LJExport.Config;

public class RateLimiter
{
    public static final RateLimiter LJ_PAGES = new RateLimiter(Config.RateLimitLivejournalPageLoad);  
    public static final RateLimiter LJ_IMAGES = new RateLimiter(Config.RateLimitLivejournalImages);  
    
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastReturnTime = System.currentTimeMillis();
    
    private int delay = 0;
    
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
        limitRate(delay);
    }
    
    public void limitRateMinMax(int msmin, int msmax)
    {
        int ms = delay;
        ms = Math.max(ms, msmin);
        ms = Math.min(ms, msmax);
        limitRate(ms);
    }
    
    public void limitRate(int ms)
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