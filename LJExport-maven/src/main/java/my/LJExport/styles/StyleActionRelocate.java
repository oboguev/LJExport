package my.LJExport.styles;

import org.jsoup.nodes.DataNode;
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
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;

public class StyleActionRelocate
{
    private static final String StyleManagerSignature = StyleManager.StyleManagerSignature;
    private static final String GeneratedBy = StyleManager.GeneratedBy;
    // private static final String SuppressedBy = StyleManager.SuppressedBy;

    /*
     * For all links to local styles, i.e. ../../{repeat}/../styles/remainder
     * add or remove preceding ../ as follows:
     * 
     * deltaLevel = +2   add leading ../../ 
     * deltaLevel = +1   add leading ../ 
     * deltaLevel = -1   remove leading ../ 
     * deltaLevel = -2   remove leading ../../ 
     */
    public boolean relocaleLocalHtmlStyleReferences(Node pageRoot, int deltaLevel) throws Exception
    {
        if (deltaLevel == 0)
            return false;

        boolean updated = false;

        /*
         * LINK tags
         */
        //   <link rel="stylesheet" type="text/css" href="../../../styles/remainder" generated-by="...."> 
        for (Node n : JSOUP.findElements(pageRoot, "link"))
        {
            String generated_by = JSOUP.getAttribute(n, GeneratedBy);
            if (generated_by == null || !generated_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;

            String href = JSOUP.getAttribute(n, "href");

            if (StyleManager.isHtmlReferenceToLocalStyle(href))
            {
                String newref = relocate(href, deltaLevel);
                JSOUP.updateAttribute(n, "href", newref);
                updated = true;
            }
        }

        /*
         * STYLE tags
         */
        for (Node n : JSOUP.findElements(pageRoot, "style"))
        {
            String generated_by = JSOUP.getAttribute(n, GeneratedBy);
            if (generated_by == null || !generated_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;
            
            Element el = JSOUP.asElement(n);
            boolean isPureCss = el.children().isEmpty() && el.childNodeSize() == 1 && el.childNode(0) instanceof DataNode;
            if (!isPureCss)
                throw new Exception("STYLE has non-CSS content");

            String css = el.data();
            String newcss = relocateCss(css, deltaLevel);
            if (newcss != null)
            {
                el.empty(); // Remove existing content
                el.appendChild(new DataNode(newcss, el.baseUri()));
                updated = true;
            }
        }

        /*
         * STYLE attribute on regular tags
         */
        for (Node n : JSOUP.findElements(pageRoot))
        {
            String style_altered_by = JSOUP.getAttribute(n, "style-altered-by");
            if (style_altered_by == null || !style_altered_by.trim().equalsIgnoreCase(StyleManagerSignature))
                continue;
            
            String css = JSOUP.getAttribute(n, "style");
            String newcss = relocateCss(css, deltaLevel);
            if (newcss != null)
            {
                JSOUP.updateAttribute(n, "style", newcss);
                updated = true;
            }
        }

        return updated;
    }

    private String relocate(String href, int deltaLevel) throws Exception
    {
        if (href.toLowerCase().startsWith("file://"))
            throw new Exception("Unexpected file:// url in a style or link");

        if (deltaLevel > 0)
        {
            for (int k = 0; k < deltaLevel; k++)
                href = "../" + href;
            return href;
        }
        else if (deltaLevel == 0)
        {
            return href;
        }
        else // if (deltaLevel < 0)
        {
            for (int k = 0; k < -deltaLevel; k++)
            {
                if (!href.startsWith("../"))
                    throw new Exception("Invalid relative href in a style");
                href = Util.stripStart(href, "../");
            }
            
            return href;
        }
    }
    
    private String adjustReference(String href, int deltaLevel) throws Exception
    {
        if (href.toLowerCase().startsWith("file://"))
            throw new Exception("Unexpected file:// url in a style");

        if (href.startsWith("../"))
            return relocate(href, deltaLevel);
        else
            return null;
    }

    /* =================================================================================================== */
    
    private String relocateCss(String cssText, int deltaLevel) throws Exception
    {
        CascadingStyleSheet css = CSSReader.readFromString(cssText, ECSSVersion.CSS30);
        if (css == null)
            throw new Exception("Failed to parse CSS content");

        boolean updated = false;

        // Walk @import rules
        for (CSSImportRule importRule : css.getAllImportRules())
        {
            String originalUrl = importRule.getLocationString();
            String newUrl = adjustReference(originalUrl, deltaLevel);
            if (newUrl != null)
            {
                importRule.setLocationString(newUrl);
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
                            String newUrl = adjustReference(originalUrl, deltaLevel);
                            if (newUrl != null)
                            {
                                uriTerm.setURIString(newUrl);
                                changed = true;
                                updated = true;
                            }
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
}