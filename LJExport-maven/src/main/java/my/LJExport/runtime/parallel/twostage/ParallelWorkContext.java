package my.LJExport.runtime.parallel.twostage;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Executes stage1 on a bounded number of parallel tasks while preserving the
 * original item order for stage2.  Implements {@link Iterable} so that the
 * caller can process the returned {@code WorkContext} instances in a standard
 * for‑each loop.  The class is {@link AutoCloseable}; invoking {@link #close()}
 * (or using try‑with‑resources) shuts down the pipeline and waits for all
 * pending tasks.
 *
 * <pre>{@code
 * try (ParallelWorkContext<String, MyCtx> pwc =
 *          new ParallelWorkContext<>(
 *                  workItems,
 *                  MyCtx::new,          // context factory
 *                  ctx -> heavyStage1(ctx),
 *                  16)) {               // 16 worker threads
 *     for (MyCtx ctx : pwc) {
 *         stage2(ctx);
 *     }
 * }
 * }</pre>
 */
public class ParallelWorkContext<I, WC extends WorkContext<I>>
        implements Iterable<WC>, AutoCloseable, Closeable
{
    private final List<I> items;
    private final Function<I, WC> contextFactory;
    private final Stage1Processor<WC> stage1;
    private final ExecutorService executor;
    private final boolean ownExecutor;
    private final int maxInFlight;

    private final Queue<Future<WC>> inFlight = new ArrayDeque<>();
    private int nextIndex = 0;
    private volatile boolean shutdownRequested = false;

    /**
     * Primary constructor.
     *
     * @param workItems      items to be processed in their original order
     * @param contextFactory factory creating a {@code WC} for each item
     * @param stage1         parallel stage implementation
     * @param executor       worker executor to use
     * @param maxInFlight    upper bound of concurrently scheduled items
     * @param ownExecutor    if {@code true}, {@link #close()} will shut it down
     */
    public ParallelWorkContext(List<I> workItems,
            Function<I, WC> contextFactory,
            Stage1Processor<WC> stage1,
            ExecutorService executor,
            int maxInFlight,
            boolean ownExecutor)
    {
        Objects.requireNonNull(workItems, "workItems");
        Objects.requireNonNull(contextFactory, "contextFactory");
        Objects.requireNonNull(stage1, "stage1");
        Objects.requireNonNull(executor, "executor");
        if (maxInFlight <= 0)
        {
            throw new IllegalArgumentException("maxInFlight must be > 0");
        }
        this.items = workItems;
        this.contextFactory = contextFactory;
        this.stage1 = stage1;
        this.executor = executor;
        this.maxInFlight = maxInFlight;
        this.ownExecutor = ownExecutor;
    }

    /**
     * Convenience constructor using an externally supplied executor.
     */
    public ParallelWorkContext(List<I> workItems,
            Function<I, WC> contextFactory,
            Stage1Processor<WC> stage1,
            ExecutorService executor,
            int maxInFlight)
    {
        this(workItems, contextFactory, stage1, executor, maxInFlight, false);
    }

    /**
     * Convenience constructor that creates its own fixed‑size thread pool.
     */
    public ParallelWorkContext(List<I> workItems,
            Function<I, WC> contextFactory,
            Stage1Processor<WC> stage1,
            int parallelism)
    {
        this(workItems,
                contextFactory,
                stage1,
                Executors.newFixedThreadPool(parallelism),
                parallelism,
                true);
    }
    
    /* =============================================================================================== */

    @Override
    public Iterator<WC> iterator()
    {
        return new Iterator<WC>()
        {
            @Override
            public boolean hasNext()
            {
                return nextIndex < items.size() || !inFlight.isEmpty();
            }

            @Override
            public WC next()
            {
                if (!hasNext())
                    throw new NoSuchElementException();
                
                try
                {
                    scheduleUntilFull();
                    Future<WC> head = inFlight.peek();

                    if (head == null)
                        throw new IllegalStateException("Internal queue empty despite hasNext()");
                    
                    WC ctx;
                    try
                    {
                        ctx = head.get();
                    }
                    catch (InterruptedException ie)
                    {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for stage1", ie);
                    }
                    catch (ExecutionException ee)
                    {
                        // Stage1 already stored the exception inside ctx, but
                        // if creation failed early we propagate the underlying cause.
                        Throwable cause = ee.getCause();
                        if (cause instanceof RuntimeException)
                        {
                            throw (RuntimeException) cause;
                        }
                        throw new RuntimeException(cause);
                    }
                    finally
                    {
                        inFlight.remove();
                    }

                    // Re‑fill the pipeline after removing one item
                    scheduleUntilFull();
                    return ctx;
                }
                catch (RuntimeException rte)
                {
                    throw rte;
                }
                catch (Exception ex)
                {
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    /** Schedules tasks until {@code inFlight.size() == maxInFlight} or no more items. */
    private void scheduleUntilFull()
    {
        while (!shutdownRequested && inFlight.size() < maxInFlight && nextIndex < items.size())
        {
            final I item = items.get(nextIndex++);
            final WC ctx = contextFactory.apply(item);
            ctx.setWorkItem(item);
            
            Future<WC> f = executor.submit(() ->
            {
                try
                {
                    stage1.process(ctx);
                }
                catch (Exception ex)
                {
                    ctx.setException(ex);
                }
                return ctx;
            });
            
            inFlight.add(f);
        }
    }

    /* =============================================================================================== */

    /**
     * Stops submitting new work items, waits for already scheduled tasks to
     * finish, and invokes {@link WorkContext#close()} on every finished context.
     */
    public void shutdown()
    {
        shutdownRequested = true;
        drainAndClose();
        if (ownExecutor)
        {
            executor.shutdown();
        }
    }

    private void drainAndClose()
    {
        while (!inFlight.isEmpty())
        {
            try
            {
                WC ctx = inFlight.remove().get();
                closeSafely(ctx);
            }
            catch (InterruptedException ie)
            {
                Thread.currentThread().interrupt();
                break;
            }
            catch (ExecutionException ee)
            {
                // Ignore – exception already stored in ctx (if created)
            }
        }
    }

    private static void closeSafely(WorkContext<?> ctx)
    {
        try
        {
            if (ctx != null)
            {
                ctx.close();
            }
        }
        catch (Exception ex)
        {
            // Log or re‑throw as needed – swallow here.
        }
    }

    @Override
    public void close()
    {
        shutdown();
    }
}
