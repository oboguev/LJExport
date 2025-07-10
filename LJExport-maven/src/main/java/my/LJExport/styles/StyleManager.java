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

import my.LJExport.Config;
import my.LJExport.html.JSOUP;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.FileBackedMap;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.synch.IntraInterprocessLock;
import my.LJExport.styles.StyleProcessor.StyleProcessorAction;

public class StyleManager
{
    private final String styleCatalogDir;

    private String styleDir;
    private LinkDownloader linkDownloader = new LinkDownloader();
    private IntraInterprocessLock styleRepositoryLock;
    private FileBackedMap resolvedCSS = new FileBackedMap(); 
    
    private boolean initialized = false;
    private boolean initializing = false;

    static final String StyleManagerSignature = "ljexport-style-manager";
    static final String GeneratedBy = "ljexport-generated-by";
    static final String SuppressedBy = "ljexport-suppressed-by";
    static final String Original = "ljexport-original-";

    public StyleManager(String styleCatalogDir) throws Exception
    {
        while (styleCatalogDir.endsWith(File.separator))
            styleCatalogDir = Util.stripTail(styleCatalogDir, File.separator);

        File fp = new File(styleCatalogDir).getCanonicalFile();
        styleCatalogDir = fp.getCanonicalPath();
        if (fp.exists() && !fp.isDirectory())
            throw new Exception("Not a directory: " + styleCatalogDir);

        Util.mkdir(styleCatalogDir);
        if (!fp.exists() || !fp.isDirectory())
            throw new Exception("Unable to create directory: " + styleCatalogDir);

        this.styleCatalogDir = styleCatalogDir;
    }

    public String getStyleDir()
    {
        return styleDir;
    }

    public synchronized void init() throws Exception
    {
        if (initializing)
            throw new Exception("Partial initialization");

        if (initialized)
            throw new Exception("Already initialized");

        // close();
        
        try
        {
            initializing = true;
            inner_init();
            initializing = false;
            initialized = true;
        }
        catch (Exception ex)
        {
            close();
            throw ex;
        }
    }
    
    private void inner_init() throws Exception
    {
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

        linkDownloader.init(styleDir);
        resolvedCSS.init(styleDir + File.separator + "resolved-css-map.txt");

        styleRepositoryLock = new IntraInterprocessLock(styleDir + File.separator + "repository.lock");
    }

    public synchronized void close() throws Exception
    {
        styleDir = null;
        linkDownloader.close();
        resolvedCSS.close();

        if (styleRepositoryLock != null)
        {
            styleRepositoryLock.close();
            styleRepositoryLock = null;
        }

        initialized = false;
        initializing = false;
    }

    public void processHtmlFile(String htmlFilePath, StyleProcessorAction action, String htmlPageUrl) throws Exception
    {
        String threadName = Thread.currentThread().getName();

        try
        {
            String rurl = new File(htmlFilePath).getName();
            Thread.currentThread().setName("processing styles " + rurl);

            PageParserDirectBasePassive parser = new PageParserDirectBasePassive();
            parser.pageSource = Util.readFileAsString(htmlFilePath);
            parser.parseHtml();

            boolean updated = false;

            switch (action)
            {
            case TO_LOCAL:
                updated = new StyleActionToLocal(styleDir, linkDownloader, styleRepositoryLock, resolvedCSS)
                        .processHtmlFileToLocalStyles(htmlFilePath, parser, htmlPageUrl);
                break;

            case REVERT:
                updated = new StyleActionRevert().processHtmlFileRevertStylesToRemote(htmlFilePath, parser);
                break;
            }

            if (updated && Config.False) // ###
            {
                String html = JSOUP.emitHtml(parser.pageRoot);
                Util.writeToFileSafe(htmlFilePath, html);
            }
        }
        catch (Exception ex)
        {
            throw new Exception("While processing styles for " + htmlFilePath, ex);
        }
        finally
        {
            Thread.currentThread().setName(threadName);
        }
    }

    public static boolean isHtmlReferenceToLocalStyle(String href)
    {
        if (!href.startsWith("../"))
            return false;

        while (href.startsWith("../"))
            href = href.substring("../".length());

        return href.startsWith("styles/");
    }
}
