package my.LJExport.runtime;

import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ExclusiveFileAccess
{
    // private FileOutputStream fileOutputStream;
    private FileChannel channel;
    private FileLock lock;
    private BufferedWriter writer;

    @SuppressWarnings("unused")
    private Path filePath;

    @SuppressWarnings("unused")
    private boolean fileCreated;

    public ExclusiveFileAccess(String filePath) throws IOException
    {
        this(new File(filePath).getCanonicalFile().toPath());
    }

    private ExclusiveFileAccess(Path filePath) throws IOException
    {
        this.filePath = filePath;
        this.fileCreated = false;

        // Ensure parent directory exists
        Path parent = filePath.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        try
        {
            // Try to atomically create the file — only one process will succeed
            try
            {
                channel = FileChannel.open(filePath,
                        StandardOpenOption.CREATE_NEW, // fails if file exists
                        StandardOpenOption.WRITE,
                        StandardOpenOption.READ,
                        // StandardOpenOption.APPEND, // incompatible with READ
                        StandardOpenOption.DSYNC);
                fileCreated = true;
            }
            catch (FileAlreadyExistsException e)
            {
                // File already exists — open normally
                channel = FileChannel.open(filePath,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.READ,
                        // StandardOpenOption.APPEND,  // incompatible with READ
                        StandardOpenOption.DSYNC);
            }

            // Acquire exclusive lock on the file
            lock = channel.tryLock();
            if (lock == null)
                throw new IOException("File is locked by another process: " + filePath);
        }
        catch (IOException | RuntimeException e)
        {
            closeResources(); // cleanup on failure
            throw e;
        }
    }

    public synchronized String readExistingContent() throws IOException
    {
        if (writer != null)
            throw new IOException("Already started appending");

        // Read through the locked channel
        long fileSize = channel.size();
        if (fileSize == 0)
            return "";

        // Position to beginning of file
        channel.position(0);

        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        channel.read(buffer);
        buffer.flip();

        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    public synchronized List<String> readExistingLines() throws IOException
    {
        String content = readExistingContent();
        List<String> lines = new ArrayList<>();
        if (content.isEmpty())
            return lines;

        // Split by line endings while preserving empty lines
        String[] lineArray = content.split("\\r?\\n|\\r", -1);
        for (String line : lineArray)
            lines.add(line);
        return lines;
    }

    public synchronized void appendContent(String content) throws IOException
    {
        beginAppend();
        writer.write(content);
    }

    private void beginAppend() throws IOException
    {
        if (writer == null)
        {
            channel.position(channel.size()); // Seek to end for append
            OutputStream outputStream = Channels.newOutputStream(channel);
            writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        }
    }

    public synchronized void flush() throws IOException
    {
        if (writer != null)
            writer.flush();
    }

    public synchronized void close()
    {
        closeResources();
    }

    private void closeResources()
    {
        try
        {
            if (writer != null)
                writer.close();
        }
        catch (IOException e)
        {
            System.err.println("Error closing writer: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        try
        {
            if (lock != null && lock.isValid())
                lock.release();
        }
        catch (IOException e)
        {
            System.err.println("Error releasing lock: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        try
        {
            if (channel != null && channel.isOpen())
                channel.close();
        }
        catch (IOException e)
        {
            System.err.println("Error closing channel: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        writer = null;
        lock = null;
        channel = null;
        filePath = null;
        fileCreated = false;
    }

    /* ====================================================================================== */

    /*
     * Usage example
     */
    public static void example_main(String[] args)
    {
        Path file = Paths.get("example.txt");
        ExclusiveFileAccess efa = null;

        try
        {
            // 1. Try to lock the file (will throw exception immediately if can't)
            efa = new ExclusiveFileAccess(file);

            // 2. Read existing content through locked channel
            String existingContent = efa.readExistingContent();
            System.out.println("Existing content: " + existingContent);

            // Alternative: read as lines
            List<String> lines = efa.readExistingLines();
            System.out.println("Lines count: " + lines.size());

            // 3. Append new content
            efa.appendContent("\nNew line added at: " + System.currentTimeMillis());

            // Explicit flush (optional)
            efa.flush();

            System.out.println("File updated successfully");
        }
        catch (IOException e)
        {
            System.err.println("Error accessing file: " + e.getMessage());
        }
        finally
        {
            // 4. Flush, close and unlock
            if (efa != null)
            {
                efa.close();
            }
        }
    }
}