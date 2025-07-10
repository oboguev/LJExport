package my.LJExport.styles;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import java.net.URLEncoder;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import com.helger.css.ECSSVersion;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSExpression;
import com.helger.css.decl.CSSExpressionMemberTermURI;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSExpressionMember;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriter;

import my.LJExport.Config;
import my.LJExport.html.JSOUP;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.FileBackedMap;
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
    private static final String Original = StyleManager.Original;

    private final String styleDir;
    private final LinkDownloader linkDownloader;
    private final IntraInterprocessLock styleRepositoryLock;
    private final FileBackedMap resolvedCSS;
    private final List<URI> inprogress = new ArrayList<>();

    public StyleActionToLocal(String styleDir, LinkDownloader linkDownloader, IntraInterprocessLock styleRepositoryLock,
            FileBackedMap resolvedCSS)
    {
        this.styleDir = styleDir;
        this.linkDownloader = linkDownloader;
        this.styleRepositoryLock = styleRepositoryLock;
        this.resolvedCSS = resolvedCSS;
    }

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

            // ### href resolves to list ?? etc.
            // ### CssHelper.cssLinks

            if (type == null || type.trim().equalsIgnoreCase("text/css"))
            {
                processStyleLink(JSOUP.asElement(n), href, htmlFilePath, htmlPageUrl);
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

            // ### <style>
            // ### @import url("other.css");
            // ### </style>
            // ### use InterprocessLock for locking
            // ### change to <style type="text/StyleManagerSignature-suppressed-css">

            // ### original tag: set suppressed-by, save type to original-type (if not null) and change type="text/StyleManagerSignature-suppressed-css" 
            // ###           check initially has no original-type and no suppressed-by or generated-by
            // ### new tag: copy type and other attributes, apply generated-by
            // ### copy inner content from original to new and insert new after original
            // ### updated |= ....
        }

        /*
         * STYLE attribute on regular tags
         */
        for (Node n : JSOUP.findElements(parser.pageRoot))
        {
            String style_altered_by = JSOUP.getAttribute(n, "style-altered-by");
            if (style_altered_by != null && style_altered_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            if (style_altered_by != null)
                throw new Exception("Unexpected attribute in a tag");

            /* tag not processed yet*/

            // ### style attributes on regular tags 
            // ### <p style="color: blue; font-weight: bold;"> can have @import or url:?
            // ### use InterprocessLock for locking

            // ### check has no original-style
            // ### save style to original-style
            // ### update style
            // ### updated |= ....
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
     */
    //   <link rel="stylesheet" type="text/css" href="https://web-static.archive.org/_static/css/banner-styles.css?v=1B2M2Y8A"> 
    private void processStyleLink(Element el, String href, String htmlFilePath, String baseUrl) throws Exception
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
        
        if (ArchiveOrgUrl.isArchiveOrgUrl(href))
            throw new Exception("Loading styles from Web Archive is not implemented");

        String newref = resolvedCSS.getAnyUrlProtocol(href);
        if (newref == null)
        {
            // lock repository to avoid deadlocks while processing A.css and B.css referencing each other
            styleRepositoryLock.lockExclusive();
            try
            {
                // ### txLogCheckEmpty
                
                /*
                 * Check if this CSS has already been adjusted on disk
                 */
                newref = resolvedCSS.getAnyUrlProtocol(href);
                if (newref == null)
                {
                    newref = linkDownloader.download(href, null, "");
                    if (newref == null)
                        throw new Exception("Failed to download style URL: " + href);
                    String cssFilePath = linkDownloader.rel2abs(newref);

                    /*
                     * No in-progress CSS processing yet on this thread
                     */
                    inprogress.clear();

                    String modifiedCss = resolveCssDependencies(Util.readFileAsString(cssFilePath), cssFilePath, href, baseUrl);
                    if (modifiedCss != null)
                    {
                        // ### save before (.guid-before)
                        // ### txLog saving ... to ...
                        Util.writeToFileSafe(cssFilePath, modifiedCss);
                        // ### txLog overwrote ...
                        // ### txLog writig to resolved css
                    }
                    resolvedCSS.put(href, newref);
                    // ### txLogClear
                }
            }
            finally
            {
                styleRepositoryLock.unlock();
            }
        }

        updateLinkElement(el, htmlFilePath, newref);
    }

    private void updateLinkElement(Element el, String htmlFilePath, String newref) throws Exception
    {
        String cssFilePath = linkDownloader.rel2abs(newref);
        String relpath = RelativeLink.fileRelativeLink(cssFilePath, htmlFilePath,
                Config.DownloadRoot + File.separator + Config.User);

        if (JSOUP.getAttribute(el, Original + "rel") != null ||
                JSOUP.getAttribute(el, Original + "type") != null ||
                JSOUP.getAttribute(el, Original + "href") != null)
        {
            throw new Exception("LINK tag contains unexpected original-xxx attributes");
        }

        String original_rel = JSOUP.getAttribute(el, "rel");
        String original_type = JSOUP.getAttribute(el, "type");
        String original_href = JSOUP.getAttribute(el, "href");

        Element elx = el.clone().empty(); // shallow copy (preserves attributes)
        el.after(elx); // insert into the tree

        JSOUP.deleteAttribute(elx, "rel");
        JSOUP.deleteAttribute(elx, "type");
        JSOUP.deleteAttribute(elx, "href");

        JSOUP.setAttribute(elx, "rel", "stylesheet");
        if (original_type != null)
            JSOUP.setAttribute(elx, "type", original_type); // "text/css" or ommitted
        JSOUP.setAttribute(elx, "href", urlEncodeLink(relpath));
        JSOUP.setAttribute(elx, GeneratedBy, StyleManagerSignature);

        JSOUP.deleteAttribute(el, "rel");
        JSOUP.deleteAttribute(el, "type");
        JSOUP.deleteAttribute(el, "href");
        JSOUP.setAttribute(el, "rel", Original + "stylesheet");
        JSOUP.setAttribute(el, Original + "rel", original_rel);
        if (original_type != null)
            JSOUP.setAttribute(el, Original + "type", original_type);
        JSOUP.setAttribute(el, Original + "href", original_href);
        JSOUP.setAttribute(el, SuppressedBy, StyleManagerSignature);
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

    private String resolveCssDependencies(String cssText, String cssFilePath, String cssFileURL, String baseUrl) throws Exception
    {
        // ### inprogress.add(new URI(href));
        // ### keep a in-memory list of scans in progress, to detect recursion
        // ### check for recursion, isSame
        // ### on exit - finally -- pop

        // ### check style at newref for imports
        // ### background-image: url("https://www.dreamwidth.org/images/header-bg.png");
        // ### @import url("https://www.dreamwidth.org/css/base.css");
        // ###
        CascadingStyleSheet css = CSSReader.readFromString(cssText, ECSSVersion.CSS30);
        if (css == null)
            throw new Exception("Failed to parse CSS content");

        boolean updated = false;

        // Walk @import rules
        for (CSSImportRule importRule : css.getAllImportRules())
        {
            String url = importRule.getLocationString();
            String newUrl = downloadAndRelink(url); // ### baseUrl
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
                            String newUrl = downloadAndRelink(originalUrl); // ### baseUrl
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

    private String downloadAndRelink(String originalUrl)
    {
        // ###
        return null;
    }
}