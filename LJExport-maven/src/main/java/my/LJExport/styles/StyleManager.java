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
import my.LJExport.runtime.Util;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.synch.InterprocessLock;
import my.LJExport.styles.StyleProcessor.StyleProcessorAction;

public class StyleManager
{
    private final String styleCatalogDir;

    private String styleDir;
    private LinkDownloader linkDownloader = new LinkDownloader();
    private InterprocessLock styleRepositoryLock;
    static final String StyleManagerSignature = "ljexport-style-manager";

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

        linkDownloader.init(styleDir);

        styleRepositoryLock = new InterprocessLock(styleDir + File.separator + "repository.lock");
    }

    public synchronized void close() throws Exception
    {
        styleDir = null;
        linkDownloader.close();

        if (styleRepositoryLock != null)
        {
            styleRepositoryLock.close();
            styleRepositoryLock = null;
        }
    }

    public void processHtmlFile(String htmlFilePath, StyleProcessorAction action, String htmlPageUrl) throws Exception
    {
        try
        {
            PageParserDirectBasePassive parser = new PageParserDirectBasePassive();
            parser.pageSource = Util.readFileAsString(htmlFilePath);
            parser.parseHtml();

            boolean updated = false;
            
            switch (action)
            {
            case TO_LOCAL:
                updated = new StyleActionToLocal(styleDir, linkDownloader, styleRepositoryLock).processHtmlFileToLocal(htmlFilePath, parser, htmlPageUrl);
                break;

            case REVERT:
                updated = new StyleActionRevert().processHtmlFileRevert(htmlFilePath, parser);
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
    }
}
