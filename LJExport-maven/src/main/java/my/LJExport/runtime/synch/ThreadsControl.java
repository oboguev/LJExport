package my.LJExport.runtime.synch;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadsControl
{
    public static final EventFlag workerThreadGoEventFlag = new EventFlag();
    public static final AtomicInteger activeWorkerThreadCount = new AtomicInteger(0);

    private static final int SpawnLinkDownloadThreshold = 20;

    public static boolean useLinkDownloadThreads()
    {
        return activeWorkerThreadCount.get() <= SpawnLinkDownloadThreshold;
    }
}
