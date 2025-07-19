package my.LJExport.maintenance.handlers;

import java.io.File;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.maintenance.Maintenance;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FilePath;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;

public abstract class MaintenanceHandler extends Maintenance
{
    protected final String linkDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "links";
    protected final String linkDirSep = linkDir + File.separator;

    private static final String FileProtocol = "file://";

    protected static class LinkInfo
    {
        public String linkFullFilePath;
        public String linkRelativeFilePath;
        public String linkRelativeUnixPath;
    }

    protected LinkInfo linkInfo(String fullHtmlFilePath, String href)
    {
        if (href == null)
            return null;

        File fp = new File(fullHtmlFilePath);
        fp = new File(fp, ("../" + href).replace("/", File.separator));
        fp = FilePath.canonicalFile(fp);
        String linkFullFilePath = fp.toString();
        if (!linkFullFilePath.startsWith(linkDirSep))
        {
            return null;
        }
        else
        {
            LinkInfo info = new LinkInfo();
            info.linkFullFilePath = linkFullFilePath;
            info.linkRelativeFilePath = linkFullFilePath.substring(linkDirSep.length());
            info.linkRelativeUnixPath = info.linkRelativeFilePath.replace(File.separatorChar, '/');
            return info;
        }
    }

    protected String spaces(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray())
        {
            sb.append(' ');
            Util.unused(c);
        }
        return sb.toString();
    }

    protected String getLinkAttribute(Node n, String name) throws Exception
    {
        String href = JSOUP.getAttribute(n, name);
        href = preprocesHref(href);
        if (href != null)
            href = LinkDownloader.decodePathComponents(href);
        return href;

    }

    protected void updateLinkAttribute(Node n, String attrname, String newref) throws Exception
    {
        JSOUP.updateAttribute(n, attrname, LinkDownloader.encodePathComponents(newref));
    }

    private String preprocesHref(String href)
    {
        if (href == null)
            return null;

        href = href.trim();
        if (href.startsWith(FileProtocol))
            href = href.substring(FileProtocol.length());

        if (href.isEmpty() || !href.startsWith("../"))
            return null;

        return href;
    }
}