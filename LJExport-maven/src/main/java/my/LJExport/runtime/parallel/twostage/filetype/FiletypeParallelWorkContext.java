package my.LJExport.runtime.parallel.twostage.filetype;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import my.LJExport.runtime.parallel.twostage.ParallelWorkContext;

public class FiletypeParallelWorkContext extends ParallelWorkContext<String, FiletypeWorkContext>
{
    private ThreadPoolExecutor executor;

    public FiletypeParallelWorkContext(List<String> workItems,
            FiletypeStage1Processor stage1,
            int parallelism)
    {
        super(workItems, FiletypeParallelWorkContext::createContext, stage1, null, parallelism);

        executor = new ThreadPoolExecutor(
                parallelism, // core pool size
                parallelism, // max pool size
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                executorThreadFactory);

        this.setExecutorService(executor);
    }

    public FiletypeParallelWorkContext(List<String> fullFilePaths, int parallelism)
    {
        this(fullFilePaths, new FiletypeStage1Processor(), parallelism);
    }

    private static FiletypeWorkContext createContext(String workItem)
    {
        return new FiletypeWorkContext(workItem);
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
