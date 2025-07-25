package my.LJExport.runtime;

public class ContentProvider
{
    private String filepath;
    private byte[] content;
    
    public ContentProvider(String filepath)
    {
        this.filepath = filepath;
    }

    public ContentProvider(byte[] content)
    {
        this.content = content;
    }
    
    public byte[] get() throws Exception
    {
        if (content == null)
            content = Util.readFileAsByteArray(filepath);
        return content;
    }
}
