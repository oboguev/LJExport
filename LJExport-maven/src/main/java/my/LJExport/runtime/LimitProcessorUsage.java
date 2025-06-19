package my.LJExport.runtime;

import com.sun.jna.Native;

import com.sun.jna.Library;
// import com.sun.jna.Platform;
import com.sun.jna.Pointer;

public class LimitProcessorUsage
{
    public static void limit()
    {
        final int ncpus = Runtime.getRuntime().availableProcessors();
        final int maxcpus = maxcpus(ncpus);
        if (Util.isWindowsOS())
            limitWindowsOS(maxcpus);
    }

    private static int maxcpus(int ncpus)
    {
        if (ncpus <= 2)
        {
            return 1;
        }
        else if (ncpus <= 4)
        {
            return ncpus - 1;
        }
        else if (ncpus <= 8)
        {
            return ncpus - 2;
        }
        else if (ncpus <= 11)
        {
            return ncpus - 3;
        }
        else if (ncpus <= 20)
        {
            return ncpus - 4;
        }
        else
        {
            return ncpus - 8;
        }
    }

    public interface Kernel32 extends Library
    {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        Pointer GetCurrentProcess();

        boolean SetProcessAffinityMask(Pointer hProcess, long dwProcessAffinityMask);
    }

    private static void limitWindowsOS(int maxcpus)
    {
        long coreMask = affinityMask(maxcpus);
        Pointer process = Kernel32.INSTANCE.GetCurrentProcess();
        Kernel32.INSTANCE.SetProcessAffinityMask(process, coreMask);
    }

    private static long affinityMask(int numProcessors)
    {
        if (numProcessors < 1 || numProcessors > 64)
            throw new IllegalArgumentException("Number of processors must be between 1 and 64");
        
        return (1L << numProcessors) - 1;
    }
}
