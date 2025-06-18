package my.LJExport.runtime.audio;

import javazoom.jl.player.Player;
import java.io.ByteArrayInputStream;

public class PlayMP3
{
    public static void play(byte[] mp3Data)
    {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(mp3Data))
        {
            Player player = new Player(bais);
            player.play();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
