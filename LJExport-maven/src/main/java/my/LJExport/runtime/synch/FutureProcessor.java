package my.LJExport.runtime.synch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * A generic processor for managing Callable tasks, submitting them to a shared ThreadPoolExecutor, and retrieving completed results
 * (blocking until one is available).
 *
 * @param <T>
 *            The result type returned by the Callable tasks
 */
public class FutureProcessor<T>
{
    private final ThreadPoolExecutor executor;
    private final List<Callable<T>> pendingTasks = new ArrayList<>();
    private final List<FutureTask<T>> submittedTasks = new ArrayList<>();

    /**
     * Constructs a FutureProcessor using a provided shared ThreadPoolExecutor.
     *
     * @param executor
     *            the executor used for task execution
     */
    public FutureProcessor(ThreadPoolExecutor executor)
    {
        this.executor = executor;
    }

    /**
     * Add a Callable<T> task to the internal pending list. These tasks will be submitted when start() is called.
     */
    public synchronized void add(Callable<T> task)
    {
        pendingTasks.add(task);
    }

    /**
     * Submit all pending Callable<T> tasks to the executor. Their FutureTasks are tracked in the submittedTasks list. The pending
     * list is cleared after submission.
     */
    public synchronized void start()
    {
        for (Callable<T> task : pendingTasks)
        {
            FutureTask<T> futureTask = new FutureTask<>(task);
            submittedTasks.add(futureTask);
            executor.execute(futureTask);
        }

        pendingTasks.clear();
    }

    /**
     * Attempt to cancel all submitted tasks that have not yet started execution. Tries to remove queued tasks from the executor and
     * cancels them. Clears both the pending and submitted task lists.
     */
    public synchronized void shutdown()
    {
        for (FutureTask<T> task : submittedTasks)
        {
            executor.remove(task); // no effect if already running
            task.cancel(false); // cancel if not started, do not interrupt
        }
        submittedTasks.clear();
        pendingTasks.clear();
        // Do NOT shut down the executor (may be shared)
    }

    /**
     * Wait (blocking) until at least one submitted task completes, then return it. Uses polling with 0.1-second sleep interval.
     *
     * @return the first completed Future<T>
     * @throws InterruptedException
     *             if the thread is interrupted while waiting
     */
    public Future<T> nextExpectedResult() throws InterruptedException
    {
        while (true)
        {
            synchronized (this)
            {
                for (int i = 0; i < submittedTasks.size(); i++)
                {
                    FutureTask<T> task = submittedTasks.get(i);
                    if (task.isDone())
                    {
                        submittedTasks.remove(i);
                        return task;
                    }
                }
            }

            Thread.sleep(100); // 0.1 seconds
        }
    }

    public static ThreadPoolExecutor createExecutor(int threadsPoolSize)
    {
        return new ThreadPoolExecutor(threadsPoolSize, threadsPoolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }
}
