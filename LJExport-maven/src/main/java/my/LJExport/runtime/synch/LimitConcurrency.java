package my.LJExport.runtime.synch;

import java.util.concurrent.Semaphore;

/**
 * Limits the number of threads that may execute a guarded section concurrently.
 *
 * <pre>{@code
 * LimitConcurrency limiter = new LimitConcurrency(3);
 *
 * void doWork() throws InterruptedException
 * {
 *     limiter.enter();
 *     try
 *     {
 *         // critical section – at most 3 threads here at once
 *     }
 *     finally
 *     {
 *         limiter.leave();
 *     }
 * }
 * }</pre>
 *
 * <p>
 * The implementation is just a thin wrapper over {@link Semaphore} so you get well-tested fairness and interruption behaviour “for
 * free.”
 * </p>
 */
public final class LimitConcurrency
{
    /** Semaphore whose permits correspond to available “slots.” */
    private final Semaphore semaphore;

    /**
     * @param maxThreads
     *            maximum number of threads allowed simultaneously
     * @throws IllegalArgumentException
     *             if {@code maxThreads &lt;= 0}
     */
    public LimitConcurrency(int maxThreads)
    {
        if (maxThreads <= 0)
            throw new IllegalArgumentException("maxThreads must be > 0");

        // fair semaphore prevents barging starvation
        this.semaphore = new Semaphore(maxThreads, /*fair=*/true);
    }

    /**
     * Blocks until a slot is free, then enters the critical section.
     *
     * @throws InterruptedException
     *             if the current thread is interrupted while waiting
     */
    public void enter() throws InterruptedException
    {
        semaphore.acquire(); // may throw InterruptedException
    }

    /**
     * Leaves the critical section, releasing one slot.
     *
     * <p>
     * If you call this more times than {@link #enter()} from a given thread, the semaphore’s permit count will exceed the intended
     * limit, so pair the calls carefully (prefer a {@code try/finally} pattern).
     * </p>
     */
    public void leave()
    {
        semaphore.release();
    }
}
