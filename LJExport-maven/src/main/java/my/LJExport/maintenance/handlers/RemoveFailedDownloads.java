package my.LJExport.maintenance.handlers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.LJExport.Config;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.links.LinkDownloader;

/*
 * For link files listed in failed-link-downloads.txt:
 *     - created by DetectFailedDownloads
 *     - left after MainRedownloadFailedLinks
 * i.e. files of wrong content, and with original content irrecoverable:
 *    - revert a.href and img.src pointing to the link file to original URL
 *    - remove file from link index map
 *    - delete actual (bad) link file
 *    - delete empty directories
 *    - delete failed-link-downloads.txt
 */
public class RemoveFailedDownloads extends MaintenanceHandler
{
    private static boolean DryRun = true;

    public RemoveFailedDownloads() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Removing failed downloads of linked files");
        super.beginUsers("Removing failed downloads of linked files");
        txLog.writeLine(String.format("Executing RemoveFailedDownloads in %s RUN mode", DryRun ? "DRY" : "WET"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private List<KVEntry> linkFileMap;
    private List<KVEntry> failedLinksFiles;
    private boolean noEndUser = false;

    private static final String FailedLinkDownloadsFileName = DetectFailedDownloads.FailedLinkDownloadsFileName;

    @Override
    protected void beginUser() throws Exception
    {
        KVFile kvfile = new KVFile(this.linksDirSep + FailedLinkDownloadsFileName);
        if (!kvfile.exists())
        {
            trace("No failed downloads for user " + Config.User);
            skipUser();
            noEndUser = true;
            return;
        }

        failedLinksFiles = kvfile.load(true);
        if (failedLinksFiles.size() == 0)
        {
            if (DryRun)
            {
                trace("Skipping empty " + kvfile.getPath());
            }
            else
            {
                trace("Removing empty " + kvfile.getPath());
                txLog.writeLine("Removing empty " + kvfile.getPath());
                kvfile.delete();
            }

            skipUser();
            noEndUser = true;
            return;
        }

        kvfile = new KVFile(this.linksDirSep + LinkDownloader.LinkMapFileName);
        linkFileMap = kvfile.load(true);
    }

    @Override
    protected void endUser() throws Exception
    {
        if (noEndUser)
        {
            super.endUser();
            return;
        }
        
        if (!DryRun)
        {
            // remove files from link index map
            if (removeFromLinkFileMap())
            {
                // update index map file
                KVFile kvfile = new KVFile(this.linksDirSep + LinkDownloader.LinkMapFileName);
                trace("Updating " + kvfile.getPath());
                txLog.writeLine("Updating " + kvfile.getPath());
                kvfile.save(linkFileMap);
                txLog.writeLine("Updated " + kvfile.getPath());
            }

            // delete actual (bad) link files
            for (KVEntry e : failedLinksFiles)
            {
                String abs = rel2abs(e.value);
                File fp = new File(abs).getCanonicalFile();
                if (fp.exists())
                {
                    trace("Deleting link file " + fp.getCanonicalPath());
                    Files.deleteIfExists(Paths.get(abs));
                }
            }

            // delete empty directories
            Map<String, String> dir_lc2ac = new HashMap<>();

            for (String fp : Util.enumerateDirectories(linksDir))
            {
                fp = linksDir + File.separator + fp;
                dir_lc2ac.put(fp.toLowerCase(), fp);
            }

            supertrace("  >>> Deleting empty directories for user " + Config.User);
            deleteEmptyFolders(dir_lc2ac.values());
            supertrace("  >>> Deleted empty directories for user " + Config.User);

            // delete failed-link-downloads.txt
            KVFile kvfile = new KVFile(this.linksDirSep + FailedLinkDownloadsFileName);
            trace("Removing emptied " + kvfile.getPath());
            txLog.writeLine("Removing emptied " + kvfile.getPath());
            kvfile.delete();
        }

        super.endUser();
    }
    
    private boolean removeFromLinkFileMap() throws Exception
    {
        boolean updated = false;
        
        Map<String, List<KVEntry>> lc2entry = KVFile.reverseMultiMap(linkFileMap, true);
        
        for (KVEntry e : failedLinksFiles)
        {
            List<KVEntry> xlist = lc2entry.get(e.key.toLowerCase());
            if (xlist != null)
            {
                for (KVEntry e2 : xlist)
                {
                    linkFileMap.remove(e2);
                    updated = true;
                }
            }
        }
        
        return updated;
    }
}
