package my.LJExport.runtime.http;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import my.LJExport.runtime.Util;

public class CooloffMode
{
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition resumeCondition = lock.newCondition();
    private volatile long cooloffUntil = 0;

    private final long durationMillis;

    public CooloffMode(long durationMillis)
    {
        this.durationMillis = durationMillis;
    }

    // Call this BEFORE a request: will block if cool-off is active
    public void waitIfCoolingOff()
    {
        lock.lock();
        try
        {
            long now;
            while ((now = System.currentTimeMillis()) < cooloffUntil)
            {
                try
                {
                    long waitTime = cooloffUntil - now;
                    resumeCondition.awaitNanos(waitTime * 1_000_000);
                }
                catch (InterruptedException ignored)
                {
                }
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    // Call this AFTER detecting 429: will activate if not already cooling
    public void signalStart()
    {
        lock.lock();
        try
        {
            long now = System.currentTimeMillis();
            if (now < cooloffUntil)
                return; // already cooling

            cooloffUntil = now + durationMillis;
            Util.out("Archive.org cool-off mode activated for " + (durationMillis / 1000) + " sec");

            // Schedule resume message + notification
            new Thread(() ->
            {
                try
                {
                    Thread.sleep(durationMillis);
                    lock.lock();
                    try
                    {
                        Util.out("Archive.org cool-off mode completed");
                        resumeCondition.signalAll();
                    }
                    finally
                    {
                        lock.unlock();
                    }
                }
                catch (InterruptedException ignored)
                {
                }
            }, "CooloffTimer").start();

        }
        finally
        {
            lock.unlock();
        }
    }
}
