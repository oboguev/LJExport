package my.WebArchiveOrg;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpStatus;
import org.jsoup.nodes.Node;

import my.LJExport.Main;
import my.LJExport.runtime.LimitProcessorUsage;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.Web.Response;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.xml.JSOUP;

public class MainScrapeArchiveOrg
{
    private static final String ArchiveOrgWebRoot = "https://web.archive.org/web/20160411084012/http://www.nationalism.org/";
    private static final String DownloadRoot = "C:\\LJExport-journals\\nationalism.org";

    private final String archiveOrgWebRoot;
    private final String downloadRoot;
    private final String pagesDir;
    private final String linksDir;

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
        Util.mkdir(pagesDir);
        Util.mkdir(linksDir);
        LinkDownloader.init(linksDir);
        Web.init();
    }

    private void do_main() throws Exception
    {
        LimitProcessorUsage.limit();
        Util.out(">>> Start time: " + Util.timeNow());
        scrape("");
        Main.playCompletionSound();
    }

    private void scrape(String relPath) throws Exception
    {
        String url;
        String localFilePath;

        if (isFile(relPath))
        {
            url = archiveOrgWebRoot + "/" + relPath;
            localFilePath = fileRelPath2FullPath(relPath);
        }
        else
        {
            if (relPath.length() != 0)
                url = archiveOrgWebRoot + "/" + relPath;
            else
                url = archiveOrgWebRoot;

            String rp = relPath;
            if (rp.length() != 0 && Util.lastChar(rp) != '/')
                rp += "/";
            rp += "index.html";
            localFilePath = fileRelPath2FullPath(rp);
        }

        File fp = new File(localFilePath).getCanonicalFile();
        if (!fp.exists())
            downloadArchive(url, fp);
        if (fp.exists())
            follow(relPath, url, fp);
    }
    
    private void follow(String relPath, String url, File fp) throws Exception
    {
        ParserArchiveOrg parser = new ParserArchiveOrg();
        parser.pageSource = Util.readFileAsString(fp.getCanonicalPath());
        parser.parseHtmlWithBaseUrl(url);
        
        Set<String> links = new HashSet<>();
        
        for (Node an : JSOUP.findElements(parser.pageRoot, "a"))
        {
            String href = JSOUP.getAttribute(an, "href");
            if (href != null && href.startsWith(ArchiveOrgWebRoot))
            {
                links.add(href);
            }
        }
        
        Util.noop();
        
        // ### extract links
    }

    private boolean isFile(String relPath)
    {
        // file.xxx or file.xxxx
        return relPath.matches(".*\\.[A-Za-z]{3,4}$");
    }

    public String fileRelPath2FullPath(String relPath)
    {
        return pagesDir + File.separator + relPath.replace("/", File.separator);
    }

    private void downloadArchive(String url, File fp) throws Exception
    {
        Util.out(String.format("Downloading %s => %s", url, fp.getCanonicalPath()));
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
        
        // ### download linked img/pdf files and remap links
        
        String html = JSOUP.emitHtml(parser.pageRoot);

        if (!fp.getParentFile().exists())
            fp.getParentFile().mkdirs();
        Util.writeToFileSafe(fp.getCanonicalPath(), html);
    }
}
