package my.LJExport.maintenance.handlers;

import java.util.List;

import my.LJExport.maintenance.Maintenance;
import my.LJExport.runtime.Util;

public class CountFiles extends MaintenanceHandler
{
    private int totalFileCount = 0;

    public CountFiles() throws Exception
    {
    }

    @Override
    protected void beginUsers()
    {
        Util.out(">>> Counting all HTML files for all users");
    }

    @Override
    protected void endUsers()
    {
        Maintenance.TotalFileCount = totalFileCount;
        Util.out(">>>     Found " + totalFileCount + " files");
    }

    @Override
    protected void beginUser()
    {
    }

    @Override
    protected void endUser()
    {
    }

    @Override
    protected boolean isParallel()
    {
        return true;
    }

    @Override
    protected void printDivider()
    {
    }

    @Override
    protected boolean onEnumFiles(String which, List<String> enumeratedFiles)
    {
        totalFileCount += enumeratedFiles.size();
        return false;
    }
}
