package my.LJExport.styles;

import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.html.JSOUP;
import my.LJExport.runtime.Util;

public class StyleActionRelocate
{
    private static final String StyleManagerSignature = StyleManager.StyleManagerSignature;
    private static final String GeneratedBy = StyleManager.GeneratedBy;
    // private static final String SuppressedBy = StyleManager.SuppressedBy;

    /*
     * For all links to local styles, i.e. ../../{repeat}/../styles/remainder
     * add or remove preceding ../ as follows:
     * 
     * deltaLevel = +2   add ../../ 
     * deltaLevel = +1   add ../ 
     * deltaLevel = -1   remove ../ 
     * deltaLevel = -2   remove ../../ 
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
            String newcss = relocateCss(css);
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
            String newcss = relocateCss(css);
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
                    throw new Exception("Invalid style href");
                href = Util.stripStart(href, "../");
            }
            return href;
        }
    }

    /* =================================================================================================== */
    
    private String relocateCss(String css) throws Exception
    {
        // ###
        return null;
    }
}