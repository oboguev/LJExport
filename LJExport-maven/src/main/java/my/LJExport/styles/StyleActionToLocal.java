package my.LJExport.styles;

import java.io.File;

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
import my.LJExport.runtime.Util;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.synch.InterprocessLock;

public class StyleActionToLocal
{
    private static final String StyleManagerSignature = StyleManager.StyleManagerSignature;
    
    private final String styleDir;
    private final LinkDownloader linkDownloader;
    private final InterprocessLock styleRepositoryLock;
    
    public StyleActionToLocal(String styleDir, LinkDownloader linkDownloader, InterprocessLock styleRepositoryLock)
    {
        this.styleDir = styleDir;
        this.linkDownloader = linkDownloader;
        this.styleRepositoryLock = styleRepositoryLock;
    }
    
    public boolean processHtmlFileToLocal(String htmlFilePath, PageParserDirectBasePassive parser, String htmlPageUrl) throws Exception
    {
        if (htmlPageUrl != null && !Util.isAbsoluteURL(htmlPageUrl))
            throw new IllegalArgumentException("HTML page URL is not absolute: " + htmlPageUrl);
        
        boolean updated = false;

        // ### inline style tags
        // ### <style>
        // ### @import url("other.css");
        // ### </style>
        // ### use InterprocessLock for locking
        // ### change to <style type="text/StyleManagerSignature-suppressed-css">

        // ### style attributes on regular tags 
        // ### <p style="color: blue; font-weight: bold;"> can have @import or url:?
        // ### use InterprocessLock for locking

        /*
         * LINK tags
         */
        //   <link rel="stylesheet" type="text/css" href="https://web-static.archive.org/_static/css/banner-styles.css?v=1B2M2Y8A"> 
        for (Node n : JSOUP.findElements(parser.pageRoot, "link"))
        {
            String generated_by = JSOUP.getAttribute(n, "generated-by");
            if (generated_by != null && generated_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            String suppressed_by = JSOUP.getAttribute(n, "suppressed-by");
            if (suppressed_by != null && suppressed_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            /* tag not processed yet*/
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
        
        /*
         * STYLE tags
         */
        for (Node n : JSOUP.findElements(parser.pageRoot, "style"))
        {
            String generated_by = JSOUP.getAttribute(n, "generated-by");
            if (generated_by != null && generated_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            String suppressed_by = JSOUP.getAttribute(n, "suppressed-by");
            if (suppressed_by != null && suppressed_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;
            
            /* tag not processed yet*/
            // ###
        }
        
        /*
         * STYLE attribute on regular tags
         */
        for (Node n : JSOUP.findElements(parser.pageRoot))
        {
            String style_altered_by = JSOUP.getAttribute(n, "style-altered-by");
            if (style_altered_by != null && style_altered_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;
            
            /* tag not processed yet*/
            // ###
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

    private boolean processStyleLink(Element el, String href, String htmlFilePath) throws Exception
    {
        if (href.toLowerCase().startsWith("http://") || href.toLowerCase().startsWith("https://"))
        {
            // do process
        }
        else
        {
            String generatedBy = JSOUP.getAttribute(el, "generated-by");
            if (generatedBy != null && generatedBy.equals(StyleManagerSignature))
                return false;
            throw new Exception("Unexpected link.href: " + href);
        }

        String newref = linkDownloader.download(href, null, "");
        String cssFilePath = linkDownloader.getLinksDir() + File.separator + newref.replace("/", File.separator);

        // lock repository to avoid deadlocks while processing A.css and B.css referencing each other
        styleRepositoryLock.lockExclusive();
        try
        {
            // ### keep a list (on-disk) of adjusted css files to prevent double-scan and recursion
            // ### check list if should scan
            String modifiedCss = resolveCssDependencies(Util.readFileAsString(cssFilePath), cssFilePath, href);

            if (modifiedCss != null)
            {
                // ### write it to cssFilePath
                // ### add to list
            }
        }
        finally
        {
            styleRepositoryLock.unlock();
        }

        // ### add new link tags <link rel="stylesheet" href="..." type="text/css" generated-by=StyleManagerSignature>
        // ### change original to <link rel="original-stylesheet" original-rel="..." original-href="..." original-type="..." suppressed-by=StyleManagerSignature> and delete href and type
        // ### return true

        return false;
    }

    /* ================================================================================== */

    private String resolveCssDependencies(String cssText, String cssFilePath, String cssFileURL) throws Exception
    {
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
            String newUrl = downloadAndRelink(url);
            // ### recursive?
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
                            String newUrl = downloadAndRelink(originalUrl);
                            uriTerm.setURIString(newUrl);
                            changed = true;
                            updated = true;
                            // ### recursive ?
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
