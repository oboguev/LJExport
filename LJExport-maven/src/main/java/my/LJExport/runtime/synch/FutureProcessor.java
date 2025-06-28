package my.LJExport.runtime.synch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
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
    private final List<Callable<T>> pendingTasks = new ArrayList<>();
    private final List<FutureTask<T>> submittedTasks = new ArrayList<>();

    /**
     * Constructs a FutureProcessor using a provided shared ThreadPoolExecutor.
     *
     * @param executor
     *            the executor used for task execution
     */
    public FutureProcessor()
    {
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
            ThreadsControl.getExecutor(true).execute(futureTask);
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
            ThreadsControl.getExecutor(true).remove(task); // no effect if already running
            task.cancel(false); // cancel if not started, do not interrupt
        }
        submittedTasks.clear();
        pendingTasks.clear();
        // do NOT shut down the executor (it may be shared)
    }

    /**
     * Wait (blocking) until at least one submitted task completes, then return it. Uses polling with 0.1-second sleep interval.
     *
     * @return the first completed T, until it returns null
     * @throws InterruptedException
     *             if the thread is interrupted while waiting
     */
    public T nextExpectedResult() throws Exception
    {
        for (;;)
        {
            synchronized (this)
            {
                if (submittedTasks.isEmpty())
                    return null;

                for (int i = 0; i < submittedTasks.size(); i++)
                {
                    FutureTask<T> task = submittedTasks.get(i);
                    if (task.isDone())
                    {
                        submittedTasks.remove(i);
                        return task.get();
                    }
                }
            }

            Thread.sleep(100); // 0.1 seconds
        }
    }

    // Wrapper to make it usable in enhanced for-loop
    public Iterable<T> expectedResults()
    {
        return () -> new Iterator<T>()
        {
            private T next;
            private boolean finished = false;
            private boolean fetched = false;

            private void fetchNext()
            {
                if (fetched || finished)
                    return;
                
                try
                {
                    next = nextExpectedResult();
                    if (next == null)
                        finished = true;
                }
                catch (Exception e)
                {
                    throw new RuntimeException("Error fetching next result", e);
                }
                fetched = true;
            }

            @Override
            public boolean hasNext()
            {
                fetchNext();
                return !finished;
            }

            @Override
            public T next()
            {
                if (!hasNext())
                    throw new NoSuchElementException();
                fetched = false;
                return next;
            }
        };
    }
}
