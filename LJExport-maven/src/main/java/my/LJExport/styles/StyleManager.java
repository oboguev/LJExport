package my.LJExport.styles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.BadToGood;
import my.LJExport.runtime.ErrorMessageLog;
import my.LJExport.runtime.TxLog;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.util.DownloadSource;
import my.LJExport.runtime.lj.LJExportInformation;
import my.LJExport.runtime.synch.IntraInterprocessLock;
import my.LJExport.runtime.url.UrlSetMatcher;
import my.LJExport.styles.StyleProcessor.StyleProcessorAction;

public class StyleManager
{
    private final String styleCatalogDir;
    private final String fallbackStyleDir;
    private final boolean dryRun;

    private String styleDir;
    private LinkDownloader linkDownloader = new LinkDownloader();
    private IntraInterprocessLock styleRepositoryLock;
    private FileBackedMap resolvedCSS = new FileBackedMap();
    private TxLog txLog;
    private boolean isDownloadedFromWebArchiveOrg = false;
    private DownloadSource downloadSource;
    private UrlSetMatcher dontReparseCss;
    private UrlSetMatcher allowUndownloadaleCss;
    private UrlSetMatcher dontDownloadCss;
    private BadToGood badCssMapper;
    private ConcurrentHashMap<String, Optional<String>> cssCache = new ConcurrentHashMap<>();

    private boolean initialized = false;
    private boolean initializing = false;

    static final String StyleManagerSignature = "ljexport-style-manager";
    static final String GeneratedBy = "ljexport-generated-by";
    static final String SuppressedBy = "ljexport-suppressed-by";
    static final String AlteredBy = "ljexport-style-altered-by";
    static final String Original = "ljexport-original-";

    public StyleManager(String styleCatalogDir) throws Exception
    {
        this(styleCatalogDir, null, false);
    }

    public StyleManager(String styleCatalogDir, String fallbackStyleDir, boolean dryRun) throws Exception
    {
        linkDownloader.setAlwaysAcceptContent(true);
        
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
        this.dryRun = dryRun;

        this.isDownloadedFromWebArchiveOrg = LJExportInformation
                .load()
                .getProperty(LJExportInformation.IsDownloadedFromWebArchiveOrg, "false")
                .equals("true");

        File fpx = new File(fp, "do-not-reparse-css.txt");
        if (fpx.exists())
            dontReparseCss = UrlSetMatcher.loadFile(fpx.getCanonicalPath());
        else
            dontReparseCss = UrlSetMatcher.empty();

        fpx = new File(fp, "allow-undownloadable-css.txt");
        if (fpx.exists())
            allowUndownloadaleCss = UrlSetMatcher.loadFile(fpx.getCanonicalPath());
        else
            allowUndownloadaleCss = UrlSetMatcher.empty();

        fpx = new File(fp, "dont-download-css.txt");
        if (fpx.exists())
            dontDownloadCss = UrlSetMatcher.loadFile(fpx.getCanonicalPath());
        else
            dontDownloadCss = UrlSetMatcher.empty();

        this.fallbackStyleDir = fallbackStyleDir;
        if (fallbackStyleDir != null)
        {
            dontReparseCss = chain(dontReparseCss, this.fallbackStyleDir, "do-not-reparse-css.txt");
            allowUndownloadaleCss = chain(allowUndownloadaleCss, this.fallbackStyleDir, "allow-undownloadable-css.txt");
            dontDownloadCss = chain(dontDownloadCss, this.fallbackStyleDir, "dont-download-css.txt");
        }
    }

    private UrlSetMatcher chain(UrlSetMatcher urlSetMatcher, String dir, String filename) throws Exception
    {
        File fpfall = new File(dir).getCanonicalFile();
        if (!fpfall.exists() || !fpfall.isDirectory())
            throw new Exception("Fallback style directory does not exist: " + dir);

        File fpx = new File(fpfall, filename);
        if (fpx.exists())
        {
            UrlSetMatcher ufall = UrlSetMatcher.loadFile(fpx.getCanonicalPath());
            urlSetMatcher.chain(ufall);
        }

        return urlSetMatcher;
    }

    public String getStyleDir()
    {
        return styleDir;
    }

    public ConcurrentHashMap<String, Optional<String>> getCssCache()
    {
        return cssCache;
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
        downloadSource = new DownloadSource(styleCatalogDir + File.separator + "overrides");

        if (fallbackStyleDir != null)
        {
            File fpfall = new File(fallbackStyleDir).getCanonicalFile();
            if (!fpfall.exists() || !fpfall.isDirectory())
                throw new Exception("Fallback style directory does not exist: " + fallbackStyleDir);

            File fpx = new File(fpfall, "overrides");
            if (fpx.exists() && fpx.isDirectory())
                downloadSource.chain(new DownloadSource(fallbackStyleDir + File.separator + "overrides"));

            fpx = new File(fpfall, "replace-css");
            if (fpx.exists() && fpx.isDirectory())
                badCssMapper = new BadToGood(fpx.getCanonicalPath(), dryRun);
        }

        styleRepositoryLock = new IntraInterprocessLock(styleDir + File.separator + "repository.lock");
        txLog = new TxLog(styleDir + File.separator + "transaction.log");
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

        if (txLog != null)
        {
            txLog.close();
            txLog = null;
        }

        downloadSource = null;

        initialized = false;
        initializing = false;
    }

    public void processHtmlFile(String htmlFilePath, StyleProcessorAction action, String htmlPageUrl, boolean dryRun,
            HtmlFileBatchProcessingContext batchContext, ErrorMessageLog errorMessageLog, PageParserDirectBasePassive parser)
            throws Exception
    {
        if (this.dryRun && !dryRun)
            throw new Exception("Cannot execute wet-run on a dry-run style manager");
        
        String threadName = Thread.currentThread().getName();

        try
        {
            String rurl = new File(htmlFilePath).getName();
            Thread.currentThread().setName("processing styles " + rurl);

            if (parser == null)
            {
                parser = new PageParserDirectBasePassive();
                parser.pageSource = Util.readFileAsString(htmlFilePath);
                parser.parseHtml();
            }

            boolean updated = false;

            switch (action)
            {
            case TO_LOCAL:
                updated = new StyleActionToLocal(this, linkDownloader, styleRepositoryLock, resolvedCSS,
                        txLog, isDownloadedFromWebArchiveOrg, downloadSource, dontReparseCss, allowUndownloadaleCss,
                        dontDownloadCss, badCssMapper, dryRun, errorMessageLog)
                                .processHtmlFileToLocalStyles(htmlFilePath, parser, htmlPageUrl);
                break;

            case REVERT:
                updated = new StyleActionRevert().processHtmlFileRevertStylesToRemote(htmlFilePath, parser);
                break;
            }

            if (updated)
            {
                batchContext.updatedHtmlFiles.incrementAndGet();

                if (!dryRun)
                {
                    String html = JSOUP.emitHtml(parser.pageRoot);
                    Util.writeToFileSafe(htmlFilePath, html);
                    batchContext.savedHtmlFiles.incrementAndGet();
                }
            }
            
            parser = null;  // help GC
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

        return href.startsWith("styles/styles.20");
    }
}
