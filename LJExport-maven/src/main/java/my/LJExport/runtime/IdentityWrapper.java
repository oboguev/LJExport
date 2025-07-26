package my.LJExport.runtime;

public final class IdentityWrapper<T>
{
    private final T obj;

    public IdentityWrapper(T obj)
    {
        this.obj = obj;
    }

    public T get()
    {
        return obj;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof IdentityWrapper<?> && ((IdentityWrapper<?>) other).obj == this.obj;
    }

    @Override
    public int hashCode()
    {
        return System.identityHashCode(obj);
    }
}
