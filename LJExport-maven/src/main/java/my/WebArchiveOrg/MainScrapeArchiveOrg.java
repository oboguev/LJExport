package my.WebArchiveOrg;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.html.JSOUP;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.Web.Response;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.RelativeLink;

/*
 * Использовать -Xss16m
 */
public class MainScrapeArchiveOrg
{
    private static final String ArchiveOrgWebRoot = "https://web.archive.org/web/20160411084012/http://www.nationalism.org/";
    // private static final String DownloadRoot = "C:\\LJExport-journals\\nationalism.org";
    private static final String DownloadRoot = Config.DownloadRoot + File.separator + "nationalism.org";
    private static final Set<String> Excludes = Util.setOf("forum/", "rr/4/index.htm");

    private final String archiveOrgWebRoot;
    private final String downloadRoot;
    private final String pagesDir;
    private final String linksDir;
    private final String pageMapFilePath;
    private final File pageMapFile;
    private Map<String, String> pageMap = null;

    private Set<String> prune_follow = new HashSet<>();
    private Set<String> prune_scrape = new HashSet<>();

    private static final String PageMapFileDivider = "----";

    public static void main(String[] args)
    {
        try
        {
            MainScrapeArchiveOrg self = new MainScrapeArchiveOrg(ArchiveOrgWebRoot, DownloadRoot);
            self.do_main();
        }
        catch (Exception ex)
        {
            System.err.println("*** Exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private MainScrapeArchiveOrg(String archiveOrgWebRoot, String downloadRoot) throws Exception
    {
        this.archiveOrgWebRoot = archiveOrgWebRoot;
        this.downloadRoot = downloadRoot;

        pagesDir = this.downloadRoot + File.separator + "pages";
        linksDir = this.downloadRoot + File.separator + "links";
        pageMapFilePath = this.downloadRoot + File.separator + "pages-map.txt";
        pageMapFile = new File(pageMapFilePath).getCanonicalFile();
        Util.mkdir(pagesDir);
        Util.mkdir(linksDir);
        LinkDownloader.init(linksDir);
        Web.init();
    }

    private void do_main() throws Exception
    {
        LimitProcessorUsage.limit();
        Util.out(">>> Start time: " + Util.timeNow());

        // donwload HTML files
        if (Config.False)
        {
            scrape("");
        }

        lookupDoubleArchiveLinks("a", "href");
        lookupDoubleArchiveLinks("img", "src");

        if (Config.False)
        {
            // rempap intra-page html links ("a") to local files
            remapRelativePageLinks();

            // ### download images and pdfs and remap links (a, img)
        }

        Util.out(">>> Completed");
        Main.playCompletionSound();
    }

    private void scrape(String relPath) throws Exception
    {
        scrape(relPath, archiveOrgWebRoot);
    }

    private void scrape(String relPath, String webRoot) throws Exception
    {
        /*
         * Обрезать рекурсию циклических ссылок
         */
        if (prune_scrape.contains(relPath))
            return;
        prune_scrape.add(relPath);

        if (toExclude(relPath))
            return;

        String url;
        String localFilePath;

        if (isHtmlFilePage(relPath))
        {
            url = webRoot + "/" + relPath;
            localFilePath = fileRelPath2FullPath(relPath);
        }
        else if (isHtmlDirPage(relPath))
        {
            if (relPath.length() != 0)
                url = webRoot + "/" + relPath;
            else
                url = webRoot;

            String rp = relPath;
            if (rp.length() != 0 && Util.lastChar(rp) != '/')
                rp += "/";
            rp += "index.html";
            localFilePath = fileRelPath2FullPath(rp);
        }
        else
        {
            return;
        }

        File fp = getCanonicalFile(new File(localFilePath));
        if (fp != null)
        {
            if (!fp.exists())
                downloadFromArchive(url, fp);
            if (fp.exists())
                follow(relPath, url, fp);
        }
    }

    private void follow(String relPath, String url, File fp) throws Exception
    {
        /*
         * Обрезать рекурсию циклических ссылок
         */
        if (prune_follow.contains(relPath))
            return;
        prune_follow.add(relPath);

        if (toExclude(relPath))
            return;

        Util.out("Scanning/traversing file " + fp.getCanonicalPath());

        ParserArchiveOrg parser = new ParserArchiveOrg();
        parser.pageSource = Util.readFileAsString(fp.getCanonicalPath());
        parser.parseHtmlWithBaseUrl(url);

        Set<String> links = new HashSet<>();

        for (Node an : JSOUP.findElements(parser.pageRoot, "a"))
        {
            String href = JSOUP.getAttribute(an, "href");
            href = restoreArchiveOrgUrl(href);
            if (href != null)
                links.add(href);
        }

        Util.noop();

        for (String xurl : links)
        {
            if (ArchiveOrgUrl.urlMatchesRoot(xurl, archiveOrgWebRoot, true))
            {
                String webRoot = ArchiveOrgUrl.urlExtractRoot(xurl, archiveOrgWebRoot);
                String xrelPath = ArchiveOrgUrl.urlRelativePath(xurl, archiveOrgWebRoot);
                xrelPath = Util.stripAnchor(xrelPath);
                scrape(xrelPath, webRoot);
            }
            else
            {
                Util.noop();
            }
        }
    }

    private boolean isHtmlFilePage(String relPath)
    {
        // file.xxx or file.xxxx
        // return relPath.matches(".*\\.[A-Za-z]{3,4}$");
        String extension = Util.getFileExtension(relPath);
        if (extension == null)
            return false;

        switch (extension.toLowerCase())
        {
        case "htm":
        case "html":
        case "shtm":
        case "shtml":
            return true;

        default:
            return false;
        }
    }

    private boolean isHtmlDirPage(String relPath)
    {
        String extension = Util.getFileExtension(relPath);
        return extension == null;
    }

    public String fileRelPath2FullPath(String relPath)
    {
        return pagesDir + File.separator + relPath.replace("/", File.separator);
    }

    private void downloadFromArchive(String url, File fp) throws Exception
    {
        Util.out(String.format("Downloading %s => %s", url, fp.getCanonicalPath()));

        if (url.contains("#"))
        {
            Util.noop();
        }

        Response r = Web.get(url);

        if (r.code != HttpStatus.SC_OK)
        {
            Util.err(String.format("Failed to load %s, error: ", url, Web.describe(r.code)));
            return;
        }

        ParserArchiveOrg parser = new ParserArchiveOrg();
        parser.pageSource = r.body;
        parser.parseHtmlWithBaseUrl(r.finalUrl);

        String charset = parser.extractCharset();
        if (charset != null && r.charset != null && !charset.equalsIgnoreCase(r.charset.name()))
        {
            parser = new ParserArchiveOrg();
            parser.pageSource = new String(r.binaryBody, charset);
            parser.parseHtmlWithBaseUrl(r.finalUrl);
        }

        parser.removeArchiveJunk();
        parser.resolveAbsoluteURLs(r.finalUrl);

        String html = JSOUP.emitHtml(parser.pageRoot);

        if (!fp.getParentFile().exists())
            fp.getParentFile().mkdirs();
        Util.writeToFileSafe(fp.getCanonicalPath(), html);

        savePageFileMap(fp.getCanonicalPath(), url, r.finalUrl);
    }

    private void savePageFileMap(String filePath, String url, String finalUrl) throws Exception
    {
        if (!filePath.startsWith(pagesDir))
            throw new Exception("File is out of pages directory: " + filePath);

        String relPath = filePath.substring(pagesDir.length());
        relPath = relPath.replace(File.separator, "/");
        if (!relPath.startsWith("/"))
            throw new Exception("File is out of pages directory: " + filePath);
        relPath = relPath.substring(1);

        final String nl = Util.isWindowsOS() ? "\r\n" : "\n";
        StringBuilder sb = new StringBuilder();
        sb.append(relPath + nl).append(url + nl).append(finalUrl + nl).append(PageMapFileDivider + nl);

        String content = pageMapFile.exists() ? Util.readFileAsString(pageMapFile.getCanonicalPath()) : "";
        Util.writeToFileSafe(pageMapFile.getCanonicalPath(), content += sb.toString());
    }

    // https://web.archive.org/web/TIMESTAMP/http:/REMAINDER -> https://web.archive.org/web/TIMESTAMP/http://REMAINDER
    // https://web.archive.org/web/TIMESTAMP/https:/REMAINDER -> https://web.archive.org/web/TIMESTAMP/https://REMAINDER
    private static String restoreArchiveOrgUrl(String href)
    {
        if (href == null)
            return null;

        // Match archive.org prefix and malformed scheme
        // Example: https://web.archive.org/web/20160411084012/http:/www.site.com/
        String prefix = "https://web.archive.org/web/";
        if (!href.startsWith(prefix))
            return href;

        // Find where the embedded original scheme starts (after TIMESTAMP/)
        int afterPrefix = prefix.length();
        int slashIndex = href.indexOf('/', afterPrefix);
        if (slashIndex == -1 || slashIndex + 1 >= href.length())
            return href;

        // Extract the scheme component
        String schemePart = href.substring(slashIndex + 1);
        if (schemePart.startsWith("http:/") && !schemePart.startsWith("http://"))
        {
            return href.replaceFirst("http:/", "http://");
        }
        else if (schemePart.startsWith("https:/") && !schemePart.startsWith("https://"))
        {
            return href.replaceFirst("https:/", "https://");
        }

        return href;
    }

    private File getCanonicalFile(File fp) throws Exception
    {
        try
        {
            return fp.getCanonicalFile();
        }
        catch (Exception ex)
        {
            Util.err("Unable to canonicize file path " + fp.getPath());
            return null;
        }
    }

    private boolean toExclude(String relPath)
    {
        for (String ex : Excludes)
        {
            if (ex.endsWith("/"))
            {
                if (relPath.startsWith(ex) || ex.equals(relPath + "/"))
                    return true;
            }
            else if (ex.equals(relPath))
            {
                return true;
            }
        }

        return false;
    }

    /* ========================================================================== */

    private Map<String, String> readPageMap() throws Exception
    {
        Map<String, String> m = new HashMap<>();

        String[] lines = Util.readFileAsString(pageMapFile.getCanonicalPath()).replace("\r", "").split("\n");

        for (int k = 0;; k += 4)
        {
            if (k + 3 >= lines.length)
                throw new Exception("Unable to read page map file");

            String relPath = lines[k + 0];
            String url = lines[k + 1];
            String finalUrl = lines[k + 2];
            String divider = lines[k + 3];

            if (!divider.equals(PageMapFileDivider))
                throw new Exception("Unable to read page map file");

            if (m.get(relPath) != null)
                throw new Exception("Unable to read page map file");

            m.put(relPath, finalUrl);

            Util.unused(url);

            if (k + 4 == lines.length)
                break;
        }

        return m;
    }

    /* ========================================================================== */

    private void remapRelativePageLinks() throws Exception
    {
        pageMap = readPageMap();

        List<String> list = Util.enumerateAnyHtmlFiles(pagesDir);
        for (String fn : list)
        {
            String fullFilePath = pagesDir + File.separator + fn;
            String relPath = fn.replace(File.separator, "/");
            remapRelativePageLinks(fullFilePath, relPath);
        }
    }

    private void remapRelativePageLinks(String fullFilePath, String fileRelPath) throws Exception
    {
        ParserArchiveOrg parser = new ParserArchiveOrg();
        parser.pageSource = Util.readFileAsString(fullFilePath);
        String baseUrl = pageMap.get(fileRelPath);
        if (baseUrl == null)
            throw new Exception("File is not in page map");
        parser.parseHtmlWithBaseUrl(baseUrl);

        boolean updated = false;

        for (Node an : JSOUP.findElements(parser.pageRoot, "a"))
        {
            String href = JSOUP.getAttribute(an, "href");
            if (href != null && ArchiveOrgUrl.urlMatchesRoot(href, archiveOrgWebRoot, true))
            {
                boolean b = remapNodeA(an, href, fileRelPath);
                if (!b)
                {
                    // Util.err("Failed to remap link " + href);
                }
                updated |= b;
            }
        }

        if (updated)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullFilePath, html);
        }
    }

    private boolean remapNodeA(Node an, String href, String loadedFileRelPath) throws Exception
    {
        String linkRelPath = ArchiveOrgUrl.urlRelativePath(href, archiveOrgWebRoot);

        String anchor = extractAnchor(linkRelPath);
        linkRelPath = Util.stripAnchor(linkRelPath);

        while (linkRelPath.endsWith("/"))
            linkRelPath = Util.stripTail(linkRelPath, "/");

        /* link can be file or directory */
        File fp = new File(pagesDir + File.separator + linkRelPath.replace("/", File.separator));
        if (!fp.exists())
            return false;

        if (fp.isDirectory())
        {
            if (linkRelPath.length() == 0)
                linkRelPath += "index.html";
            else
                linkRelPath += "/index.html";

            fp = new File(pagesDir + File.separator + linkRelPath.replace("/", File.separator));
            if (!fp.exists())
                return false;
        }

        String newref = RelativeLink.createRelativeLink(linkRelPath, loadedFileRelPath);

        if (anchor != null)
            newref += anchor;

        if (JSOUP.getAttribute(an, "original-href") == null)
            JSOUP.setAttribute(an, "original-href", href);

        JSOUP.updateAttribute(an, "href", newref);

        if (Config.False)
        {
            Util.out(loadedFileRelPath);
            Util.out(linkRelPath);
            Util.out(newref);
            Util.out("-----------------");
        }

        return true;
    }

    private String extractAnchor(String urlPath)
    {
        int hashIndex = urlPath.indexOf('#');
        if (hashIndex >= 0 && hashIndex < urlPath.length() - 1)
            return urlPath.substring(hashIndex);
        else
            return null;
    }

    /* ========================================================================================== */

    private void lookupDoubleArchiveLinks(String tag, String attr) throws Exception
    {
        pageMap = readPageMap();
        List<String> list = Util.enumerateAnyHtmlFiles(pagesDir);

        for (String fn : list)
        {
            String fullFilePath = pagesDir + File.separator + fn;
            String fileRelPath = fn.replace(File.separator, "/");

            ParserArchiveOrg parser = new ParserArchiveOrg();
            parser.pageSource = Util.readFileAsString(fullFilePath);
            String baseUrl = pageMap.get(fileRelPath);
            if (baseUrl == null)
                throw new Exception("File is not in page map");
            parser.parseHtmlWithBaseUrl(baseUrl);

            for (Node n : JSOUP.findElements(parser.pageRoot, tag))
            {
                String href = JSOUP.getAttribute(n, attr);
                if (href != null)
                {
                    int count = 0;
                    int index = 0;
                    boolean mult = false;
                    String target = "archive.org";

                    while ((index = href.indexOf(target, index)) != -1)
                    {
                        count++;
                        index += target.length();

                        if (count > 1)
                        {
                            mult = true;
                        }
                    }

                    if (mult)
                    {
                        Util.out(href);
                        Util.noop();
                    }
                }
            }
        }
    }
}
