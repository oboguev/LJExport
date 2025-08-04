package my.LJExport.maintenance.styles;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

import my.LJExport.runtime.Util;

public class MergeStyleRepositories
{
    private final Path srcRoot;
    private final Path dstRoot;

    public MergeStyleRepositories(String src, String dst)
    {
        this.srcRoot = Paths.get(src).normalize().toAbsolutePath();
        this.dstRoot = Paths.get(dst).normalize().toAbsolutePath();
    }

    /**
     * Returns true if there are no conflicts between files in src and dst.
     * Prints an error message using Util.err(...) for each conflict found.
     */
    public boolean noConflicts() throws Exception
    {
        boolean noConflicts = true;

        try (var stream = Files.walk(srcRoot))
        {
            for (Path srcFile : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator)
            {
                Path relPath = srcRoot.relativize(srcFile);
                Path dstFile = dstRoot.resolve(relPath);

                if (Files.exists(dstFile))
                {
                    if (!filesEqual(srcFile, dstFile))
                    {
                        Util.err("Conflict: " + relPath);
                        noConflicts = false;
                    }
                }
            }
        }

        return noConflicts;
    }

    /**
     * Copies files from src to dst if they do not exist in dst.
     * Creates necessary subdirectories.
     */
    public boolean copyMissingFiles() throws IOException
    {
        try (var stream = Files.walk(srcRoot))
        {
            for (Path srcFile : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator)
            {
                Path relPath = srcRoot.relativize(srcFile);
                Path dstFile = dstRoot.resolve(relPath);

                if (!Files.exists(dstFile))
                {
                    Files.createDirectories(dstFile.getParent());
                    Files.copy(srcFile, dstFile);
                }
            }
        }

        return true;
    }

    private static boolean filesEqual(Path f1, Path f2) throws Exception
    {
        byte[] ba1 = Util.readFileAsByteArray(f1.toAbsolutePath().toString());
        byte[] ba2 = Util.readFileAsByteArray(f2.toAbsolutePath().toString());
        
        return Arrays.equals(ba1, ba2);
    }
}
