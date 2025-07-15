package my.LJExport.runtime.parallel.twostage;

import java.io.Closeable;
import java.io.IOException;

/**
 * Generic context passed between stage1 (parallel) and stage2 (sequential).
 * May be subclassed to attach additional mutable state.
 */
public class WorkContext<I> implements AutoCloseable, Closeable
{
    private I workItem;
    private volatile Exception exception;

    public WorkContext()
    {
    }

    public WorkContext(I workItem)
    {
        this.workItem = workItem;
    }

    public I getWorkItem()
    {
        return workItem;
    }

    public void setWorkItem(I workItem)
    {
        this.workItem = workItem;
    }

    public Exception getException()
    {
        return exception;
    }

    public void setException(Exception exception)
    {
        this.exception = exception;
    }

    /**
     * Override in subclasses to release resources associated with this context.
     */
    @Override
    public void close() throws IOException
    {
        // default implementation does nothing
    }
}