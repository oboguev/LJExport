package my.LJExport.runtime.parallel.twostage.parser;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import my.LJExport.runtime.parallel.twostage.ParallelWorkContext;

public class ParserParallelWorkContext extends ParallelWorkContext<String, ParserWorkContext>
{
    private ThreadPoolExecutor executor;

    public ParserParallelWorkContext(List<String> workItems,
            ParserStage1Processor stage1,
            int parallelism)
    {
        super(workItems, ParserParallelWorkContext::createContext, stage1, null, parallelism);

        executor = new ThreadPoolExecutor(
                parallelism, // core pool size
                parallelism, // max pool size
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                executorThreadFactory);

        this.setExecutorService(executor);
    }

    public ParserParallelWorkContext(List<String> relativeFilePaths, String rootDir, int parallelism)
    {
        this(relativeFilePaths, new ParserStage1Processor(rootDir), parallelism);
    }

    private static ParserWorkContext createContext(String workItem)
    {
        return new ParserWorkContext(workItem);
    }

    public void shutdown()
    {
        super.shutdown();

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

    /* ================================================================= */

    private static final ThreadFactory executorThreadFactory = new ThreadFactory()
    {
        @Override
        public Thread newThread(Runnable r)
        {
            Thread t = new Thread(r);

            t.setName("parser idle");

            int priority = (Thread.NORM_PRIORITY + Thread.MIN_PRIORITY) / 2;
            if (priority == Thread.NORM_PRIORITY)
                priority = Thread.MIN_PRIORITY;

            t.setPriority(priority);

            return t;
        }
    };
}
