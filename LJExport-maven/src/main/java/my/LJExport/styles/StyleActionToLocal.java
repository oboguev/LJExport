package my.LJExport.styles;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URLEncoder;

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
import my.LJExport.html.JSOUP;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.FileBackedMap;
import my.LJExport.runtime.TxLog;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.RelativeLink;
import my.LJExport.runtime.synch.IntraInterprocessLock;
import my.WebArchiveOrg.ArchiveOrgUrl;

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
     * Downloads remote resources to files in the repository. 
     */
    private final LinkDownloader linkDownloader;

    /*
     * Persistent (on-disk) map that stores local CSS style sheets that have already been
     * re-written to resolve external links in them. A link to a remote server is re-written
     * to become a link to a local file cached in styles repository.
     * 
     * Maps URL -> local file path (of retrieved URL content) relative to repository root.
     */
    private final FileBackedMap resolvedCSS;
    
    /*
     * Transaction log. Helps to identify a state of style repository if a failure (OS crash
     * or power down) happens in the middle of update operation.
     */
    private final TxLog txLog;

    /* 
     * list (stack) of URLs of CSS/HTML files with styles being currently re-writtem,
     * used to detect circular references 
     */
    private final List<URI> inprogress = new ArrayList<>();

    public StyleActionToLocal(LinkDownloader linkDownloader,
            IntraInterprocessLock styleRepositoryLock,
            FileBackedMap resolvedCSS,
            TxLog txLog)
    {
        this.linkDownloader = linkDownloader;
        this.styleRepositoryLock = styleRepositoryLock;
        this.resolvedCSS = resolvedCSS;
        this.txLog = txLog;
    }

    /*
     * Returns @true if parsed HTML file (tree) had been changed during style processing,
     * and the tree needs to be written back to the file.
     * 
     * If no changes are done, return @false.   
     */
    public boolean processHtmlFileToLocalStyles(String htmlFilePath, PageParserDirectBasePassive parser, String htmlPageUrl)
            throws Exception
    {
        if (htmlPageUrl != null && !Util.isAbsoluteURL(htmlPageUrl))
            throw new IllegalArgumentException("HTML page URL is not absolute: " + htmlPageUrl);

        boolean updated = false;

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
                throw new Exception("Unexpected attributes in LINK tag");

            /* tag not processed yet*/
            String rel = JSOUP.getAttribute(n, "rel");
            String type = JSOUP.getAttribute(n, "type");
            String href = JSOUP.getAttribute(n, "href");

            if (rel == null || !relContainsStylesheet(rel))
                continue;

            if (!rel.trim().equalsIgnoreCase("stylesheet"))
                throw new Exception("Unexpected value of link.rel: " + rel);

            if (href == null)
                throw new Exception("Unexpected value of link.href: null");

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
                throw new Exception("Unexpected attributes in LINK tag");

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
                throw new Exception("Unexpected attribute in a tag");

            /* tag not processed yet*/
            if (JSOUP.getAttribute(n, "style") != null)
            {
                updated |= processHtmlStyleAttribute(JSOUP.asElement(n), htmlFilePath, htmlPageUrl);
            }
        }

        return updated;
    }

    /* ================================================================================== */

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
        if (baseUrl == null && !Util.isAbsoluteURL(href))
        {
            throw new Exception("Unexpected link.href: " + href);
        }
        else if (!Util.isAbsoluteURL(href))
        {
            // later may resolve it against baseUrl
            throw new Exception("Unexpected link.href: " + href);
        }

        String cssFileURL = Util.resolveURL(baseUrl, href);

        if (ArchiveOrgUrl.isArchiveOrgUrl(cssFileURL))
            throw new Exception("Loading styles from Web Archive is not implemented");

        String newref = resolvedCSS.getAnyUrlProtocol(cssFileURL);
        if (newref == null)
        {
            // lock repository to avoid deadlocks while processing A.css and B.css referencing each other
            styleRepositoryLock.lockExclusive();
            try
            {
                txLog.open();

                if (!txLog.isEmpty())
                {
                    StringBuilder sb = new StringBuilder();
                    final String nl = "\n";
                    sb.append("Operation on styles repository was aborted in the middle of critical phase." + nl);
                    sb.append("Please check transaction log and take corrective action." + nl);
                    sb.append("Log file: " + txLog.getPath());
                    Util.err(sb.toString());
                    throw new Exception(sb.toString());
                }

                /*
                 * Check if this CSS has already been adjusted on disk
                 */
                newref = resolvedCSS.getAnyUrlProtocol(cssFileURL);
                if (newref == null)
                {
                    newref = linkDownloader.download(cssFileURL, null, "");
                    if (newref == null)
                        throw new Exception("Failed to download style URL: " + cssFileURL);
                    String cssFilePath = linkDownloader.rel2abs(newref);

                    /*
                     * No in-progress CSS processing yet on this thread
                     */
                    inprogress.clear();

                    String beforeCss = Util.readFileAsString(cssFilePath);
                    String modifiedCss = resolveCssDependencies(beforeCss, cssFilePath, cssFileURL);
                    if (modifiedCss != null)
                    {
                        String beforeFilePath = cssFilePath + "." + Util.uuid() + ".before";
                        Util.writeToFileVerySafe(beforeFilePath, beforeCss);
                        txLog.writeLine(String.format("Saved file [%s] to [%s]", cssFilePath, beforeFilePath));

                        txLog.writeLine(
                                String.format("About to overwrite file with edited CSS content, file path: [%s]", cssFilePath));
                        Util.writeToFileVerySafe(cssFilePath, modifiedCss);
                        txLog.writeLine(String.format("Overwrote file with edited CSS content, file path: [%s]", cssFilePath));

                        txLog.writeLine(String.format("About to write a mapping to map file: %s%sfrom: %s%sto: %s",
                                resolvedCSS.getPath(),
                                System.lineSeparator(),
                                cssFileURL,
                                System.lineSeparator(),
                                newref));
                        resolvedCSS.put(cssFileURL, newref);
                        txLog.writeLine(String.format("Wrote a mapping to %s", resolvedCSS.getPath()));
                        txLog.clear();
                        new File(beforeFilePath).delete();
                    }
                    else
                    {
                        resolvedCSS.put(cssFileURL, newref);
                    }
                }
            }
            finally
            {
                txLog.close();
                styleRepositoryLock.unlock();
            }
        }

        updateLinkElement(elLink, htmlFilePath, newref, elInsertAfter, updateElLink, createdElement);
    }

    private void updateLinkElement(Element elLink, String htmlFilePath, String newref, Element elInsertAfter, boolean updateElLink,
            AtomicReference<Element> createdElement) throws Exception
    {
        String cssFilePath = linkDownloader.rel2abs(newref);
        String relpath = RelativeLink.fileRelativeLink(cssFilePath, htmlFilePath,
                Config.DownloadRoot + File.separator + Config.User);

        if (JSOUP.getAttribute(elLink, Original + "rel") != null ||
                JSOUP.getAttribute(elLink, Original + "type") != null ||
                JSOUP.getAttribute(elLink, Original + "href") != null)
        {
            throw new Exception("LINK tag contains unexpected original-xxx attributes");
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
        JSOUP.setAttribute(elx, "href", urlEncodeLink(relpath));
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

    private String urlEncodeLink(String relativeFilePath) throws Exception
    {
        if (relativeFilePath == null || relativeFilePath.isEmpty())
            return relativeFilePath;

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
                String encodedComp = URLEncoder.encode(comp, "UTF-8").replace("+", "%20");
                encoded.append(encodedComp);
            }
        }

        return encoded.toString();
    }

    /* ================================================================================== */

    /*
     * cssText -- CSS content/text to process
     * hostingFilePath -- path of either CSS file or HTML file containing cssText, cached in a local file system
     * hostingFileURL -- original URL of hosting CSS/HTML file, used to resolve relative links in cssText     
     */
    private String resolveCssDependencies(String cssText, String hostingFilePath, String hostingFileURL) throws Exception
    {
        /*
         * Check for circular references in stylesheets being re-writtem
         */
        URI uri = new URI(hostingFileURL);
        for (URI xuri : inprogress)
        {
            if (isSameURL(uri, xuri))
            {
                /*
                 * Should later add actual handling of circular references.
                 * For now just abort.
                 */
                throw new Exception("Detected circular reference in style sheets");
            }
        }

        inprogress.add(uri);
        try
        {
            return do_resolveCssDependencies(cssText, hostingFilePath, hostingFileURL);
        }
        finally
        {
            inprogress.remove(inprogress.size() - 1);

        }
    }

    private String do_resolveCssDependencies(String cssText, String hostingFilePath, String hostingFileURL) throws Exception
    {
        CascadingStyleSheet css = CSSReader.readFromString(cssText, ECSSVersion.CSS30);
        if (css == null)
            throw new Exception("Failed to parse CSS content");

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
            String newUrl = downloadAndRelink(url, hostingFileURL);
            // ### if (ArchiveOrgUrl.isArchiveOrgUrl
            // ### recursive? -- if so detect and abort
            importRule.setLocationString(newUrl);
            updated = true;
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
                            String newUrl = downloadAndRelink(originalUrl, hostingFileURL);
                            uriTerm.setURIString(newUrl);
                            changed = true;
                            updated = true;
                        }
                    }
                    if (changed && Config.False)
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
            return modifiedCss;
        }
        else
        {
            return null;
        }
    }

    private boolean isSameURL(URI uri1, URI uri2)
    {
        // Compare scheme and host case-insensitively
        if (Config.False && !equalsIgnoreCase(uri1.getScheme(), uri2.getScheme()))
            return false;

        if (!equalsIgnoreCase(uri1.getHost(), uri2.getHost()))
            return false;

        // Compare port (default ports need normalization if desired)
        if (Config.False && uri1.getPort() != uri2.getPort())
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

    private String downloadAndRelink(String originalUrl, String baseUrl)
    {
        // ### check if remote
        // ###
        return null;
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
        String modifiedCss = resolveCssDependencies(cssText, htmlFilePath, htmlPageUrl);
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
            throw new Exception("STYLE tag contains unexpected original-type attribute");

        String original_type = JSOUP.getAttribute(elStyle, "type");

        Element elx = elStyle.clone().empty(); // shallow copy (preserves attributes)
        JSOUP.setAttribute(elx, GeneratedBy, StyleManagerSignature);
        elx.appendChild(new DataNode(modifiedCss, elx.baseUri()));
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
            throw new Exception("Tag contains unexpected original-style attribute");

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
        // 1. Parse “prop: value; …” into a declaration list.
        CSSDeclarationList declList = CSSReaderDeclarationList.readFromString(inlineStyleText, ECSSVersion.CSS30);
        if (declList == null)
            throw new Exception("Failed to parse inline style content");

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

                    // Inline styles can only reference images/fonts/etc.
                    String newUrl = downloadAndRelink(originalUrl, hostingFileURL);

                    if (!newUrl.equals(originalUrl))
                    {
                        uriTerm.setURIString(newUrl);
                        updated = true;
                    }
                }
            }
        }

        // 3. Emit the rewritten declaration list (or null if unchanged).
        if (updated)
        {
            CSSWriterSettings settings = new CSSWriterSettings(ECSSVersion.CSS30, false);
            settings.setOptimizedOutput(true); // optional: strip superfluous whitespace
            return declList.getAsCSSString(settings, 0);
        }
        else
        {
            return null;
        }
    }
}