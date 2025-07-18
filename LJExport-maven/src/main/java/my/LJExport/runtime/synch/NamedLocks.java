package my.LJExport.runtime.synch;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class NamedLocks
{
    @FunctionalInterface
    public interface CheckedRunnable
    {
        void run() throws Exception;
    }

    private static class RefCountedLock
    {
        final ReentrantLock lock = new ReentrantLock();
        int refCount = 0;
    }

    private final ConcurrentHashMap<String, RefCountedLock> lockMap = new ConcurrentHashMap<>();

    public void interlock(String name, CheckedRunnable lambda) throws Exception
    {
        RefCountedLock refLock = lockMap.compute(name, (key, existing) ->
        {
            if (existing == null)
                existing = new RefCountedLock();
            existing.refCount++;
            return existing;
        });

        try
        {
            refLock.lock.lock();
            lambda.run();
        }
        finally
        {
            refLock.lock.unlock();
            lockMap.compute(name, (key, existing) ->
            {
                if (existing == null)
                    return null;
                existing.refCount--;
                return existing.refCount == 0 ? null : existing;
            });
        }
    }
}
