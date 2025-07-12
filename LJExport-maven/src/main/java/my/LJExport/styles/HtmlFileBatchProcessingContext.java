package my.LJExport.styles;

import java.util.concurrent.atomic.AtomicInteger;

public class HtmlFileBatchProcessingContext
{
    public final AtomicInteger scannedHtmlFiles = new AtomicInteger(0);
    public final AtomicInteger updatedHtmlFiles = new AtomicInteger(0);
    public final AtomicInteger savedHtmlFiles = new AtomicInteger(0);

    public void add(HtmlFileBatchProcessingContext x)
    {
        this.scannedHtmlFiles.addAndGet(x.scannedHtmlFiles.get());
        this.updatedHtmlFiles.addAndGet(x.updatedHtmlFiles.get());
        this.savedHtmlFiles.addAndGet(x.savedHtmlFiles.get());
    }
}
