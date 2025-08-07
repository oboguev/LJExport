package my.LJExport.runtime.lj;

import my.LJExport.Config;

public class LJUserHost
{
    public String user;
    public String mangledUser;
    public String host;
    
    public static LJUserHost split(String user) throws Exception
    {
        LJUserHost uh = new LJUserHost();
        String [] sa = user.split("\\.");

        if (sa.length == 1)
        {
            uh.user = user;
            uh.mangledUser = Config.mangleUser(uh.user);

            if (Config.isDreamwidthOrg())
            {
                uh.host = "dreamwidth.org";
            }
            else if (Config.isRossiaOrg())
            {
                uh.host = "lj.rossia.org";
            }
            else
            {
                uh.host = "livejournal.com";
            }
            
            return uh;
        }

        if (sa.length != 2)
            throw new Exception("");
        
        uh.user = sa[0];
        uh.mangledUser = Config.mangleUser(uh.user);
        
        switch (sa[1])
        {
        case "dreamwidth-org":
            uh.host = "dreamwidth.org";
            break;
        
        case "lj-rossia-org":
            uh.host = "lj.rossia.org";
            break;
            
        default:
            throw new Exception("Invalid user id specification: " + user);
        }
        
        return uh;
    }
}
