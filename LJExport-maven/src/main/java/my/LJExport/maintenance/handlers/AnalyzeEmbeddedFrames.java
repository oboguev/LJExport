package my.LJExport.maintenance.handlers;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.jsoup.nodes.Node;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.lj.LjToys;
import my.LJExport.runtime.url.UrlUtil;

/*
 * Analyze embedded <iframe> tags
 */
public class AnalyzeEmbeddedFrames extends MaintenanceHandler
{
    public AnalyzeEmbeddedFrames() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Analyzing embedded iframes");
        super.beginUsers("Analyzing embedded iframes");
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    @Override
    protected void beginUser() throws Exception
    {
        super.beginUser();
    }

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        for (Node n : JSOUP.findElements(pageFlat, "iframe"))
        {
            // get ENCODED link
            String href = JSOUP.getAttribute(n, "src");
            if (href == null)
                continue;
            href = href.trim();
            if (href.length() == 0)
                continue;

            String host = UrlUtil.extractHost(href);
            if (host == null)
                continue;
            host = host.trim().toLowerCase();
            if (host.length() == 0)
                continue;

            if (isIgnore(href))
                continue;

            if (!host.equals("l.lj-toys.com"))
            {
                Util.out("");
                Util.out("non-LJToys iframe " + href);
                Util.out("               in " + fullHtmlFilePath);
                continue;
            }

            Optional<LjToys.Target> target = LjToys.extract(href);
            if (target.isEmpty())
            {
                Util.out("");
                Util.out("non-decodable LJToys iframe " + href);
                Util.out("                         in " + fullHtmlFilePath);
                continue;
            }

            if (isIgnore(target.get()))
                continue;

            Util.out("");
            Util.out("non-video LJToys iframe " + href);
            Util.out("                     in " + fullHtmlFilePath);
            Util.out("                   with " + target.get());
            continue;
        }
    }

    private boolean isIgnore(LjToys.Target target) throws Exception
    {
        if (target.url != null && isIgnore(target.url))
            return true;

        if (Util.False && target.source != null && Util.in(target.source, "vk.com"))
            return true;

        return false;
    }

    private boolean isIgnore(String url) throws Exception
    {
        String host = UrlUtil.extractHost(url);

        if (host == null || host.trim().length() == 0)
            return false;
        host = host.trim().toLowerCase();

        if (host.equals("youtube.com") || host.endsWith(".youtube.com") ||
            host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com") ||
            host.equals("youtu.be") || host.endsWith(".youtu.be") ||
            host.equals("vimeo.com") || host.endsWith(".vimeo.com") ||
            host.equals("rutube.ru") || host.endsWith(".rutube.ru") ||
            host.equals("slideshare.net") || host.endsWith(".slideshare.net") ||
            host.equals("coub.com") || host.endsWith(".coub.com") ||
            host.equals("openstreetmap.org") || host.endsWith(".openstreetmap.org") ||
            host.equals("video.yandex.ru") || host.endsWith(".video.yandex.ru"))
        {
            return true;
        }

        if (host.equals("vk.com") || host.equals("vkontakte.ru"))
        {
            URL xurl = new URL(url);
            String path = xurl.getPath();
            if (path != null && path.startsWith("/video_ext.php"))
                return true;
        }

        return false;
    }
}
