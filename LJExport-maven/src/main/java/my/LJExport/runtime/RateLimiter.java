package my.LJExport.runtime;

import java.util.concurrent.locks.ReentrantLock;

public class RateLimiter
{
    private static final ReentrantLock lock = new ReentrantLock();
    private static volatile long lastReturnTime = System.currentTimeMillis();
    
    private static int delay = 0;
    
    public static void setRateLimit(int ms)
    {
        delay = ms;
    }

    public static void limitRate()
    {
        limitRate(delay);
    }
    
    public static void limitRateMinMax(int msmin, int msmax)
    {
        int ms = delay;
        ms = Math.max(ms, msmin);
        ms = Math.min(ms, msmax);
        limitRate(ms);
    }
    
    public static void limitRate(int ms)
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