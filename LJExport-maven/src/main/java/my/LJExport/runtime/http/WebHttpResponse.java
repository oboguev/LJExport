package my.LJExport.runtime.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.IntPredicate;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.brotli.dec.BrotliInputStream;

import io.airlift.compress.zstd.ZstdInputStream;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.response.ProgressHttpEntity;

public class WebHttpResponse implements Closeable, AutoCloseable
{
    private CloseableHttpResponse cresp;

    WebHttpResponse(CloseableHttpResponse cresp)
    {
        this.cresp = cresp;
    }

    @Override
    public void close() throws IOException
    {
        if (cresp != null)
        {
            cresp.close();
            cresp = null;
        }
    }

    public Header[] getAllHeaders()
    {
        if (cresp != null)
            return cresp.getAllHeaders();
        else
            throw new RuntimeException("No wrapped response");
    }

    public Header getFirstHeader(String name)
    {
        if (cresp != null)
            return cresp.getFirstHeader(name);
        else
            throw new RuntimeException("No wrapped response");
    }

    public boolean containsHeader(String name)
    {
        if (cresp != null)
            return cresp.containsHeader(name);
        else
            throw new RuntimeException("No wrapped response");
    }

    public int getStatusCode()
    {
        if (cresp != null)
            return cresp.getStatusLine().getStatusCode();
        else
            throw new RuntimeException("No wrapped response");
    }

    public String getStatusReasonPhrase()
    {
        if (cresp != null)
            return cresp.getStatusLine().getReasonPhrase();
        else
            throw new RuntimeException("No wrapped response");
    }

    public byte[] getBinaryBody(IntPredicate shouldLoadBody, boolean progress) throws Exception
    {
        if (cresp != null)
            return crespBinaryBody(shouldLoadBody, progress);
        else
            throw new RuntimeException("No wrapped response");
        
    }
    
    private byte[] crespBinaryBody(IntPredicate shouldLoadBody, boolean progress) throws Exception
    {
        if (shouldLoadBody == null || shouldLoadBody.test(getStatusCode()))
        {
            HttpEntity entity = cresp.getEntity();

            if (entity != null)
            {
                InputStream entityStream = null;

                try
                {
                    if (progress)
                    {
                        long totalBytes = entity.getContentLength();
                        ProgressHttpEntity progressEntity = new ProgressHttpEntity(entity, totalBytes);
                        entityStream = progressEntity.getContent();
                        byte[] binaryBody = toByteArray(entityStream);
                        binaryBody = decompress(binaryBody);
                        return binaryBody;
                    }
                    else
                    {
                        entityStream = entity.getContent();
                        byte[] binaryBody = toByteArray(entityStream);
                        binaryBody = decompress(binaryBody);
                        return binaryBody;
                    }
                }
                finally
                {
                    if (entityStream != null)
                        entityStream.close();
                }
            }
        }
        
        return null;
    }
    
    public String getHeaderValue(String name)
    {
        Header[] headers = getAllHeaders();

        if (headers != null)
        {
            for (Header h : headers)
            {
                if (h.getName().equalsIgnoreCase(name))
                    return h.getValue();
            }
        }

        return null;
    }

    /* ============================================================================= */

    private static byte[] toByteArray(InputStream input) throws IOException
    {
        if (input == null)
            throw new IllegalArgumentException("Input stream must not be null");

        if (Util.False)
        {
            return IOUtils.toByteArray(input);

        }
        else
        {
            final int BUFFER_SIZE = 64 * 1024; // 64KB buffer

            try (ByteArrayOutputStream output = new ByteArrayOutputStream(2 * BUFFER_SIZE))
            {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1)
                    output.write(buffer, 0, bytesRead);

                return output.toByteArray();
            }
        }
    }

    /* ============================================================================= */

    public byte[] decompress(byte[] binaryBody) throws Exception
    {
        if (binaryBody == null || binaryBody.length == 0)
            return binaryBody;

        String encoding = getHeaderValue("Content-Encoding");
        if (encoding == null)
            return binaryBody;

        encoding = encoding.trim().toLowerCase();

        InputStream decodedStream = null;
        switch (encoding)
        {
        case "br":
            decodedStream = new BrotliInputStream(new ByteArrayInputStream(binaryBody));
            break;

        case "zstd":
            decodedStream = new ZstdInputStream(new ByteArrayInputStream(binaryBody));
            break;

        case "gzip":
        case "deflate":
            // gzip and deflate are already handled by Apache, so ignore them
            // Apache HttpClient v4 auto-decompresses gzip/deflate unless configured otherwise
            return binaryBody;

        default:
            throw new Exception("Unsupported compression method/header " + encoding);
        }

        try (InputStream in = decodedStream)
        {
            binaryBody = readAllBytes(in);
        }

        // Optionally clear encoding to reflect that body is now decoded
        // You can also leave it as-is if you want to preserve raw headers
        // r.removeHeader("Content-Encoding");
        
        return binaryBody;
    }

    private static byte[] readAllBytes(InputStream in) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buf = new byte[8192];
        int len;

        while ((len = in.read(buf)) != -1)
            out.write(buf, 0, len);

        return out.toByteArray();
    }
}