package my.LJExport.runtime.synch;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/*
 * Inter-process locking
 */
public class InterprocessLock
{
    private final File lockFile;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private FileLock lock;

    private int lockCount = 0; // reentrant lock depth counter

    public InterprocessLock(String filePath) throws Exception
    {
        this.lockFile = new File(filePath);

        // Ensure parent directories exist
        File parent = lockFile.getParentFile();
        if (parent != null && !parent.exists())
        {
            parent.mkdirs(); // May fail silently if it already exists
        }

        // Safely create the lock file if it doesn't exist
        try
        {
            lockFile.createNewFile(); // Will return false if it already exists
        }
        catch (IOException e)
        {
            // Ignore if file already exists
            if (!lockFile.exists())
            {
                throw e; // True failure
            }
        }

        // Open in read-write mode for locking
        this.raf = new RandomAccessFile(lockFile, "rw");
        this.channel = raf.getChannel();
    }

    // Acquires the exclusive lock. Supports reentrant locking.
    public synchronized void lockExclusive() throws Exception
    {
        if (lockCount == 0)
        {
            this.lock = channel.lock(); // blocks until exclusive lock acquired
        }
        lockCount++;
    }

    // Releases one level of the lock. Fully unlocks only when depth reaches zero.
    public synchronized void unlock() throws Exception
    {
        if (lockCount == 0)
        {
            throw new IllegalStateException("unlock() called without matching lock()");
        }

        lockCount--;

        if (lockCount == 0 && lock != null)
        {
            lock.release();
            lock = null;
        }
    }

    // Releases any remaining lock and closes the channel and file
    public synchronized void close() throws Exception
    {
        unlockCompletely();
        channel.close();
        raf.close(); // closes underlying file descriptor safely
    }

    // Helper method: forcibly releases all lock depth
    private void unlockCompletely() throws Exception
    {
        if (lockCount > 0 && lock != null)
        {
            lock.release();
            lock = null;
            lockCount = 0;
        }
    }
}
