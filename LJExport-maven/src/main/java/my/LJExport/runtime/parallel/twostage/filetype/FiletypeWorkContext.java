package my.LJExport.runtime.parallel.twostage.filetype;

import java.io.IOException;

import my.LJExport.runtime.parallel.twostage.WorkContext;

public class FiletypeWorkContext extends WorkContext<String>
{
    public String fullFilePath;
    public String contentExtension;
    public boolean empty;
    public long size;
    public boolean zeroes;

    public FiletypeWorkContext(String fullFilePath)
    {
        super(fullFilePath);
        this.fullFilePath = fullFilePath;
    }

    @Override
    public void close() throws IOException
    {
        // no-op
    }
}
