package my.LJExport.maint.handlers;

import my.LJExport.maint.Maintenance;
import my.LJExport.runtime.Util;

public class CheckLinkCaseConflicts extends Maintenance
{
    protected void beginUsers()
    {
        Util.out(">>> Checking link case conflicts");
        super.beginUsers("Checking link case conflicts");
    }

    protected void endUsers()
    {
        super.endUsers();
    }

}
