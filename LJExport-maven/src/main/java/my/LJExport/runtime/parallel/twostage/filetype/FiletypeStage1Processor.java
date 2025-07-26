package my.LJExport.runtime.parallel.twostage.filetype;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.parallel.twostage.Stage1Processor;

public class FiletypeStage1Processor implements Stage1Processor<FiletypeWorkContext>
{
    public FiletypeStage1Processor()
    {
    }

    @Override
    public void process(FiletypeWorkContext ctx) throws Exception
    {
        String threadName = Thread.currentThread().getName();
        try
        {
            String fn = ctx.getWorkItem();
            Thread.currentThread().setName("analyzing " + fn);
            byte[] content = Util.readFileAsByteArray(fn);

            ctx.empty = content.length == 0;
            ctx.size = content.length;
            ctx.zeroes = true;
            for (int k = 0; k < content.length; k++)
            {
                if (content[k] != 0)
                {
                    ctx.zeroes = false;
                    break;
                }
            }

            String fnExt = LinkFilepath.getMediaFileExtension(fn);

            ctx.contentExtension = FileTypeDetector.fileExtensionFromActualFileContent(content, fnExt);
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }
    }
}
