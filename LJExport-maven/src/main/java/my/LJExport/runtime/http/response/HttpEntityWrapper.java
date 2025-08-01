package my.LJExport.runtime.http.response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

// Wrapper class for HttpEntity
public class HttpEntityWrapper implements HttpEntity
{
    protected final HttpEntity wrappedEntity;

    public HttpEntityWrapper(HttpEntity entity)
    {
        this.wrappedEntity = entity;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void consumeContent() throws IOException
    {
        wrappedEntity.consumeContent();
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException
    {
        return wrappedEntity.getContent();
    }

    @Override
    public Header getContentEncoding()
    {
        return wrappedEntity.getContentEncoding();
    }

    @Override
    public long getContentLength()
    {
        return wrappedEntity.getContentLength();
    }

    @Override
    public boolean isChunked()
    {
        return wrappedEntity.isChunked();
    }

    @Override
    public boolean isRepeatable()
    {
        return wrappedEntity.isRepeatable();
    }

    @Override
    public boolean isStreaming()
    {
        return wrappedEntity.isStreaming();
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException
    {
        wrappedEntity.writeTo(outstream);
    }

    @Override
    public Header getContentType()
    {
        return wrappedEntity.getContentType();
    }
}
