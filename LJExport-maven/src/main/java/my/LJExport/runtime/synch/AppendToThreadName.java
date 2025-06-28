package my.LJExport.runtime.synch;

public class AppendToThreadName implements AutoCloseable
{
    private final String threadName;

    public AppendToThreadName(String extra)
    {
        threadName = Thread.currentThread().getName();
        Thread.currentThread().setName(threadName + extra);
    }

    @Override
    public void close() throws Exception
    {
        Thread.currentThread().setName(threadName);
    }
}
