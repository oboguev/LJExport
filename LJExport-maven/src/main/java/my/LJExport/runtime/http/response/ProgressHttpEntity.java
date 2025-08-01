package my.LJExport.runtime.http.response;

import java.io.IOException;
import java.io.InputStream;
import org.apache.http.HttpEntity;

public class ProgressHttpEntity extends HttpEntityWrapper
{
    private long totalBytes;
    private long bytesRead = 0;

    public ProgressHttpEntity(HttpEntity entity, long totalBytes)
    {
        super(entity);
        this.totalBytes = totalBytes;
    }

    @Override
    public InputStream getContent() throws IOException
    {
        InputStream in = wrappedEntity.getContent();
        return new ProgressInputStream(in);
    }

    private class ProgressInputStream extends InputStream
    {
        private InputStream wrappedInputStream;
        private String threadNameBase = null;

        public ProgressInputStream(InputStream wrappedInputStream)
        {
            this.threadNameBase = Thread.currentThread().getName();
            this.wrappedInputStream = wrappedInputStream;
        }

        @Override
        public int read() throws IOException
        {
            int byteRead = wrappedInputStream.read();
            if (byteRead != -1)
            {
                bytesRead++;
                displayProgress();
            }
            return byteRead;
        }

        @Override
        public int read(byte[] b) throws IOException
        {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            int bytesReadThisTime = wrappedInputStream.read(b, off, len);
            if (bytesReadThisTime > 0)
            {
                bytesRead += bytesReadThisTime;
                displayProgress();
            }
            return bytesReadThisTime;
        }

        @Override
        public void close() throws IOException
        {
            Thread.currentThread().setName(this.threadNameBase);
            wrappedInputStream.close();
        }

        private void displayProgress()
        {
            if (this.threadNameBase != null)
            {
                StringBuilder sb = new StringBuilder(this.threadNameBase + " ");
                sb.append(String.format(" downloaded %,d bytes", bytesRead));
                if (totalBytes != -1)
                {
                    int progress = (int) ((bytesRead * 100) / totalBytes);
                    sb.append(" (" + progress + "%)");
                }
                Thread.currentThread().setName(sb.toString());
            }
        }
    }
}
