package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.maintenance.Maintenance;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FilePath;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.RelativeLink;

public abstract class MaintenanceHandler extends Maintenance
{
    protected final String linkDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "links";
    protected final String linkDirSep = linkDir + File.separator;
    protected final List<String> validNonLinkRoots = validNonLinkRoots();

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

    private List<String> validNonLinkRoots()
    {
        List<String> list = new ArrayList<>();

        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "profile");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "pages");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "reposts");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "monthly-pages");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "monthly-reposts");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "styles");

        return list;
    }

    protected boolean isLinksRepositoryReference(String fullHtmlFilePath, String href) throws Exception
    {
        String href_root = firstRoot(href);
        String abs_root = RelativeLink.resolveFileRelativeLink(fullHtmlFilePath, href_root);

        for (String root : this.validNonLinkRoots)
        {
            if (abs_root.equals(root) && href.endsWith(".html"))
                return false;
        }

        if (!abs_root.equals(this.linkDir))
        {
            if (this.isArchiveOrg())
                return false;
            else
                throw new Exception("Path escapes links root directory");
        }

        return true;
    }

    private String firstRoot(String href)
    {
        if (href == null || href.isEmpty())
            throw new IllegalArgumentException("href must not be null or empty");

        String[] parts = href.split("/");
        StringBuilder result = new StringBuilder();

        for (String part : parts)
        {
            if (part.isEmpty())
                continue; // ignore repeated slashes
            if (part.equals(".") || part.equals(".."))
            {
                result.append(part).append("/");
            }
            else
            {
                result.append(part);
                return result.toString();
            }
        }

        throw new IllegalArgumentException("No root component found in href: " + href);
    }

    protected boolean isArchiveOrg()
    {
        return Config.User.equals("nationalism.org");
    }
}