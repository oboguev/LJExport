package my.LJExport.runtime;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MemoryMonitor
{

    private static final MemoryMXBean MEM_BEAN = ManagementFactory.getMemoryMXBean();
    private static final AtomicBoolean REPORTER_STARTED = new AtomicBoolean(false);

    private MemoryMonitor()
    {
        /* utility class – no instances */
    }

    // ------------------------------------------------------------------
    // Existing API
    // ------------------------------------------------------------------

    /** @return bytes of used heap space */
    public static long getUsedHeap()
    {
        return MEM_BEAN.getHeapMemoryUsage().getUsed();
    }

    /** @return maximum configured heap in bytes, or -1 if unbounded */
    public static long getMaxHeap()
    {
        return MEM_BEAN.getHeapMemoryUsage().getMax();
    }

    /** @return used-heap / max-heap in the interval [0.0 … 1.0] */
    public static double getUsedHeapFraction()
    {
        long max = getMaxHeap();
        return max <= 0 ? 0.0 : (double) getUsedHeap() / max;
    }

    // ------------------------------------------------------------------
    // New feature: thread-name reporter
    // ------------------------------------------------------------------

    /**
     * Starts a single daemon thread that renames itself every&nbsp;5 s to {@code "memory monitor: heap usage <NN>%"}.<br>
     * Safe to call multiple times – the reporter starts only once.
     */
    public static void startMonitor()
    {
        if (REPORTER_STARTED.getAndSet(true))
            return; // already running

        Thread reporter = new Thread(() ->
        {
            try
            {
                while (!Thread.currentThread().isInterrupted())
                {
                    int pct = (int) Math.round(getUsedHeapFraction() * 100);
                    Thread.currentThread().setName("memory monitor: heap usage " + pct + "%");
                    Thread.sleep(Duration.ofSeconds(5).toMillis());
                }
            }
            catch (InterruptedException ie)
            {
                Thread.currentThread().interrupt(); // preserve interrupt status
            }
        }, "memory monitor: initializing");

        reporter.setDaemon(true);
        reporter.start();
    }
}
