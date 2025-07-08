package my.WebArchiveOrg;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.html.JSOUP;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.TeleportUrl;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.Web.Response;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.RelativeLink;
import my.WebArchiveOrg.customize.Exclude;

/*
 * Использовать -Xss16m
 */
public class MainScrapeArchiveOrg
{
    // private static final String ArchiveOrgWebRoot = "https://web.archive.org/web/20160411084012/http://www.nationalism.org/";
    private static final String ArchiveOrgWebRoot = "https://web.archive.org/web/20080912012829/http://nationalism.org";
    // private static final String DownloadRoot = "C:\\LJExport-journals\\nationalism.org";
    private static final String DownloadRoot = Config.DownloadRoot + File.separator + "nationalism.org";
    // private static final Set<String> Excludes = Util.setOf("forum/", "rr/4/index.htm");
    private static final Set<String> Excludes = Util.setOf("rr/4/index.htm");

    private final String archiveOrgWebRoot;
    private final String downloadRoot;
    private final String pagesDir;
    private final String linksDir;
    private final String pageMapFilePath;
    private final File pageMapFile;
    private Map<String, String> pageMap = null;
    private Set<String> url_set_404 = null;

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
        Web.init();

        if (new File(set404filepath()).getCanonicalFile().exists())
            url_set_404 = Util.read_set_from_file(set404filepath());
        else
            url_set_404 = new HashSet<>();
    }

    private String set404filepath()
    {
        return this.downloadRoot + File.separator + "http-error-404.txt";
    }

    private void do_main() throws Exception
    {
        LimitProcessorUsage.limit();
        Util.out(">>> Start time: " + Util.timeNow());

        if (Config.False)
        {
            // donwload HTML files from archive.org
            scrape("");
        }

        if (Config.False)
        {
            // diagnostic
            lookupDoubleArchiveLinks("a", "href");
            lookupDoubleArchiveLinks("img", "src");
        }

        if (Config.True)
        {
            // remap intra-page html links ("a") to local files
            remapRelativePageLinks();
        }

        if (Config.False)
        {
            loadExternalResources();
        }

        // download images. pdfs etc. and remap links (a, img)

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
            xurl = TeleportUrl.ungarbleTeleportUrl(xurl);

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
        return pagesDir + File.separator + encodeUnsafeFileNameChars(relPath.replace("/", File.separator));
    }

    /**
     * Encodes unsafe characters for filenames, but preserves valid existing %XX sequences. Encoded characters: = & ? % ' * : < >
     * 
     * @param input
     *            input string possibly containing already-encoded segments
     * @return sanitized file name
     */
    public static String encodeUnsafeFileNameChars(String input)
    {
        if (input == null)
            return null;

        StringBuilder sb = new StringBuilder();
        int len = input.length();
        for (int i = 0; i < len; i++)
        {
            char ch = input.charAt(i);

            // Handle percent sign separately
            if (ch == '%')
            {
                if (i + 2 < len && isHexDigit(input.charAt(i + 1)) && isHexDigit(input.charAt(i + 2)))
                {
                    // Valid %XX sequence, copy as is
                    sb.append('%').append(input.charAt(i + 1)).append(input.charAt(i + 2));
                    i += 2;
                }
                else
                {
                    // Not a valid encoding, encode the %
                    sb.append("%25");
                }
            }
            else
            {
                switch (ch)
                {
                case '=':
                    sb.append("%3D");
                    break;
                case '&':
                    sb.append("%26");
                    break;
                case '?':
                    sb.append("%3F");
                    break;
                case '\'':
                    sb.append("%27");
                    break;
                case '*':
                    sb.append("%2A");
                    break;
                case ':':
                    sb.append("%3A");
                    break;
                case '<':
                    sb.append("%3C");
                    break;
                case '>':
                    sb.append("%3E");
                    break;
                default:
                    sb.append(ch);
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static boolean isHexDigit(char c)
    {
        return (c >= '0' && c <= '9') ||
                (c >= 'A' && c <= 'F') ||
                (c >= 'a' && c <= 'f');
    }

    private void downloadFromArchive(String url, File fp) throws Exception
    {
        if (url_set_404.contains(url))
            return;

        if (Exclude.isNationalismOrgForumControlURL(url))
        {
            // Util.err("Excluding " + url);
            return;
        }

        Util.out(String.format("Downloading %s => %s", url, fp.getCanonicalPath()));

        if (url.contains("#"))
        {
            Util.noop();
        }

        Response r = Web.get(url);

        if (r.code != HttpStatus.SC_OK)
        {
            Util.err(String.format("Failed to load %s, error: %s", url, Web.describe(r.code)));

            if (r.code == 404)
            {
                url_set_404.add(url);
                Util.write_set_to_file(set404filepath(), url_set_404);
            }

            return;
        }

        ParserArchiveOrg parser = new ParserArchiveOrg();
        parser.pageSource = r.body;
        parser.parseHtmlWithBaseUrl(r.finalUrl);

        String charset = parser.extractCharset();

        if (charset != null && charset.equalsIgnoreCase("win-1251"))
            charset = "windows-1251";

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

            if (m.get(relPath) != null && !m.get(relPath).equals(finalUrl))
            {
                switch (relPath)
                {
                case "index.html":
                    break;
                default:
                    throw new Exception("Unable to read page map file");
                }
            }

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
        Util.out("Remapping local page links in " + fileRelPath);
        
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
            if (href != null && href.equals(
                    "https://web.archive.org/web/20160622134130/http:/nationalism.org/library/publicism/holmogorov/holmogorov-specnaz-2002-01-1.htm"))
            {
                Util.noop();
            }

            href = TeleportUrl.ungarbleTeleportUrl(href);

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
        File fp = new File(pagesDir + File.separator + encodeUnsafeFileNameChars(linkRelPath.replace("/", File.separator)));
        if (!fp.exists())
            return false;

        if (fp.isDirectory())
        {
            Pair<File, String> p = findDirIndexFile(fp, linkRelPath);
            if (p == null)
                return false;
            fp = p.getLeft();
            linkRelPath = p.getRight();
        }

        String newref = RelativeLink.createRelativeLink(encodeUnsafeFileNameChars(linkRelPath), loadedFileRelPath);

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

    private Pair<File, String> findDirIndexFile(File fp, String linkRelPath)
    {
        // index.htm if exists otherwise index.html if exists
        Pair<File, String> p = findDirIndexFile(fp, linkRelPath, "index.htm");
        if (p == null)
            p = findDirIndexFile(fp, linkRelPath, "index.html");
        return p;
    }

    private Pair<File, String> findDirIndexFile(File fp, String linkRelPath, String indexFile)
    {
        if (linkRelPath.length() == 0)
            linkRelPath += "index.html";
        else
            linkRelPath += "/index.html";

        fp = new File(pagesDir + File.separator + encodeUnsafeFileNameChars(linkRelPath.replace("/", File.separator)));
        if (fp.exists())
            return Pair.of(fp, encodeUnsafeFileNameChars(linkRelPath.replace(File.separator, "/")));
        else
            return null;
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
                    href = TeleportUrl.ungarbleTeleportUrl(href);

                    if (href.startsWith("http://web.archive.org/"))
                    {
                        Util.err("Unexpected link:" + href);
                    }

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

    /* ========================================================================================== */

    private void loadExternalResources() throws Exception
    {
        pageMap = readPageMap();

        LinkDownloader.init(linksDir);

        List<String> list = Util.enumerateAnyHtmlFiles(pagesDir);
        for (String fn : list)
        {
            String fullFilePath = pagesDir + File.separator + fn;
            String relPath = fn.replace(File.separator, "/");
            loadExternalResources(fullFilePath, relPath);
        }
    }

    private void loadExternalResources(String fullFilePath, String fileRelPath) throws Exception
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
                updated |= loadExternalResource(an, "href", href, fileRelPath);
            }
        }

        for (Node an : JSOUP.findElements(parser.pageRoot, "img"))
        {
            String href = JSOUP.getAttribute(an, "src");

            if (href != null && ArchiveOrgUrl.urlMatchesRoot(href, archiveOrgWebRoot, true))
            {
                updated |= loadExternalResource(an, "src", href, fileRelPath);
            }
        }

        if (updated && Config.False) // ###
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullFilePath, html);
        }
    }

    private boolean loadExternalResource(Node an, String tag, String original_href, String loadedFileRelPath) throws Exception
    {
        // ### degarbleTeleportPro
        /*
         * Unwrapped_href is used for resource naming, it is original URL before archival.
         * Whereas original_href is used for actual resource downloading.
         */
        String unwrapped_href = ArchiveOrgUrl.extractArchivedUrlPart(original_href);
        if (unwrapped_href == null)
            unwrapped_href = original_href;

        if (!LinkDownloader.shouldDownload(unwrapped_href, tag.equalsIgnoreCase("a")))
            return false;

        String newref = LinkDownloader.download(linksDir, unwrapped_href, original_href, null, "");

        if (newref == null)
        {
            Util.err("Failed to download " + original_href);
        }
        else
        {
            Util.out("Downloaded " + original_href + " => " + newref);
        }

        // ### try to download original_href 
        // ### if cannot, return false

        return false;
    }
}
