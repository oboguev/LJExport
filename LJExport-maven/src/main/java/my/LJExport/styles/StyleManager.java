package my.LJExport.styles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.html.JSOUP;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.links.LinkDownloader;
import my.WebArchiveOrg.ParserArchiveOrg;

public class StyleManager
{
    private final String styleCatalogDir;

    private String styleDir;
    private LinkDownloader linkManager = new LinkDownloader(); 

    public StyleManager(String styleCatalogDir) throws Exception
    {
        while (styleCatalogDir.endsWith(File.separator))
            styleCatalogDir = Util.stripTail(styleCatalogDir, File.separator);

        styleCatalogDir = new File(styleCatalogDir).getCanonicalPath();
        Util.mkdir(styleCatalogDir);

        this.styleCatalogDir = styleCatalogDir;
    }

    public String getStyleDir()
    {
        return styleDir;
    }

    public synchronized void init() throws Exception
    {
        close();

        File catalog = new File(styleCatalogDir);
        if (!catalog.isDirectory())
            throw new IOException("styleCatalogDir does not exist or is not a directory: " + styleCatalogDir);

        final Pattern styleDirPattern = Pattern.compile("^styles\\.(\\d{4}-\\d{2}-\\d{2}-\\d{2}_\\d{2}_\\d{2})$");
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH_mm_ss");
        final File newDir = new File(catalog, "new");

        if (!newDir.exists())
        {
            // Case 1: "new" directory does not exist
            File[] candidates = catalog.listFiles(file -> file.isDirectory() && styleDirPattern.matcher(file.getName()).matches());

            if (candidates != null && candidates.length > 0)
            {
                // Sort by timestamp and select the oldest
                File oldest = Arrays.stream(candidates)
                        .min(Comparator.comparing(File::getName)) // works because timestamped names are lexically ordered
                        .orElseThrow(() -> new IllegalStateException("No style directories found despite length check")); // cannot happen due to length check
                styleDir = oldest.getCanonicalPath();
            }
            else
            {
                // No existing styles.* folder — create a new one
                String newName = "styles." + LocalDateTime.now().format(formatter);
                File created = new File(catalog, newName);
                if (!created.mkdir())
                    throw new IOException("Failed to create directory: " + created.getCanonicalPath());
                styleDir = created.getCanonicalPath();
            }
        }
        else
        {
            // Case 2: "new" directory exists

            // Check if "new" is empty (no files or subdirectories)
            String[] contents = newDir.list();
            if (contents != null && contents.length > 0)
                throw new IOException("'new' directory is not empty: " + newDir.getCanonicalPath());

            // Generate new style folder name
            String newName = "styles." + LocalDateTime.now().format(formatter);
            File targetDir = new File(catalog, newName);

            // Ensure no existing styles.* folder has a newer or equal timestamp
            File[] existing = catalog.listFiles(file -> file.isDirectory() && styleDirPattern.matcher(file.getName()).matches());

            if (existing != null)
            {
                for (File f : existing)
                {
                    Matcher m = styleDirPattern.matcher(f.getName());
                    if (m.matches())
                    {
                        String ts = m.group(1);
                        LocalDateTime existingTime = LocalDateTime.parse(ts, formatter);
                        LocalDateTime newTime = LocalDateTime.parse(newName.substring(7), formatter);
                        if (!existingTime.isBefore(newTime))
                            throw new IOException("Existing styles directory has equal or newer timestamp: " + f.getName());
                    }
                }
            }

            // Rename "new" to target name
            Files.move(newDir.toPath(), targetDir.toPath(), StandardCopyOption.ATOMIC_MOVE);
            styleDir = targetDir.getCanonicalPath();
        }

        linkManager.init(styleDir);
    }

    public synchronized void close() throws Exception
    {
        styleDir = null;
        linkManager.close();
    }

    public void processHtmlFile(String htmlFilePath) throws Exception
    {
        try
        {
            ParserArchiveOrg parser = new ParserArchiveOrg();
            parser.pageSource = Util.readFileAsString(htmlFilePath);
            parser.parseHtml();

            boolean updated = false;

            // ### style tags
            // ### <style>
            // ### @import url("other.css");
            // ### </style>

            // ### <p style="color: blue; font-weight: bold;"> can have @import?

            //   <link rel="stylesheet" type="text/css" href="https://web-static.archive.org/_static/css/banner-styles.css?v=1B2M2Y8A"> 
            for (Node n : JSOUP.findElements(parser.findHead(), "link"))
            {
                String rel = JSOUP.getAttribute(n, "rel");
                String type = JSOUP.getAttribute(n, "type");
                String href = JSOUP.getAttribute(n, "href");

                if (rel == null || !relContainsStylesheet(rel))
                    continue;

                if (!rel.trim().equalsIgnoreCase("stylesheet"))
                    throw new Exception("Unexpected value of link.rel: " + rel);

                if (href == null)
                    throw new Exception("Unexpected value of link.href: " + null);

                if (type == null || type.trim().equalsIgnoreCase("text/css"))
                    updated |= processStyleLink(JSOUP.asElement(n), href, htmlFilePath);
            }

            if (updated)
            {
                // ### emit html and safe-save
            }
        }
        catch (Exception ex)
        {
            throw new Exception("While processing styles for " + htmlFilePath, ex);
        }
    }

    public boolean relContainsStylesheet(String relValue)
    {
        if (relValue != null)
        {
            String[] tokens = relValue.trim().toLowerCase().split("\\s+");
            for (String token : tokens)
            {
                if ("stylesheet".equals(token))
                    return true;
            }
        }

        return false;
    }

    private boolean processStyleLink(Element el, String href, String htmlFilePath) throws Exception
    {
        if (href.toLowerCase().startsWith("http://") || href.toLowerCase().startsWith("https://"))
        {
            // do process
        }
        else
        {
            String generatedBy = JSOUP.getAttribute(el, "generated-by");
            if (generatedBy != null && generatedBy.equals("ljexport-style-manager"))
                return false;
            throw new Exception("Unexpected link.href: " + href);
        }
        
        // ### linkManager.download
        
        // ### download styles
        // ### check they have no imports
        // ### add new link tags <link rel="stylesheet" href="..." type="text/css" generated-by="ljexport-style-manager">
        // ### change original to <link rel="original-stylesheet" original-href="..." original-type="..."> and delete href and type
        // ### return true
        return false;
    }
    
    /* ================================================================================== */
}
