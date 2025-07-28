package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.maintenance.Maintenance;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FilePath;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.links.util.RelativeLink;
import my.LJExport.runtime.url.UrlUtil;
import my.WebArchiveOrg.ArchiveOrgUrl;

public abstract class MaintenanceHandler extends Maintenance
{
    protected final String userDir = isEmpty(Config.User) ? null
            : FilePath.getFilePathActualCase(Config.DownloadRoot + File.separator + Config.User);

    protected final String linksDir = isEmpty(Config.User) ? null
            : FilePath.getFilePathActualCase(userDir + File.separator + "links");

    protected final String linksDirSep = isEmpty(Config.User) ? null : linksDir + File.separator;

    protected final List<String> validNonLinkRoots = validNonLinkRoots();

    private static final String FileProtocol = "file://";

    public static class LinkInfo
    {
        public String linkFullFilePath;
        public String linkRelativeFilePath;
        public String linkRelativeUnixPath;
    }

    public static class FileContentTypeInformation
    {
        public List<KVEntry> list;
        public Map<String, String> lcrel2ctype = new HashMap<>();

        void load(String linksDir) throws Exception
        {
            KVFile kvfile = new KVFile(linksDir + File.separator + "content-type.txt");
            if (kvfile.exists())
                list = kvfile.load(true);
            else
                list = kvfile.getEmptyList();

            lcrel2ctype = new HashMap<>();
            for (KVEntry entry : list)
                lcrel2ctype.put(entry.key.toLowerCase(), entry.value);
        }

        public String contentTypeForLcUnixRelpath(String rel)
        {
            return lcrel2ctype.get(rel.toLowerCase());
        }
    }

    protected FileContentTypeInformation fileContentTypeInformation;

    public MaintenanceHandler() throws Exception
    {
    }

    @Override
    protected void beginUser() throws Exception
    {
        super.beginUser();
        fileContentTypeInformation = null;
    }

    @Override
    protected void endUser() throws Exception
    {
        fileContentTypeInformation = null;
        super.endUser();
    }

    public LinkInfo linkInfo(String fullHtmlFilePath, String href)
    {
        if (href == null)
            return null;

        File fp = new File(fullHtmlFilePath);
        fp = new File(fp, ("../" + href).replace("/", File.separator));
        fp = FilePath.canonicalFile(fp);
        String linkFullFilePath = fp.toString();
        if (!linkFullFilePath.startsWith(linksDirSep))
        {
            return null;
        }
        else
        {
            LinkInfo info = new LinkInfo();
            info.linkFullFilePath = linkFullFilePath;
            info.linkRelativeFilePath = linkFullFilePath.substring(linksDirSep.length());
            info.linkRelativeUnixPath = info.linkRelativeFilePath.replace(File.separatorChar, '/');
            return info;
        }
    }

    protected static String spaces(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray())
        {
            sb.append(' ');
            Util.unused(c);
        }
        return sb.toString();
    }

    public String getLinkAttribute(Node n, String name) throws Exception
    {
        String href = JSOUP.getAttribute(n, name);
        href = preprocesHref(href);
        if (href != null)
            href = LinkFilepath.decodePathComponents(href);
        return href;
    }

    public String getLinkOriginalAttribute(Node n, String name) throws Exception
    {
        String href = JSOUP.getAttribute(n, name);
        
        try
        {
            href = preprocesOriginalHref(href);
            href = UrlUtil.decodeHtmlAttrLink(href);
        }
        catch (Exception ex)
        {
            return null;
            // ### throw ex;
        }
        
        return href;
    }

    protected String getLinkAttributeUndecoded(Node n, String name) throws Exception
    {
        String href = JSOUP.getAttribute(n, name);
        href = preprocesHref(href);
        return href;
    }

    public void updateLinkAttribute(Node n, String attrname, String newref) throws Exception
    {
        JSOUP.updateAttribute(n, attrname, LinkFilepath.encodePathComponents(newref));
    }

    public void setLinkAttribute(Node n, String attrname, String newref) throws Exception
    {
        JSOUP.setAttribute(n, attrname, LinkFilepath.encodePathComponents(newref));
    }

    private String preprocesHref(String href)
    {
        if (href == null)
            return null;

        href = href.trim();
        if (href.toLowerCase().startsWith(FileProtocol))
            href = href.substring(FileProtocol.length());

        if (href.isEmpty() || !href.startsWith("../"))
            return null;

        return href;
    }

    private String preprocesOriginalHref(String href)
    {
        if (href == null)
            return null;

        href = href.trim();
        if (href.toLowerCase().startsWith(FileProtocol))
            href = href.substring(FileProtocol.length());

        if (href.isEmpty())
            return null;

        return href;
    }

    private List<String> validNonLinkRoots()
    {
        if (isEmpty(Config.User))
            return null;

        List<String> list = new ArrayList<>();

        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "profile");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "pages");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "reposts");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "monthly-pages");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "monthly-reposts");
        list.add(Config.DownloadRoot + File.separator + Config.User + File.separator + "styles");

        return list;
    }

    public boolean isLinksRepositoryReference(String fullHtmlFilePath, String href) throws Exception
    {
        String href_root = firstRoot(href);
        String abs_root = RelativeLink.resolveFileRelativeLink(fullHtmlFilePath, href_root);

        for (String root : this.validNonLinkRoots)
        {
            if (abs_root.equals(root) && href.endsWith(".html"))
                return false;
        }

        if (!abs_root.equals(this.linksDir))
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

    public boolean isArchiveOrg()
    {
        return Config.User.equals("nationalism.org");
    }

    // href -> relative Unix path relative to links repository dir
    protected String href2rel(String href, String fullHtmlFilePath) throws Exception
    {
        String abs = href2abs(href, fullHtmlFilePath);
        String rel = abs2rel(abs);
        return rel;
    }

    // href -> absolute file path of a linked file
    protected String href2abs(String href, String fullHtmlFilePath) throws Exception
    {
        String abs = RelativeLink.resolveFileRelativeLink(fullHtmlFilePath, href, this.linksDir);
        return abs;
    }

    // absolute file path of a linked file -> relative Unix path relative to links repository dir  
    public String abs2rel(String abs) throws Exception
    {
        String rel = Util.stripStart(abs, this.linksDir + File.separator);
        rel = rel.replace(File.separatorChar, '/');
        return rel;
    }

    // relative Unix path relative to links repository dir -> absolute file path of a linked file  
    protected String rel2abs(String rel)
    {
        return this.linksDir + File.separator + rel.replace('/', File.separatorChar);
    }

    protected String rel2href(String rel, String fullHtmlFilePath) throws Exception
    {
        String abs = rel2abs(rel);
        return abs2href(abs, fullHtmlFilePath);
    }

    protected String abs2href(String abs, String fullHtmlFilePath) throws Exception
    {
        String href = RelativeLink.fileRelativeLink(abs, fullHtmlFilePath, this.userDir);
        return href;
    }

    private static boolean isEmpty(String s)
    {
        return s == null || s.isEmpty();
    }

    public boolean isLinksRootFileRelativePathSyntax(String relPath)
    {
        return !relPath.replace(File.separator, "/").contains("/");
    }

    public boolean isLinksRootFileRelativePath(String relPath) throws Exception
    {
        if (!isLinksRootFileRelativePathSyntax(relPath))
            return false;

        File fp = new File(this.linksDirSep + relPath.replace("/", File.separator)).getCanonicalFile();

        if (!fp.exists())
            throw new Exception("Unexpected: file does not exist: " + fp.getCanonicalPath());

        return fp.isFile();
    }

    protected void loadFileContentTypeInformation() throws Exception
    {
        fileContentTypeInformation = new FileContentTypeInformation();
        fileContentTypeInformation.load(linksDir);
    }
}