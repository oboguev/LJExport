package my.LJExport.runtime.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

public class PlayWAV
{
    public static void play(byte[] audioData)
    {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(bais))
        {

            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

            while (!clip.isRunning())
                Thread.sleep(10);
            while (clip.isRunning())
                Thread.sleep(10);

            clip.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
