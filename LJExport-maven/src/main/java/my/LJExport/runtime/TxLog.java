package my.LJExport.runtime;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Transaction log that persists each write to disk immediately so the file survives an OS crash or sudden power loss.
 *
 * <p>
 * The file is opened for <strong>exclusive</strong> append-only access. If another process already holds the lock an
 * {@link IOException} is thrown immediately.
 * </p>
 *
 * <p>
 * All mutating operations finish with {@code channel.force(true)}, forcing both data and metadata to stable storage.
 * </p>
 *
 * <p>
 * The class is thread-safe inside a single JVM instance.
 * </p>
 */
public final class TxLog implements Closeable
{
    private final Path path;
    private FileChannel channel;
    private FileLock lock;
    private BufferedWriter writer;

    /**
     * Opens an existing log file or atomically creates a new one.
     *
     * @param txLogFilePath
     *            fully-qualified path to the log file
     * @throws IOException
     *             on I/O error or if the file is locked by another process
     * @throws NullPointerException
     *             if {@code txLogFilePath} is {@code null}
     */
    public TxLog(String txLogFilePath) throws Exception
    {
        Objects.requireNonNull(txLogFilePath, "txLogFilePath");

        this.path = Paths.get(txLogFilePath).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent != null)
            Files.createDirectories(parent);

    }

    public void open() throws Exception
    {
        if (channel != null || lock != null || writer != null)
            throw new Exception("Alredy opened");
        
        try
        {
            /*
             * Open the file in append mode, creating it if absent.  We use DSYNC
             * (or SYNC on some file systems) so the kernel schedules synchronous
             * metadata and data updates.  This does not guarantee durability until
             * {@code force(true)} completes, which we invoke after every change.
             */
            Set<StandardOpenOption> opts = EnumSet.of(
                    StandardOpenOption.CREATE, // create if missing
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.DSYNC // request synchronous updates
            );
            this.channel = FileChannel.open(path, opts);

            // Fail fast if another process already owns the lock.
            this.lock = channel.tryLock();
            if (lock == null)
            {
                channel.close();
                throw new IOException("TxLog file is already locked by another process: " + path);
            }

            // Text writer layered on the same channel; autoFlush handled manually.
            this.writer = new BufferedWriter(Channels.newWriter(channel, StandardCharsets.UTF_8.newEncoder(), 8 * 1024));
        }
        catch (Exception ex)
        {
            close();
            throw ex;
        }
    }

    /**
     * Appends text to the log and forces it to disk.
     */
    public synchronized void writeText(String s) throws IOException
    {
        Objects.requireNonNull(s, "s");

        writer.write(s);
        writer.flush(); // flush Java buffers
        channel.force(true); // flush OS buffers + metadata
    }

    /**
     * Appends a line (string + platform line separator) to the log and forces it to disk.
     */
    public synchronized void writeLine(String s) throws IOException
    {
        writeText(s + System.lineSeparator());
    }

    /**
     * Truncates the log to zero bytes and forces the change to disk.
     */
    public synchronized void clear() throws IOException
    {
        writer.flush();
        channel.truncate(0);
        channel.force(true);
    }

    /**
     * @return {@code true} if the log is currently empty
     * @throws IOException
     *             on I/O error
     */
    public synchronized boolean isEmpty() throws IOException
    {
        return channel.size() == 0;
    }

    /**
     * Flushes any unwritten data, releases the file lock, and closes the channel.
     */
    @Override
    public synchronized void close() throws IOException
    {
        try
        {
            if (writer != null)
                writer.flush();

            if (channel != null)
                channel.force(true);
        }
        finally
        {
            try
            {
                if (lock != null && lock.isValid())
                {
                    lock.release();
                    lock = null;
                }
            }
            finally
            {
                if (writer != null)
                {
                    Util.safeClose(writer);
                    writer = null;
                }

                if (channel != null)
                {
                    channel.close();
                    channel = null;
                }
            }
        }
    }

    /** Convenience accessor for the underlying path. */
    public Path getPath()
    {
        return path;
    }
}
