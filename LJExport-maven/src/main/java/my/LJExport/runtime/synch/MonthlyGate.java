package my.LJExport.runtime.synch;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Limits concurrency to
 *   • ≤ 4  monthly jobs, and
 *   • ≤ 10 regular jobs once any monthly job is active or waiting.
 *
 * Usage in the worker loop
 * ---------------------------------------------------------------
 * boolean monthly = isMonthly(fn);               // your test
 * gate.acquire(monthly);
 * try {
 *     processFile(fn);                           // your work
 * } finally {
 *     gate.release(monthly);
 * }
 * ---------------------------------------------------------------
 */
public final class MonthlyGate
{
    private int MAX_MONTHLY = 4;
    private int MAX_REG_WITH_MONTHLY = 20;

    private final ReentrantLock lock = new ReentrantLock(/*fair=*/true);
    private final Condition changed = lock.newCondition();

    /** threads currently inside {@code processFile(..)} */
    private int activeMonthly = 0;
    private int activeRegular = 0;

    /** monthly threads waiting to get in – prevents starvation */
    private int waitingMonthly = 0;
    
    public MonthlyGate(int MAX_MONTHLY, int MAX_REG_WITH_MONTHLY)
    {
        this.MAX_MONTHLY = MAX_MONTHLY;
        this.MAX_REG_WITH_MONTHLY = MAX_REG_WITH_MONTHLY;
    }

    /**
     * Call immediately before you start processing a file.
     *
     * @param monthly {@code true} for a monthly file, {@code false} otherwise
     */
    public void acquire(boolean monthly) throws InterruptedException
    {
        lock.lock();
        
        String threadName = Thread.currentThread().getName();

        try
        {
            Thread.currentThread().setName(threadName + " waiting gate"); 

            if (monthly)
            {
                waitingMonthly++;
                
                try
                {
                    while (activeMonthly >= MAX_MONTHLY || activeRegular > MAX_REG_WITH_MONTHLY)
                        changed.await();
                    
                    activeMonthly++;
                }
                finally
                {
                    waitingMonthly--;
                }
            }
            else
            {
                /* 
                 * Regular file.
                 * If any monthly activity is present (active or waiting),
                 * we must respect the 20-thread limit for regular work.
                 */
                while ((activeMonthly > 0 || waitingMonthly > 0) && activeRegular >= MAX_REG_WITH_MONTHLY)
                    changed.await();
                
                activeRegular++;
            }
        }
        finally
        {
            lock.unlock();
            Thread.currentThread().setName(threadName); 
        }
    }

    /**
     * Call in a finally-block immediately after processing finishes.
     *
     * @param monthly {@code true} if the file you just finished was monthly
     */
    public void release(boolean monthly)
    {
        lock.lock();

        try
        {
            if (monthly)
            {
                activeMonthly--;
            }
            else
            {
                activeRegular--;
            }

            /* 
             * Wake everybody – conditions are cheap and wake-ups rare,
             * so we keep it simple.
             */
            changed.signalAll();
        }
        finally
        {
            lock.unlock();
        }
    }
}
