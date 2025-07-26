package my.LJExport.runtime.http;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import my.LJExport.Main;
import my.LJExport.runtime.Util;


public class CooloffMode
{
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition resumeCondition = lock.newCondition();

    private final long durationMillis;
    private volatile long cooloffUntil = 0;
    private volatile boolean timerRunning = false;

    public CooloffMode(long durationMillis)
    {
        this.durationMillis = durationMillis;
    }

    public void waitIfCoolingOff()
    {
        if (Main.isAborting())
            return;

        lock.lock();
        try
        {
            while (true)
            {
                long now = System.currentTimeMillis();
                if (Main.isAborting())
                    return;
                if (now >= cooloffUntil)
                    return;

                long waitTime = cooloffUntil - now;
                try
                {
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

    public void signalStart()
    {
        long now = System.currentTimeMillis();
        boolean wasCooling;

        lock.lock();
        try
        {
            wasCooling = (now < cooloffUntil);
            cooloffUntil = now + durationMillis;

            if (!wasCooling && !timerRunning)
            {
                Util.out("Archive.org cool-off mode activated for " + (durationMillis / 1000) + " sec");
                timerRunning = true;

                new Thread(() ->
                {
                    while (true)
                    {
                        try
                        {
                            long sleepMillis;
                            lock.lock();
                            try
                            {
                                long nowInner = System.currentTimeMillis();
                                if (Main.isAborting())
                                    return;

                                sleepMillis = cooloffUntil - nowInner;
                                if (sleepMillis <= 0)
                                {
                                    timerRunning = false;
                                    Util.out("Archive.org cool-off mode completed");
                                    resumeCondition.signalAll();
                                    return;
                                }
                            }
                            finally
                            {
                                lock.unlock();
                            }
                            Thread.sleep(sleepMillis);
                        }
                        catch (InterruptedException ignored)
                        {
                        }
                    }
                }, "CooloffTimer").start();
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    public void cancelCoolingOff()
    {
        lock.lock();
        try
        {
            cooloffUntil = 0;
            timerRunning = false;
            resumeCondition.signalAll();
        }
        finally
        {
            lock.unlock();
        }
    }
}
