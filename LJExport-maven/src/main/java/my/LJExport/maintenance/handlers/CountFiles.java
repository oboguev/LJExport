package my.LJExport.maintenance.handlers;

import java.util.List;

import my.LJExport.maintenance.Maintenance;
import my.LJExport.runtime.Util;

public class CountFiles extends MaintenanceHandler 
{
    private int totalFileCount = 0;
    
    @Override
    protected void beginUsers()
    {
        Util.out(">>> Counting all files for all users");
    }

    @Override
    protected void endUsers()
    {
        Maintenance.TotalFileCount = totalFileCount;
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
