package my.LJExport.styles;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import com.helger.css.ECSSVersion;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.decl.CSSExpression;
import com.helger.css.decl.CSSExpressionMemberTermURI;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSExpressionMember;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.writer.CSSWriter;
import com.helger.css.writer.CSSWriterSettings;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.BadToGood;
import my.LJExport.runtime.ErrorMessageLog;
import my.LJExport.runtime.RandomString;
import my.LJExport.runtime.TxLog;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.util.DownloadSource;
import my.LJExport.runtime.links.util.LinkFilepath;
import my.LJExport.runtime.links.util.RelativeLink;
import my.LJExport.runtime.synch.IntraInterprocessLock;
import my.LJExport.runtime.url.UrlSetMatcher;
import my.LJExport.runtime.url.UrlUtil;
import my.LJExport.test.StyleTest;
import my.WebArchiveOrg.ArchiveOrgUrl;

/*
 * Process HTML file to adjust style links in it from remote to local.
 * Copy remote style resources to a local file system repository as necessary.  
 */
public class StyleActionToLocal
{
    private static final String StyleManagerSignature = StyleManager.StyleManagerSignature;
    private static final String GeneratedBy = StyleManager.GeneratedBy;
    private static final String SuppressedBy = StyleManager.SuppressedBy;
    private static final String AlteredBy = StyleManager.AlteredBy;
    private static final String Original = StyleManager.Original;

    /*
     * Interprocess and inter-thread lock on styles repository.
     */
    private final IntraInterprocessLock styleRepositoryLock;

    /*
     * Downloads remote resources to files in local file system repository. 
     */
    private final LinkDownloader linkDownloader;

    /*
     * Persistent (on-disk) map that stores local CSS style sheets that have already been
     * re-written to resolve external links in them. Links to remote servers within CSS are re-written
     * to become instead links to local files cached in local styles repository. Once such re-write
     * is completed, file is added to resolvedCSS. 
     * 
     * It maps URL (of original remote CSS file) -> local file path (of retrieved URL content) relative to the repository root.
     */
    private final FileBackedMap resolvedCSS;

    /*
     * Transaction log. Helps to identify a state of style repository if a failure (OS crash
     * or power down) happens in the middle of an update operation.
     */
    private final TxLog txLog;

    /*
     * File overrides reader
     */
    private final DownloadSource downloadSource;

    private final UrlSetMatcher dontReparseCss;
    private final UrlSetMatcher allowUndownloadaleCss;
    private final UrlSetMatcher dontDownloadCss;
    private final BadToGood badCssMapper;
    private final StyleManager styleManager;
    private final boolean dryRun;
    private final ErrorMessageLog errorMessageLog;

    /* 
     * List (stack) of URLs of CSS/HTML files with styles being currently re-written,
     * used to detect circular references 
     */
    private final List<URI> inprogress = new ArrayList<>();

    private final boolean isDownloadedFromWebArchiveOrg;
    private String currentHtmlFilePath;

    private final String nl = "\n";

    /*
     * Constructor for StyleActionToLocal processor.
     */
    public StyleActionToLocal(StyleManager styleManager,
            LinkDownloader linkDownloader,
            IntraInterprocessLock styleRepositoryLock,
            FileBackedMap resolvedCSS,
            TxLog txLog,
            boolean isDownloadedFromWebArchiveOrg,
            DownloadSource downloadSource,
            UrlSetMatcher dontReparseCss,
            UrlSetMatcher allowUndownloadaleCss,
            UrlSetMatcher dontDownloadCss,
            BadToGood badCssMapper,
            boolean dryRun,
            ErrorMessageLog errorMessageLog)
    {
        this.linkDownloader = linkDownloader;
        this.styleRepositoryLock = styleRepositoryLock;
        this.resolvedCSS = resolvedCSS;
        this.txLog = txLog;
        this.isDownloadedFromWebArchiveOrg = isDownloadedFromWebArchiveOrg;
        this.downloadSource = downloadSource;
        this.dontReparseCss = dontReparseCss;
        this.allowUndownloadaleCss = allowUndownloadaleCss;
        this.dontDownloadCss = dontDownloadCss;
        this.badCssMapper = badCssMapper;
        this.styleManager = styleManager;
        this.dryRun = dryRun;
        this.errorMessageLog = errorMessageLog;
    }

    /*
     * Main (and only) exposed method:
     * 
     * Resolve HTML page styles from remote to local.
     * Download remote styles from HTTP(S) servers to a local file system repository and adjust style resources 
     * links in the HTML tree to point to a local copy of resources, instead of remote copy.
     * 
     * Arguments:
     * 
     *     @htmlFilePath = full file path of HTML file in a local file system
     * 
     *     @htmlPageUrl = original URL of the page on the server, used as a base URL for retrieving resources.
     * 
     *     @parser.pageRoot = root Document Element of JSOUP tree, of the parsed file at @htmlFilePath 
     * 
     * Returns @true if HTML file tree had been changed during style processing,
     * and the tree needs to be written back to the file.
     * 
     * If no changes were done to HTML tree (e.g. no remote styles), return @false.
     * 
     * In particular, file that had already been processed earlier and contains style references
     * only to local style resources, will result in @false.     
     */
    public boolean processHtmlFileToLocalStyles(String htmlFilePath, PageParserDirectBasePassive parser, String htmlPageUrl)
            throws Exception
    {
        if (htmlPageUrl != null && !Util.isAbsoluteURL(htmlPageUrl))
            throw new IllegalArgumentException("HTML page URL is not absolute: " + htmlPageUrl);

        boolean updated = false;

        /* no in-progress CSS processing yet on this thread */
        this.inprogress.clear();
        this.currentHtmlFilePath = htmlFilePath;

        /*
         * LINK tags
         */
        //   <link rel="stylesheet" type="text/css" href="https://web-static.archive.org/_static/css/banner-styles.css?v=1B2M2Y8A"> 
        for (Node n : JSOUP.findElements(parser.pageRoot, "link"))
        {
            String generated_by = JSOUP.getAttribute(n, GeneratedBy);
            if (generated_by != null && generated_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            String suppressed_by = JSOUP.getAttribute(n, SuppressedBy);
            if (suppressed_by != null && suppressed_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            if (generated_by != null || suppressed_by != null)
                throw newException("Unexpected attributes in LINK tag");

            /* tag not processed yet*/
            String rel = JSOUP.getAttribute(n, "rel");
            String type = JSOUP.getAttribute(n, "type");
            String href = JSOUP.getAttribute(n, "href");

            if (rel == null || !relContainsStylesheet(rel))
                continue;

            if (!rel.trim().equalsIgnoreCase("stylesheet"))
                throw newException("Unexpected value of link.rel: " + rel);

            if (href == null)
                throw newException("Unexpected value of link.href: null");

            if (type == null || type.trim().equalsIgnoreCase("text/css"))
            {
                processHtmlLinkTag(JSOUP.asElement(n), href, htmlFilePath, htmlPageUrl);
                updated = true;
            }
        }

        /*
         * STYLE tags
         */
        for (Node n : JSOUP.findElements(parser.pageRoot, "style"))
        {
            String generated_by = JSOUP.getAttribute(n, GeneratedBy);
            if (generated_by != null && generated_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            String suppressed_by = JSOUP.getAttribute(n, SuppressedBy);
            if (suppressed_by != null && suppressed_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            if (generated_by != null || suppressed_by != null)
                throw newException("Unexpected attributes in LINK tag");

            /* tag not processed yet*/
            String type = JSOUP.getAttribute(n, "type");
            if (type == null || type.trim().equalsIgnoreCase("text/css"))
            {
                updated |= processHtmlStyleTag(JSOUP.asElement(n), htmlFilePath, htmlPageUrl);
            }
        }

        /*
         * STYLE attribute on regular tags
         */
        for (Node n : JSOUP.findElements(parser.pageRoot))
        {
            String style_altered_by = JSOUP.getAttribute(n, AlteredBy);
            if (style_altered_by != null && style_altered_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            if (style_altered_by != null)
                throw newException("Unexpected attribute in a tag");

            /* tag not processed yet*/
            if (JSOUP.getAttribute(n, "style") != null)
            {
                updated |= processHtmlStyleAttribute(JSOUP.asElement(n), htmlFilePath, htmlPageUrl);
            }
        }

        return updated;
    }

    /* ================================================================================== */

    private boolean relContainsStylesheet(String relValue)
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

    /* ================================================================================== */

    /*
     * LINK tag
     * 
     *    <link rel="stylesheet" type="text/css" href="https://web-static.archive.org/_static/css/banner-styles.css?v=1B2M2Y8A"> 
     */
    private void processHtmlLinkTag(Element elLink, String hrefComposite, String htmlFilePath, String baseUrl) throws Exception
    {
        List<String> hrefList = CssHelper.cssLinks(hrefComposite);
        Element elInsertAfter = elLink;

        for (int k = 0; k < hrefList.size(); k++)
        {
            String href = hrefList.get(k);
            AtomicReference<Element> createdElement = new AtomicReference<>();
            processHtmlLinkTag(elLink, href, htmlFilePath, baseUrl, elInsertAfter, k == hrefList.size() - 1, createdElement);
            elInsertAfter = createdElement.get();
        }
    }

    private void processHtmlLinkTag(Element elLink, String href, String htmlFilePath, String baseUrl, Element elInsertAfter,
            boolean updateElLink, AtomicReference<Element> createdElement) throws Exception
    {
        if (!Util.isAbsoluteURL(href) && this.isDownloadedFromWebArchiveOrg && ArchiveOrgUrl.isArchiveOrgUriPath(href))
        {
            // <link rel="stylesheet" type="text/css" href="/web/20160426180858cs_/http://nationalism.org/forum/07/general1.css" title="general">
            if (!href.startsWith("/"))
                href = "/" + href;
            href = ArchiveOrgUrl.ARCHIVE_SERVER + href;
        }

        if (baseUrl == null && !Util.isAbsoluteURL(href))
        {
            throw newException("Unexpected link.href: " + href);
        }
        else if (!Util.isAbsoluteURL(href))
        {
            // later may resolve it against baseUrl
            throw newException("Unexpected link.href: " + href);
        }

        String cssFileURL = Util.resolveURL(baseUrl, href);

        String newref = resolveCssFile(cssFileURL);
        if (newref != null)
        {
            /* newref is *not* url-encoded and is exact file name in file system */
            String cssFilePath = linkDownloader.rel2abs(newref);
            String relpath = RelativeLink.fileRelativeLink(cssFilePath, htmlFilePath,
                    Config.DownloadRoot + File.separator + Config.User);

            updateLinkElement(elLink, urlEncodeLink(relpath), elInsertAfter, updateElLink, createdElement);
        }
        else if (allowUndownloadaleCssResource(cssFileURL))
        {
            updateLinkElement(elLink, urlEncodeLink(cssFileURL), elInsertAfter, updateElLink, createdElement);
        }
        else
        {
            updateLinkElement(elLink, cssFileURL, elInsertAfter, updateElLink, createdElement);
        }
    }

    private void updateLinkElement(Element elLink, String newlink, Element elInsertAfter, boolean updateElLink,
            AtomicReference<Element> createdElement) throws Exception
    {
        if (JSOUP.getAttribute(elLink, Original + "rel") != null ||
                JSOUP.getAttribute(elLink, Original + "type") != null ||
                JSOUP.getAttribute(elLink, Original + "href") != null)
        {
            throw newException("LINK tag contains unexpected original-xxx attributes");
        }

        String original_rel = JSOUP.getAttribute(elLink, "rel");
        String original_type = JSOUP.getAttribute(elLink, "type");
        String original_href = JSOUP.getAttribute(elLink, "href");

        Element elx = elLink.clone().empty(); // shallow copy (preserves attributes)
        createdElement.set(elx);
        elInsertAfter.after(elx); // insert into the tree

        JSOUP.deleteAttribute(elx, "rel");
        JSOUP.deleteAttribute(elx, "type");
        JSOUP.deleteAttribute(elx, "href");

        JSOUP.setAttribute(elx, "rel", "stylesheet");
        if (original_type != null)
            JSOUP.setAttribute(elx, "type", original_type); // "text/css" or omitted
        JSOUP.setAttribute(elx, "href", newlink);
        JSOUP.setAttribute(elx, GeneratedBy, StyleManagerSignature);

        if (updateElLink)
        {
            JSOUP.deleteAttribute(elLink, "rel");
            JSOUP.deleteAttribute(elLink, "type");
            JSOUP.deleteAttribute(elLink, "href");
            JSOUP.setAttribute(elLink, "rel", Original + "stylesheet");
            JSOUP.setAttribute(elLink, Original + "rel", original_rel);
            if (original_type != null)
                JSOUP.setAttribute(elLink, Original + "type", original_type);
            JSOUP.setAttribute(elLink, Original + "href", original_href);
            JSOUP.setAttribute(elLink, SuppressedBy, StyleManagerSignature);
        }
    }

    /*
     * Download on-demand CSS and the whole chain of resources references by it into a local file system repository.
     * Downloaded resources may include other CSS files referenced via @Import or image/font/other passive files referenced via url: (...).
     * References may be cascaded and cause the whole chain of files to be loaded from a remote server into a local repository.
     * 
     * When downloading CSS files (including CSS file directly referenced as the argument and cascaded CSS files @Import-ed as dependencies),  
     * re-write links in the downloaded CSS files so they reflect their relationship within local file system repository.
     * If links were relative on the remote server, they most often will remain the same in local copies. 
     * However if links were absolute, they will be changed to become relative.
     * 
     * Returns path to re-written local copy of CSS file, relative to the root of local repository.
     * Path is *not* url-encoded and is exact file name in file system. 
     * Returns null if CSS file cannot be downloaded and original link should be left intact.
     */
    private String resolveCssFile(String cssFileURL) throws Exception
    {
        /* 
         * See if CSS file was already resolved earlier.
         * Treat HTTP and HTTPS versions of the url as pointing to the same resource. 
         */
        String newref = resolvedCSS_getAnyUrlProtocol(cssFileURL);
        if (newref != null)
            return linkDownloader.abs2rel(newref);

        // lock repository to avoid deadlocks while processing A.css and B.css referencing each other
        styleRepositoryLock.lockExclusive();
        try
        {
            txLog.open();

            if (!txLog.isEmpty())
            {
                StringBuilder sb = new StringBuilder();
                sb.append("Operation on styles repository was aborted in the middle of a critical phase." + nl);
                sb.append("Please check transaction log and take corrective action." + nl);
                sb.append("Log file: " + txLog.getPath());
                Util.err(sb.toString());
                throw newException(sb.toString());
            }

            /*
             * Re-check if this CSS had already been adjusted on disk
             * while we were waiting for repo lock
             */
            newref = resolvedCSS_getAnyUrlProtocol(cssFileURL);
            if (newref != null)
                return linkDownloader.abs2rel(newref);

            String download_href = cssFileURL;
            String naming_href = cssFileURL;

            if (ArchiveOrgUrl.isArchiveOrgUrl(cssFileURL))
            {
                naming_href = ArchiveOrgUrl.extractArchivedUrlPart(cssFileURL);
                if (naming_href == null)
                    naming_href = download_href;
            }

            /*
             * Download CSS file to local repository (residing in local file system).
             * Returns path relative to repository root, with url-encoded path components.
             */
            newref = linkDownloader.download(false, naming_href, download_href, null, "", downloadSource);
            if (newref == null && !naming_href.equals(download_href))
            {
                download_href = ArchiveOrgUrl.getLatestCaptureUrl(naming_href);
                newref = linkDownloader.download(false, naming_href, download_href, null, "", downloadSource);
                // TODO: add alias download_href if does not exist -> newref (decodePathCopmonents first)
                // TODO: add alias cssFileURL if does not exist -> newref (decodePathCopmonents first)
            }

            if (newref == null)
            {
                if (allowUndownloadaleCss != null && allowUndownloadaleCss.matchOR(download_href, naming_href))
                    return null;

                throw newException("Failed to download style URL: " + cssFileURL);
            }

            newref = LinkFilepath.decodePathComponents(newref);

            /* convert to absolute path*/
            String cssFilePath = linkDownloader.rel2abs(newref);

            String beforeCss = Util.readFileAsString(cssFilePath);
            String modifiedCss = resolveCssDependencies(beforeCss, cssFilePath, cssFileURL, false);
            if (modifiedCss != null)
            {
                String beforeFilePath = cssFilePath + "." + RandomString.rs(5) + "~before";
                Util.writeToFileVerySafe(beforeFilePath, beforeCss);
                txLog.writeLine(String.format("Saved file [%s] to [%s]", cssFilePath, beforeFilePath));

                txLog.writeLine(
                        String.format("About to overwrite file with edited CSS content, file path: [%s]", cssFilePath));
                Util.writeToFileVerySafe(cssFilePath, modifiedCss);
                txLog.writeLine(String.format("Overwrote file with edited CSS content, file path: [%s]", cssFilePath));

                txLog.writeLine(String.format("About to write a mapping to map file: %s%s  from: %s%s  to:   %s",
                        resolvedCSS.getPath(),
                        System.lineSeparator(),
                        naming_href,
                        System.lineSeparator(),
                        newref));
                resolvedCSS.put(naming_href, linkDownloader.rel2abs(newref));

                if (download_href.equals(naming_href))
                {
                    txLog.writeLine(String.format("Wrote a mapping to %s", resolvedCSS.getPath()));
                }
                else
                {
                    txLog.writeLine(String.format("About to write another mapping to map file: %s%s  from: %s%s  to:   %s",
                            resolvedCSS.getPath(),
                            System.lineSeparator(),
                            download_href,
                            System.lineSeparator(),
                            newref));
                    resolvedCSS.put(download_href, linkDownloader.rel2abs(newref));
                    txLog.writeLine(String.format("Wrote two mappings to %s", resolvedCSS.getPath()));
                }

                txLog.clear();
                new File(beforeFilePath).delete();
            }
            else
            {
                resolvedCSS.put(naming_href, linkDownloader.rel2abs(newref));
                if (!download_href.equals(naming_href))
                    resolvedCSS.put(download_href, linkDownloader.rel2abs(newref));
            }
        }
        finally
        {
            txLog.close();
            styleRepositoryLock.unlock();
        }

        return newref;
    }

    /*
     * Encode each separate path component of the path 
     */
    private String urlEncodeLink(String path) throws Exception
    {
        if (path == null || path.isEmpty())
            return path;

        String lc = path.toLowerCase();
        
        if (lc.startsWith("http://") || lc.startsWith("https://"))
            return urlEncodeRemoteAbsoluteLink(path);
        else
            return urlEncodeLocalRelativeLink(path);
    }

    private String urlEncodeLocalRelativeLink(String relativeFilePath) throws Exception
    {
        StringBuilder encoded = new StringBuilder();
        String[] components = relativeFilePath.split("/");

        for (int i = 0; i < components.length; i++)
        {
            String comp = components[i];
            if (i > 0)
                encoded.append('/');

            // Preserve "." and ".." exactly
            if (comp.equals(".") || comp.equals(".."))
            {
                encoded.append(comp);
            }
            else
            {
                // Encode and manually replace '+' with '%20'
                String encodedComp = UrlUtil.encodeSegment(comp);
                encoded.append(encodedComp);
            }
        }

        return encoded.toString();
    }

    private String urlEncodeRemoteAbsoluteLink(String path) throws Exception
    {
        URI uri = new URI(path);

        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        String[] segments = uri.getRawPath() != null ? uri.getRawPath().split("/") : new String[0];

        StringBuilder encodedPath = new StringBuilder();
        for (int i = 0; i < segments.length; i++)
        {
            if (i > 0) encodedPath.append('/');

            String seg = segments[i];
            if (seg.equals(".") || seg.equals(".."))
                encodedPath.append(seg);
            else
                encodedPath.append(UrlUtil.encodeSegment(seg));
        }

        StringBuilder result = new StringBuilder();
        result.append(scheme).append("://").append(authority);
        if (encodedPath.length() > 0)
            result.append('/').append(encodedPath);

        // Append query and fragment as-is
        if (uri.getRawQuery() != null)
            result.append('?').append(uri.getRawQuery());
        if (uri.getRawFragment() != null)
            result.append('#').append(uri.getRawFragment());

        return result.toString();        
    }
    
    /* ================================================================================== */

    /*
     * cssText -- CSS content/text to process
     * hostingFilePath -- path of either CSS file or HTML file containing cssText, cached in a local file system
     * hostingFileURL -- original URL of hosting CSS/HTML file, used to resolve relative links in cssText     
     */
    private String resolveCssDependencies(String cssText, String hostingFilePath, String hostingFileURL, boolean isLocalHtmlFile)
            throws Exception
    {
        URI uri = null;

        /*
         * Check for circular references in stylesheets being re-writtem
         */
        try
        {
            if (isLocalHtmlFile && hostingFileURL == null)
                uri = new URI("file://" + hostingFilePath.replace(File.separator, "/"));
            else
                uri = new URI(hostingFileURL);
        }
        catch (Exception ex)
        {
            throw ex;
        }

        for (URI xuri : inprogress)
        {
            if (isSameURL(uri, xuri))
            {
                displayCssCyclicError(inprogress, uri);
                /*
                 * Should later add actual handling of circular references.
                 * For now just abort.
                 * 
                 * Future design:
                 *   - mark current CSS as provisional and return its path
                 *   - mark all CSS'es that reference provisional CSSes as provisional
                 *   - do not write provisional CSS to disk, store it in memory
                 *   - commit all provisional CSS to disk at the end of whole operation 
                 * 
                 */
                throw newException("Circular reference in style sheets");
            }
        }

        inprogress.add(uri);
        try
        {
            displayCssProgress(inprogress);
            return do_resolveCssDependencies(cssText, hostingFilePath, hostingFileURL, isLocalHtmlFile);
        }
        finally
        {
            inprogress.remove(inprogress.size() - 1);

        }
    }

    private String do_resolveCssDependencies(final String cssText, String hostingFilePath, String hostingFileURL,
            boolean isLocalHtmlFile) throws Exception
    {
        if (cssHasNoExternalReferences(cssText))
            return null;

        if (dontReparseCss != null && hostingFileURL != null)
        {
            if (dontReparseCss.match(hostingFileURL))
                return null;

            if (ArchiveOrgUrl.isArchiveOrgUrl(hostingFileURL)
                    && dontReparseCss.match(ArchiveOrgUrl.extractArchivedUrlPart(hostingFileURL)))
                return null;
        }

        String cacheKey = null;

        trace("---------------------------------------");
        trace(String.format("001 - starting for file %s url %s", hostingFilePath, hostingFileURL));

        if (isLocalHtmlFile || isLocalHtmlFile(hostingFilePath))
        {
            /*
             * Try to translate via cache
             */
            cacheKey = String.format("File Depth = %d\n%s", Util.filePathDepth(hostingFilePath), cssText);
            trace(String.format("002.a - genereated cache key"));
            Optional<String> result = styleManager.getCssCache().get(cacheKey);
            if (result != null)
            {
                trace(String.format("003.a - found in cache, returning"));
                if (result.isPresent())
                    return result.get();
                else
                    return null;
            }
            else
            {
                trace(String.format("003.x - not found in cache"));
            }
        }
        else
        {
            trace(String.format("002.x - did not genereate cache key"));
        }

        String cssGoodText = null;

        CascadingStyleSheet css = null;

        if (badCssMapper != null)
        {
            cssGoodText = badCssMapper.good(cssText);
            if (cssGoodText != null)
                css = CSSReader.readFromString(cssGoodText, ECSSVersion.CSS30);
            else
                css = CSSReader.readFromString(cssText, ECSSVersion.CSS30);
        }
        else
        {
            css = CSSReader.readFromString(cssText, ECSSVersion.CSS30);
        }

        if (css == null)
        {
            String where = "<unknown>";

            if (hostingFilePath != null && hostingFileURL != null)
            {
                where = hostingFilePath + " =  " + hostingFileURL;
            }
            else if (hostingFilePath != null)
            {
                where = hostingFilePath;

            }
            else if (hostingFileURL != null)
            {
                where = hostingFileURL;
            }

            Util.err("Malformed CSS content in " + where);

            if (hostingFileURL != null && ArchiveOrgUrl.isArchiveOrgUrl(hostingFileURL))
            {
                Util.err("    archives " + ArchiveOrgUrl.extractArchivedUrlPart(hostingFileURL));
            }

            boolean crash = true;

            if (!isLocalHtmlFile && !isLocalHtmlFile(hostingFilePath))
                crash = true;

            if (!cssHasNoExternalImportReferences(cssText))
                crash = true;

            if (crash)
            {
                trace(String.format("004.a - failed to parse, crash exception"));

                if ((isLocalHtmlFile || isLocalHtmlFile(hostingFilePath)) && dryRun && errorMessageLog != null
                        && badCssMapper != null && cssGoodText == null)
                {
                    synchronized (badCssMapper)
                    {
                        String path = badCssMapper.nextFilePath("html-css", "bad", "tofix");
                        Util.writeToFileSafe(path, cssText);
                        StringBuilder sb = new StringBuilder();
                        sb.append("Malformed CSS content in " + where + nl);
                        if (hostingFileURL != null && ArchiveOrgUrl.isArchiveOrgUrl(hostingFileURL))
                            sb.append("    archives " + ArchiveOrgUrl.extractArchivedUrlPart(hostingFileURL) + nl);
                        sb.append("Saved to " + path);
                        errorMessageLog.add(sb.toString());
                        badCssMapper.addMapping(cssText, "");
                    }

                    return null;
                }

                throw newException("Failed to parse CSS content in " + where);
            }
            else
            {
                trace(String.format("004.x - failed to parse, return null"));
                return null;
            }
        }

        boolean updated = false;

        /*
         * check style for imports:
         * 
         *      @import url("https://www.dreamwidth.org/css/base.css");
         *      background-image: url("https://www.dreamwidth.org/images/header-bg.png");
         */

        // Walk @import rules
        for (CSSImportRule importRule : css.getAllImportRules())
        {
            String url = importRule.getLocationString();
            String newUrl = downloadAndRelinkCssFile(url, hostingFileURL, hostingFilePath);
            if (newUrl != null)
            {
                importRule.setLocationString(urlEncodeLink(newUrl));
                updated = true;
            }
        }

        // Walk all style rules
        for (CSSStyleRule rule : css.getAllStyleRules())
        {
            for (CSSDeclaration declaration : rule.getAllDeclarations())
            {
                CSSExpression expr = declaration.getExpression();
                if (expr != null)
                {
                    boolean changed = false;

                    for (ICSSExpressionMember member : expr.getAllMembers())
                    {
                        if (member instanceof CSSExpressionMemberTermURI)
                        {
                            CSSExpressionMemberTermURI uriTerm = (CSSExpressionMemberTermURI) member;
                            String originalUrl = uriTerm.getURIString();
                            /* this cannot be CSS but only image or similar file, hence no recursion */
                            String newUrl = downloadAndRelinkPassiveFile(originalUrl, hostingFileURL, hostingFilePath, false);

                            if (newUrl != null && !newUrl.equals(originalUrl))
                            {
                                uriTerm.setURIString(urlEncodeLink(newUrl));
                                changed = true;
                                updated = true;
                            }
                        }
                    }

                    if (changed && Util.False)
                    {
                        // Optionally log which declaration was updated
                        System.out.println("Updated declaration: " + declaration.getProperty());
                    }
                }
            }
        }

        if (updated)
        {
            CSSWriter writer = new CSSWriter(ECSSVersion.CSS30, false);
            writer.setWriteHeaderText(false);
            String modifiedCss = writer.getCSSAsString(css);

            trace(String.format("005.a - finishing with update"));

            if (cacheKey != null)
            {
                if (styleManager.getCssCache().get(cacheKey) != null)
                {
                    trace(String.format("006.a - modified: already had in cache"));
                    Util.noop();
                }
                else
                {
                    trace(String.format("006.a - modified: not in cache yet"));
                }

                trace(String.format("007.a - modified: store processing"));
                styleManager.getCssCache().put(cacheKey, Optional.of(modifiedCss));
            }
            else
            {
                trace(String.format("007.b - modified: no cache key"));
            }

            return modifiedCss;
        }
        else if (cssGoodText != null)
        {
            trace(String.format("005.b - finishing with verbatim replacement of bad -> good text"));
            return cssGoodText;
        }
        else
        {
            trace(String.format("005.x - finishing with no update"));

            if (cacheKey != null)
            {
                if (styleManager.getCssCache().get(cacheKey) != null)
                {
                    trace(String.format("006.c - unmodified: already had in cache"));
                    Util.noop();
                }
                else
                {
                    trace(String.format("006.d - unmodified: not in cache yet"));
                }

                styleManager.getCssCache().put(cacheKey, Optional.empty());
                trace(String.format("007.c - unmodified: store as empty"));
            }
            else
            {
                trace(String.format("007.d - unmodified: no cache key"));
            }

            return null;
        }
    }

    private boolean isSameURL(URI uri1, URI uri2)
    {
        // Compare scheme and host case-insensitively
        if (Util.False && !equalsIgnoreCase(uri1.getScheme(), uri2.getScheme()))
            return false;

        if (!equalsIgnoreCase(uri1.getHost(), uri2.getHost()))
            return false;

        // Compare port (default ports need normalization if desired)
        if (Util.False && uri1.getPort() != uri2.getPort())
            return false;

        // Compare path, query, and fragment case-sensitively
        if (!Objects.equals(uri1.getPath(), uri2.getPath()))
            return false;

        if (!Objects.equals(uri1.getQuery(), uri2.getQuery()))
            return false;

        if (!Objects.equals(uri1.getFragment(), uri2.getFragment()))
            return false;

        return true;
    }

    private boolean equalsIgnoreCase(String a, String b)
    {
        return (a == null && b == null) || (a != null && a.equalsIgnoreCase(b));
    }

    /*
     * Make sure passive data file (image/font/etc. ) identified by (baseUrl + originalUrl)
     * is downloaded and resides in the local repository (kept in a local file system).
     * 
     * Return relative local file path to the downloaded resource when referenced from relativeToFilePath.
     * Path is *not* url-encoded and is exact file name in file system.
     * 
     * Will return null if file is not downloadable and is allowed to be non-downloadable. 
     * 
     * Since this is a passive file referencing no other (external) resources and hence does not require 
     * downloading other referenced resources and re-writing inner links to other resources embedded in the 
     * downloaded file, we do not need to enter it into resolvedCSS. Because we just download and keep
     * this file as a binary BLOB, and do not modify it -- unlike we do for CSS files.
     */
    private String downloadAndRelinkPassiveFile(String originalUrl, String baseUrl, String relativeToFilePath,
            boolean forHtmlTagInlineStyle) throws Exception
    {
        if (originalUrl == null)
            throw newException("Resoruce URL is missing (null)");

        originalUrl = originalUrl.trim();

        if (originalUrl.isEmpty())
        {
            String msg = "Resource URL is blank in file: " + relativeToFilePath;

            if (forHtmlTagInlineStyle)
            {
                Util.err(msg);
                if (errorMessageLog != null)
                    errorMessageLog.add(msg);
                return null;
            }
            else
            {
                throw newException(msg);
            }
        }

        originalUrl = fixBadPasiveFileLinkUrl(originalUrl);
        originalUrl = originalUrl.trim();

        if (originalUrl.isEmpty())
        {
            String msg = "Resource URL is blank in file: " + relativeToFilePath;

            if (forHtmlTagInlineStyle)
            {
                Util.err(msg);
                if (errorMessageLog != null)
                    errorMessageLog.add(msg);
                return null;
            }
            else
            {
                throw newException(msg);
            }
        }

        /* embedded data URL */
        if (originalUrl.toLowerCase().startsWith("data:"))
            return null;

        String absoluteUrl = Util.resolveURL(baseUrl, originalUrl);

        /* embedded data URL */
        String lc = absoluteUrl.toLowerCase();
        if (lc.startsWith("data:"))
            return null;

        if (Config.isLiveJournal() && lc.equals("/img/userinfo_v3.svg?v="))
            absoluteUrl = "https://l-stat.livejournal.net" + absoluteUrl;

        /*
         * Resource is expected to be remote
         */
        if (!lc.startsWith("http://") && !lc.startsWith("https://"))
        {
            if (dontDownloadCss != null && dontDownloadCss.matchLocal(absoluteUrl))
            {
                return null;
            }
            else if (forHtmlTagInlineStyle)
            {
                String msg = "Referenced passive style resource is not remote, in file: " + relativeToFilePath + ", link was: "
                        + absoluteUrl;
                Util.err(msg);
                if (errorMessageLog != null)
                    errorMessageLog.add(msg);
                return null;
            }
            else
            {
                throw newException("Referenced style resource is not remote: " + absoluteUrl);
            }
        }

        String download_href = absoluteUrl;
        String naming_href = absoluteUrl;

        if (ArchiveOrgUrl.isArchiveOrgUrl(absoluteUrl))
        {
            naming_href = ArchiveOrgUrl.extractArchivedUrlPart(absoluteUrl);
            if (naming_href == null)
                naming_href = download_href;
        }

        if (dontDownloadCss != null && dontDownloadCss.matchOR(download_href, naming_href))
            return absoluteUrl;

        /*
         * Download file to local repository (residing in local file system).
         * Returns path relative to repository root, with url-encoded path components.
         */
        boolean image = FileTypeDetector.isImagePath(naming_href);
        String rel = linkDownloader.download(image, naming_href, download_href, null, "", downloadSource);
        if (rel == null && !naming_href.equals(download_href))
        {
            download_href = ArchiveOrgUrl.getLatestCaptureUrl(naming_href);
            rel = linkDownloader.download(image, naming_href, download_href, null, "", downloadSource);
            // TODO: add alias download_href if does not exist -> rel (decodePathCopmonents first)
            // TODO: add alias absoluteUrl if does not exist -> rel (decodePathCopmonents first)
        }

        if (rel == null)
        {
            if (allowUndownloadaleCss != null && allowUndownloadaleCss.matchOR(download_href, naming_href))
            {
                return absoluteUrl;
            }
            else if (allowUndownloadaleCssResource(absoluteUrl))
            {
                return absoluteUrl;
            }
            else
            {
                String fn = linkDownloader.adviseFileName(naming_href);
                fn = linkDownloader.abs2rel(fn);

                StringBuilder sb = new StringBuilder();

                sb.append("Unable to download style passive resource:" + nl);
                sb.append("    URL: " + absoluteUrl + nl);
                sb.append("    FN: " + fn + nl);
                if (!File.separator.equals("/"))
                    sb.append("    FN: " + fn.replace("/", File.separator));

                Util.err("--------------------------------------------------------------------");
                Util.err(sb.toString());
                Util.err("--------------------------------------------------------------------");

                if (isLocalHtmlFile(relativeToFilePath) && dryRun && errorMessageLog != null)
                {
                    /* debug-time, to collect them all, use only for DryRun */
                    errorMessageLog.add(sb.toString());
                    return absoluteUrl;
                }

                throw newException("Unable to download style passive resource: " + absoluteUrl);
            }
        }

        /* Full local file path name */
        rel = LinkFilepath.decodePathComponents(rel);
        String abs = linkDownloader.rel2abs(rel);

        /* File path relative to the referencing resource (both must reside within DownloadRoot)  */
        rel = RelativeLink.fileRelativeLink(abs, relativeToFilePath, Config.DownloadRoot + File.separator + Config.User);

        return rel;
    }

    private boolean allowUndownloadaleCssResource(String absoluteUrl) throws Exception
    {
        /*
         * When downloading live (non-dry) from LiveJournal, 
         * allow non-downloadable CSS resources from sites other than LiveJournal
         * and also from imgprx.livejournal.net.
         */
        if (Config.isLiveJournal() && !dryRun)
        {
            URI uri = new URI(absoluteUrl);
            String host = uri.getHost().toLowerCase();
            
            boolean result = ((Supplier<Boolean>) () -> {
                
                if (host.equals("imgprx.livejournal.net"))
                    return true;

                if (host.equals("livejournal.com") || host.equals("livejournal.net"))
                    return false;

                if (host.startsWith(".livejournal.com") || host.startsWith(".livejournal.net"))
                    return false;

                return true;
                
            }).get();
            
            if (result)
            {
                String msg = "Leaving unarchived remote link to undownloadable CSS style resource: " + absoluteUrl;
                Util.err(msg);
                if (errorMessageLog != null)
                    errorMessageLog.add(msg);                
            }

            return result;
        }
        else
        {
            return false;
        }
    }

    /*
     * Make sure CSS file (identified by (baseUrl + originalUrl)
     * is downloaded and resides in the local repository (kept in a local file system).
     * 
     * Also recursively download depended resources.
     * Rewrite links in the CSS file to them as necessary, to point to local copies.
     *  
     * Return relative local file path to the downloaded resource when referenced from relativeToFilePath.
     * Path is *not* url-encoded and is exact file name in file system.
     * Return null if CSS file cannot be downloaded and original remote link to it should be left intact. 
     */
    private String downloadAndRelinkCssFile(String originalUrl, String baseUrl, String relativeToFilePath) throws Exception
    {
        if (originalUrl == null || originalUrl.trim().isEmpty())
            throw newException("Resuouce URL is missing or blank");

        String absoluteCssFileUrl = Util.resolveURL(baseUrl, originalUrl);

        /*
         * Resource is expected to be remote
         */
        String lc = absoluteCssFileUrl.toLowerCase();
        if (!lc.startsWith("http://") && !lc.startsWith("https://"))
            throw newException("Referenced style resource is not remote: " + absoluteCssFileUrl);

        String newref = resolveCssFile(absoluteCssFileUrl);
        if (newref != null)
        {
            String cssFilePath = linkDownloader.rel2abs(newref);
            String relpath = RelativeLink.fileRelativeLink(cssFilePath, relativeToFilePath,
                    Config.DownloadRoot + File.separator + Config.User);

            return relpath;
        }
        else if (allowUndownloadaleCssResource(absoluteCssFileUrl))
        {
            return absoluteCssFileUrl;
        }
        else
        {
            return null;
        }
    }

    private String fixBadPasiveFileLinkUrl(String originalUrl) throws Exception
    {
        // &quot;http://static.gallery.ru/i/pleasewait.gif&quot;
        if (originalUrl.startsWith("&quot;") && originalUrl.endsWith("&quot;") && !originalUrl.equals("&quot;"))
        {
            originalUrl = Util.stripStart(originalUrl, "&quot;");
            originalUrl = Util.stripTail(originalUrl, "&quot;");
        }

        // &apos;http://livejournalist.com/img/eye.gif?id=98561de2a6bb836f356d3c3e6757809f&s=1&n=865&apos;
        if (originalUrl.startsWith("&apos;") && originalUrl.endsWith("&apos;") && !originalUrl.equals("&apos;"))
        {
            originalUrl = Util.stripStart(originalUrl, "&apos;");
            originalUrl = Util.stripTail(originalUrl, "&apos;");
        }

        switch (originalUrl)
        {
        case "https:l-stat.livejournal.netimgcommunity_v3.svg?v=43924":
            return "https://l-stat.livejournal.net/img/community_v3.svg?v=43924";

        case "https:l-stat.livejournal.netimgpreloaderpreloader-disc-blue-white.gif?v=39255":
            return "https://l-stat.livejournal.net/img/preloaderpreloader-disc-blue-white.gif?v=39255";

        case "https:l-stat.livejournal.netimgiconsremove.png?v=37651":
            return "https://l-stat.livejournal.net/img/iconsremove.png?v=37651";

        case "https:l-stat.livejournal.netimgpreloaderpreloader-blue-blue.gif?v=16423":
            return "https://l-stat.livejournal.net/img/preloaderpreloader-blue-blue.gif?v=16423";

        default:
            break;
        }

        if (originalUrl.startsWith("https:l-stat.livejournal.netimg"))
        {
            String fixedUrl = "https://l-stat.livejournal.net/img/"
                    + Util.stripStart(originalUrl, "https:l-stat.livejournal.netimg");

            if (errorMessageLog != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Bad URL:   %s%s", originalUrl, nl));
                sb.append(String.format("Fixed to:  %s%s", fixedUrl, nl));
                errorMessageLog.add(sb.toString());
            }

            originalUrl = fixedUrl;
        }

        if (originalUrl.startsWith("//"))
        {
            String fixedUrl = "https:" + originalUrl;

            if (errorMessageLog != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Bad URL:         %s%s", originalUrl, nl));
                sb.append(String.format("Fixed to:  %s%s", fixedUrl, nl));
                errorMessageLog.add(sb.toString());
            }

            originalUrl = fixedUrl;
        }

        return originalUrl;
    }

    /* ================================================================================== */

    /*
     * STYLE tags
     * 
     * <style>
     *     @import url("other.css");
     * </style>
     */
    private boolean processHtmlStyleTag(Element elStyle, String htmlFilePath, String htmlPageUrl) throws Exception
    {
        boolean updated = false;

        String cssText = elStyle.data();
        String modifiedCss = resolveCssDependencies(cssText, htmlFilePath, htmlPageUrl, true);
        if (modifiedCss != null)
        {
            updateStyleElement(elStyle, modifiedCss);
            updated = true;
        }

        return updated;
    }

    private void updateStyleElement(Element elStyle, String modifiedCss) throws Exception
    {
        if (JSOUP.getAttribute(elStyle, Original + "type") != null)
            throw newException("STYLE tag contains unexpected original-type attribute");

        String original_type = JSOUP.getAttribute(elStyle, "type");

        Element elx = elStyle.clone().empty(); // shallow copy (preserves attributes)
        JSOUP.setAttribute(elx, GeneratedBy, StyleManagerSignature);
        elx.appendChild(new DataNode(modifiedCss));
        elStyle.after(elx); // insert into the tree

        if (original_type != null)
            JSOUP.setAttribute(elStyle, Original + "type", original_type);
        JSOUP.setAttribute(elStyle, SuppressedBy, StyleManagerSignature);
        JSOUP.deleteAttribute(elStyle, "type");
        JSOUP.setAttribute(elStyle, "type", "text/" + StyleManagerSignature + "-suppressed-css");
    }

    /* ================================================================================== */

    /*
     * Process style external url references inside inline style attribute on tags, such as: 
     * 
     *   <p style="background-image: url('images/bg.jpg'); color: blue;">
     *   <li style="list-style-image: url('icons/bullet.png');">Item</li>
     *   <div style="cursor: url('cursors/pointer.cur'), pointer;">Hover here</div>
     */
    private boolean processHtmlStyleAttribute(Element el, String htmlFilePath, String htmlPageUrl) throws Exception
    {
        boolean updated = false;

        if (JSOUP.getAttribute(el, Original + "style") != null)
            throw newException("Tag contains unexpected original-style attribute");

        String original_style = JSOUP.getAttribute(el, "style");
        if (original_style == null)
            return false;

        String modified_style = resolveInlineStyleDependencies(original_style, htmlFilePath, htmlPageUrl);
        if (modified_style != null)
        {
            JSOUP.setAttribute(el, Original + "style", original_style);
            JSOUP.updateAttribute(el, "style", modified_style);
            JSOUP.setAttribute(el, AlteredBy, StyleManagerSignature);
            updated = true;
        }

        return updated;
    }

    /**
     * Rewrite url(...) references that live in an inline style attribute such as
     * <p style="background-image:url('bg.jpg')">
     * .
     *
     * @param inlineStyleText
     *            the raw value of the style attribute
     * @param hostingFilePath
     *            absolute path on disk of the HTML file that contains this style attribute
     * @param hostingFileURL
     *            equivalent file://… URL of the same HTML file (used by downloadAndRelink)
     * @return the modified style string, or {@code null} if nothing changed
     *
     * @throws Exception
     *             if the declaration list cannot be parsed
     */
    private String resolveInlineStyleDependencies(String inlineStyleText,
            String hostingFilePath,
            String hostingFileURL) throws Exception
    {
        if (inlineStyleHasNoExternalReferences(inlineStyleText))
            return null;

        // 1. Parse “prop: value; …” into a declaration list.
        CSSDeclarationList declList = null;

        try
        {
            declList = CSSReaderDeclarationList.readFromString(inlineStyleText, ECSSVersion.CSS30);
        }
        catch (Exception ex)
        {
            Util.noop();
        }

        if (declList == null)
        {
            // throw newException("Failed to parse inline style content");
            Util.err("Malformed inline tag style in " + hostingFilePath);
            return null;
        }

        boolean updated = false;

        // 2. Walk every declaration -> expression -> member, just as with full CSS.
        for (CSSDeclaration declaration : declList.getAllDeclarations())
        {
            CSSExpression expr = declaration.getExpression();
            if (expr == null)
                continue;

            for (ICSSExpressionMember member : expr.getAllMembers())
            {
                if (member instanceof CSSExpressionMemberTermURI)
                {
                    CSSExpressionMemberTermURI uriTerm = (CSSExpressionMemberTermURI) member;
                    String originalUrl = uriTerm.getURIString();

                    // inline styles can only reference images/fonts/etc.
                    String newUrl = downloadAndRelinkPassiveFile(originalUrl, hostingFileURL, hostingFilePath, true);

                    if (newUrl != null && !newUrl.equals(originalUrl))
                    {
                        uriTerm.setURIString(urlEncodeLink(newUrl));
                        updated = true;
                    }
                }
            }
        }

        // 3. Emit the rewritten declaration list (or null if unchanged).
        if (updated)
        {
            CSSWriterSettings settings = new CSSWriterSettings(ECSSVersion.CSS30, false);
            settings.setOptimizedOutput(false); // if true, then compactifies CSS
            return declList.getAsCSSString(settings, 0);
        }
        else
        {
            return null;
        }
    }

    /* =========================================================================================== */

    private String resolvedCSS_getAnyUrlProtocol(String cssFileURL) throws Exception
    {
        String newref = null;

        if (ArchiveOrgUrl.isArchiveOrgUrl(cssFileURL))
        {
            String cssNamingFileURL = ArchiveOrgUrl.extractArchivedUrlPart(cssFileURL);
            if (cssNamingFileURL != null)
            {
                newref = resolvedCSS.getAnyUrlProtocol(cssNamingFileURL);
                if (newref != null)
                    return newref;
            }
        }

        newref = resolvedCSS.getAnyUrlProtocol(cssFileURL);
        return newref;
    }

    /* =========================================================================================== */

    private void displayCssProgress(List<URI> inprogress)
    {
        if (inprogress.size() == 1)
        {
            String url = inprogress.get(0).toString();
            if (url.equals("file://" + this.currentHtmlFilePath.replace(File.separator, "/")))
                return;
        }

        StringBuilder sb = new StringBuilder();

        for (URI uri : inprogress)
        {
            if (sb.length() != 0)
                sb.append(" => ");
            sb.append(uri.toString());
        }

        Util.out(">>> CSS: " + sb.toString());
    }

    private void displayCssCyclicError(List<URI> inprogress, URI uri)
    {
        inprogress = new ArrayList<>(inprogress);
        inprogress.add(uri);

        StringBuilder sb = new StringBuilder();
        for (URI xuri : inprogress)
        {
            sb.append("  => ");
            sb.append(xuri.toString());
            sb.append("\n");
        }

        Util.err("Cyclic CSS reference:\n" + sb.toString());
    }

    /* =========================================================================================== */

    /**
     * Returns {@code true} <i>only if</i> the supplied style attribute value is guaranteed not to contain any CSS {@code url(...)}
     * reference.
     *
     * <p>
     * The test is deliberately conservative: it treats <code>url</code> written in any mix of upper-/lower-case letters and
     * followed by arbitrary ASCII whitespace (space, tab, CR, LF, form-feed) **before** the opening parenthesis as a match.
     *
     * <p>
     * Examples that will trigger a <code>false</code> result:
     * 
     * <pre>
     *   background:url(foo.png)
     *   background:URL ( "foo.png" )    == note whitespace before (
     *   background-image :   url(  'foo.png'  )
     * </pre>
     *
     * <p>
     * Examples that will return <code>true</code> (no <code>url(</code>):
     * 
     * <pre>
     *   color:red;
     *   font-weight:bold;
     *   content:"the literal text url(foo)";
     * </pre>
     */

    // (?i)      – case-insensitive
    // \burl     – the ident “url” at a word boundary
    // \s*       – optional whitespace (any length, incl. newlines)
    // \(        – literal opening parenthesis
    /** Case-insensitive match for <code>url  (</code> with optional whitespace */
    private static final Pattern URL_FUNCTION = Pattern.compile("(?i)\\burl\\s*\\(");

    private boolean inlineStyleHasNoExternalReferences(String style)
    {
        if (style == null || style.isEmpty())
            return true;

        String lc = style.toLowerCase(Locale.ROOT);
        if (!lc.contains("url"))
            return true;

        // Remove embedded data URLs first
        String clean = EMBEDDED_DATA_URL.matcher(style).replaceAll(" ");

        // If any other url(...) remains, assume external reference is possible
        return !URL_FUNCTION.matcher(clean).find();
    }

    /** Strip all CSS comments so they do not trigger false positives. */
    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /** Case-insensitive match for “@import” with optional whitespace after the “@”. */
    private static final Pattern IMPORT_DIRECTIVE = Pattern.compile("(?i)@\\s*import\\b");

    // Matches full embedded `url(...)` constructs that begin with data:
    // Case-insensitive, tolerant to whitespace and optional quotes
    private static final Pattern EMBEDDED_DATA_URL = Pattern.compile("(?i)\\burl\\s*\\(\\s*(['\"]?)data:[^\\)]*\\1\\s*\\)");

    /**
     * Returns {@code true} <i>only if</i> {@code cssText} is guaranteed not to contain any external references – namely
     * {@code url(...)} functions or <code>@import</code> rules.
     * 
     * The check is conservative: if the method is not 100 % sure that no reference is present, it returns {@code false}.
     */

    // It first strips CSS comments so that a literal string like
    //          /* @import url(foo.css) */ 
    // inside a comment will not force a false result.

    private boolean cssHasNoExternalReferences(String cssText)
    {
        if (cssText == null || cssText.isEmpty())
            return true; // trivially safe

        // Fast-path shortcut
        String lc = cssText.toLowerCase(Locale.ROOT);

        boolean mayHaveUrl = lc.contains("url");
        boolean mayHaveImport = lc.contains("import");

        if (!mayHaveUrl && !mayHaveImport)
            return true;

        // Clean up @important noise before checking for real @import
        if (mayHaveImport)
        {
            lc = lc.replaceAll("!\\s*important", " ");
            if (!lc.contains("import"))
                mayHaveImport = false;
        }

        // Expensive logic only if needed        

        // 1. Remove comments – they cannot introduce live references
        String clean = COMMENTS.matcher(cssText).replaceAll("");

        // 2. Strip embedded data URLs so they do not trigger URL detection
        clean = EMBEDDED_DATA_URL.matcher(clean).replaceAll(" ");

        // 3. Look for either url(…) or @import.  Finding either => external ref
        if (mayHaveUrl && URL_FUNCTION.matcher(clean).find())
            return false;

        if ((mayHaveImport && IMPORT_DIRECTIVE.matcher(clean).find()))
            return false;

        return true; // guaranteed clean
    }

    private boolean cssHasNoExternalImportReferences(String cssText)
    {
        if (cssText == null || cssText.isEmpty())
            return true; // trivially safe

        // 1. Remove comments – they cannot introduce live references
        String clean = COMMENTS.matcher(cssText).replaceAll("");

        // 2. Strip embedded data URLs so they do not trigger URL detection
        clean = EMBEDDED_DATA_URL.matcher(clean).replaceAll(" ");

        // 3. Look for @import.  Finding => external ref
        if (IMPORT_DIRECTIVE.matcher(clean).find())
            return false;

        return true; // guaranteed clean
    }

    private void trace(String s)
    {
        // Util.err(s);
    }

    /* ============================================================================ */

    public static void main(String[] args)
    {
        StyleActionToLocal self = new StyleActionToLocal(
                null,
                null,
                null,
                null,
                null,
                false,

                null,
                null,
                null,
                null,
                null,
                false,

                null);

        self.test1();
    }

    private void test1()
    {
        String cssText = StyleTest.CSS1;
        CascadingStyleSheet css = CSSReader.readFromString(cssText, ECSSVersion.CSS30);

        /*
         * check style for imports:
         * 
         *      @import url("https://www.dreamwidth.org/css/base.css");
         *      background-image: url("https://www.dreamwidth.org/images/header-bg.png");
         */

        // Walk @import rules
        for (CSSImportRule importRule : css.getAllImportRules())
        {
            String url = importRule.getLocationString();
            importRule.setLocationString("http://AAA.COM/IMPORT");
            Util.unused(url);
            Util.noop();
        }

        // Walk all style rules
        for (CSSStyleRule rule : css.getAllStyleRules())
        {
            for (CSSDeclaration declaration : rule.getAllDeclarations())
            {
                CSSExpression expr = declaration.getExpression();
                if (expr != null)
                {
                    for (ICSSExpressionMember member : expr.getAllMembers())
                    {
                        if (member instanceof CSSExpressionMemberTermURI)
                        {
                            CSSExpressionMemberTermURI uriTerm = (CSSExpressionMemberTermURI) member;
                            String originalUrl = uriTerm.getURIString();
                            uriTerm.setURIString("http://BBB.COM/URL");
                            Util.noop();
                            Util.unused(originalUrl);
                        }
                    }
                }
            }
        }

        CSSWriter writer = new CSSWriter(ECSSVersion.CSS30, false);
        writer.setWriteHeaderText(false);
        String modifiedCss = writer.getCSSAsString(css);
        Util.unused(modifiedCss);

        Util.noop();
    }

    private boolean isLocalHtmlFile(String hostingFilePath)
    {
        if (hostingFilePath == null)
            return false;
        String lc = hostingFilePath.toLowerCase();
        return lc.endsWith(".html") || lc.endsWith(".htm");
    }

    private Exception newException(String msg)
    {
        return new Exception(msg);
    }
}