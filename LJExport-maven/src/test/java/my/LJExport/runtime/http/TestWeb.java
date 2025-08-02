package my.LJExport.runtime.http;

import my.LJExport.Config;
import my.LJExport.runtime.Util;

public class TestWeb
{
    public static void main(String[] args)
    {
        try
        {
            TestWeb self = new TestWeb();
            Config.init("");
            Web.init();
            self.test_1();
        }
        catch (Exception ex)
        {
            Util.err("** Exception: " + ex.getLocalizedMessage());;
            ex.printStackTrace();
        }
    }
    
    private void test_1() throws Exception
    {
        Web.Response r = null;
        r = Web.get("https://www.google.com/");
        Util.unused(r);
    }
}
