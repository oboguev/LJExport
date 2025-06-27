package my.LJExport.runtime.synch;

public class EventFlag
{
    private boolean isSet = false;
    private int generation = 0;

    /**
     * Clears the flag. Future waiters will block until the next set().
     */
    public synchronized void clear()
    {
        isSet = false;
    }

    /**
     * Sets the flag and wakes up all waiting threads.
     */
    public synchronized void set()
    {
        isSet = true;
        generation++;
        notifyAll();
    }

    /**
     * Waits until the flag is set. Returns immediately if already set. Otherwise blocks until a new set() is called.
     */
    public synchronized void waitFlag() throws InterruptedException
    {
        if (!isSet)
        {
            int observedGeneration = generation;
            while (generation == observedGeneration)
                wait();
        }
    }
}
