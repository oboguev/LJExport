package my.LJExport.runtime;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ExclusiveFileAccess
{
    private FileOutputStream fileOutputStream;
    private FileChannel channel;
    private FileLock lock;
    private BufferedWriter writer;

    @SuppressWarnings("unused")
    private Path filePath;

    public ExclusiveFileAccess(String filePath) throws IOException
    {
        this(new File(filePath).getCanonicalFile().toPath());
    }

    public ExclusiveFileAccess(Path filePath) throws IOException
    {
        this.filePath = filePath;

        // Open the file channel with append mode
        fileOutputStream = new FileOutputStream(filePath.toFile().getCanonicalFile(), true);
        channel = fileOutputStream.getChannel();

        // Try to acquire an exclusive lock immediately (non-blocking)
        lock = channel.tryLock();
        if (lock == null)
        {
            closeResources();
            throw new IOException(String.format("File is locked by another process: %s", filePath.toString()));
        }

        // Create writer with UTF-8 encoding
        writer = new BufferedWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8));
    }

    public String readExistingContent() throws IOException
    {
        // Read through the locked channel
        long fileSize = channel.size();
        if (fileSize == 0)
        {
            return "";
        }

        // Position to beginning of file
        channel.position(0);

        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        channel.read(buffer);
        buffer.flip();

        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    public List<String> readExistingLines() throws IOException
    {
        String content = readExistingContent();
        List<String> lines = new ArrayList<>();
        if (content.isEmpty())
        {
            return lines;
        }

        // Split by line endings while preserving empty lines
        String[] lineArray = content.split("\\r?\\n|\\r", -1);
        for (String line : lineArray)
            lines.add(line);
        return lines;
    }

    public void appendContent(String content) throws IOException
    {
        writer.write(content);
    }

    public void flush() throws IOException
    {
        writer.flush();
    }

    public void close()
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
            if (channel != null)
                channel.close();
        }
        catch (IOException e)
        {
            System.err.println("Error closing channel: " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        try
        {
            if (fileOutputStream != null)
                fileOutputStream.close();
        }
        catch (IOException e)
        {
            System.err.println("Error closing file output stream: " + e.getLocalizedMessage());
            e.printStackTrace();
        }
        
        writer = null;
        lock = null;
        channel = null;
        fileOutputStream = null;
        filePath = null;
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