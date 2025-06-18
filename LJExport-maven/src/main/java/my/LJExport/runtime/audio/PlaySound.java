package my.LJExport.runtime.audio;

import my.LJExport.runtime.Util;

public class PlaySound
{
    public static void play(String path)
    {
        try
        {
            byte[] ba = Util.loadResourceAsBytes(path);
            long ts0 = System.currentTimeMillis();
            
            for (;;)
            {
                long ts = System.currentTimeMillis();
                if (ts - ts0 > 60 * 1000)
                    continue;
                if (path.endsWith(".mp3"))
                {
                    PlayMP3.play(ba);
                }
                else if (path.endsWith(".wav"))
                {
                    PlayWAV.play(ba);
                }
            }
        }
        catch (Exception ex)
        {
            Util.noop();
        }
    }
}
