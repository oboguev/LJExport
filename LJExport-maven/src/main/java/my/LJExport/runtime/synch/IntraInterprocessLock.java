package my.LJExport.runtime.synch;

import java.util.concurrent.locks.ReentrantLock;

/*
 * Combined intra- and inter-process locking
 */
public class IntraInterprocessLock
{
    private final InterprocessLock interprocessLock;
    private final ReentrantLock localLock;

    public IntraInterprocessLock(String filePath) throws Exception
    {
        this.interprocessLock = new InterprocessLock(filePath);
        this.localLock = new ReentrantLock(true); // fair lock
    }

    public void lockExclusive() throws Exception
    {
        localLock.lock(); // acquire intra-process (thread-level) lock first
        try
        {
            interprocessLock.lockExclusive(); // acquire inter-process lock
        }
        catch (Exception e)
        {
            localLock.unlock(); // clean up local lock on failure
            throw e;
        }
    }

    public void unlock() throws Exception
    {
        if (!localLock.isHeldByCurrentThread())
            throw new IllegalStateException("Current thread does not hold the lock");

        interprocessLock.unlock(); // release inter-process lock
        localLock.unlock(); // release intra-process lock
    }

    public synchronized void close() throws Exception
    {
        interprocessLock.close(); // release any inter-process resources
    }
}
