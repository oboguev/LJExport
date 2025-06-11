package my.LJExport.test;

import my.LJExport.Main;
import my.LJExport.runtime.URLCodec;
import my.LJExport.runtime.Util;

public class TestMisc
{
    public static void main(String[] args)
    {
        try
        {
            new TestMisc().do_main();
        }
        catch (Exception ex)
        {
            Main.err("** Exception ");
            ex.printStackTrace();
        }
    }
    
    private void do_main() throws Exception
    {
        String decoded;
        
        final String ts1 = "%D0%9A%D1%83%D0%BB%D0%B8%D0%BA%D0%BE%D0%B2%20%D0%A1.%D0%92.%20%D0%98%D0%BC%D0%BF%D0%B5%D1%80%D0%B0%D1%82%D0%BE%D1%80%20%D0%9D%D0%B8%D0%BA%D0%BE%D0%BB%D0%B0%D0%B9%20II%20%D0%BA%D0%B0%D0%BA%20%D1%80%D0%B5%D1%84%D0%BE%D1%80%D0%BC%D0%B0%D1%82%D0%BE%D1%80_%D0%BA%20%D0%BF%D0%BE%D1%81%D1%82%D0%B0%D0%BD%D0%BE%D0%B2%D0%BA%D0%B5%20%D0%BF%D1%80%D0%BE%D0%B1%D0%BB%D0%B5%D0%BC.pdf";
        final String ts2 = "aaa%80яяя%E2%82%ACzzzz";

        decoded = URLCodec.decodeMixed(ts1);
        decoded = URLCodec.decodeMixed(ts2);
        
        decoded = URLCodec.decode(ts1);
        String encoded = URLCodec.encode(decoded);
        encoded = encoded.replace("+", "%20");
        Util.noop();
    }
}
