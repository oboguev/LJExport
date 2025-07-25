package my.LJExport.runtime.synch;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.runtime.http.Web;

public class ThreadsControl
{
    public static final EventFlag workerThreadGoEventFlag = new EventFlag();
    public static final AtomicInteger activeWorkerThreadCount = new AtomicInteger(0);

    private static ThreadPoolExecutor executor;

    public static boolean useLinkDownloadThreads()
    {
        return activeWorkerThreadCount.get() <= Config.LinkDownloadSpawnThreshold;
    }

    public static synchronized ThreadPoolExecutor getExecutor(boolean required)
    {
        if (executor == null && required)
        {
            executor = new ThreadPoolExecutor(Config.LinkDownloadThreadsPoolSize, Config.LinkDownloadThreadsPoolSize,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    executorThreadFactory);
        }

        return executor;
    }

    public static synchronized void shutdownAfterUser()
    {
        if (executor != null)
        {
            // Prevent new tasks from being submitted and discard queued tasks
            executor.shutdownNow();

            try
            {
                // Wait only for currently executing tasks to complete
                while (!executor.awaitTermination(1, TimeUnit.SECONDS))
                {
                    if (executor.getActiveCount() == 0)
                        break;
                    // Else continue waiting
                }
            }
            catch (InterruptedException e)
            {
                // If current thread is interrupted, just preserve the interrupt status
                Thread.currentThread().interrupt();
            }

            executor = null;
        }
    }

    public static void backgroundStarting()
    {
        int priority = (Thread.NORM_PRIORITY + Thread.MIN_PRIORITY) / 2;
        if (priority == Thread.NORM_PRIORITY)
            priority = Thread.MIN_PRIORITY;
        Thread.currentThread().setPriority(priority);
    }

    public static void backgroundException(Exception ex)
    {
        boolean wasAborting = Main.isAborting();
        Main.setAborting();
        if (!wasAborting)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void backgroundFinally()
    {
        ThreadsControl.activeWorkerThreadCount.decrementAndGet();
        Web.threadExit();
    }

    /* ================================================================= */

    private static final ThreadFactory executorThreadFactory = new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable r)
        {
            Thread t = new Thread(r);

            t.setName("webload idle");

            int priority = (Thread.NORM_PRIORITY + Thread.MIN_PRIORITY) / 2;
            if (priority == Thread.NORM_PRIORITY)
                priority = Thread.MIN_PRIORITY;

            t.setPriority(priority);

            return t;
        }
    };
}
