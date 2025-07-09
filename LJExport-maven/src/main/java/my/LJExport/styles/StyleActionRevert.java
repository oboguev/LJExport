package my.LJExport.styles;

import org.jsoup.nodes.Node;

import my.LJExport.html.JSOUP;
import my.LJExport.readers.direct.PageParserDirectBasePassive;

public class StyleActionRevert
{
    private static final String StyleManagerSignature = StyleManager.StyleManagerSignature;

    public boolean processHtmlFileRevert(String htmlFilePath, PageParserDirectBasePassive parser) throws Exception
    {
        boolean updated = false;

        /*
         * LINK tags
         */
        for (Node n : JSOUP.findElements(parser.pageRoot, "link"))
        {
            String suppressed_by = JSOUP.getAttribute(n, "suppressed-by");
            String generated_by = JSOUP.getAttribute(n, "generated-by");

            if (generated_by != null && generated_by.trim().equalsIgnoreCase(StyleManagerSignature))
            {
                JSOUP.removeElement(parser.pageRoot, n);
                updated = true;
            }

            if (suppressed_by != null && suppressed_by.trim().equalsIgnoreCase(StyleManagerSignature))
            {
                String original_rel = JSOUP.getAttribute(n, "original-rel");
                String original_type = JSOUP.getAttribute(n, "original-type");
                String original_href = JSOUP.getAttribute(n, "original-href");

                if (original_rel == null)
                    throw new Exception("Link tag missing attribute original-rel");

                if (original_href == null)
                    throw new Exception("Link tag missing attribute original-href");

                JSOUP.deleteAttribute(n, "rel");
                JSOUP.deleteAttribute(n, "type");
                JSOUP.deleteAttribute(n, "href");

                JSOUP.setAttribute(n, "rel", original_rel);
                if (original_type != null)
                    JSOUP.setAttribute(n, "type", original_type);
                JSOUP.setAttribute(n, "href", original_href);

                JSOUP.deleteAttribute(n, "original-rel");
                if (original_type != null)
                    JSOUP.deleteAttribute(n, "original-type");
                JSOUP.deleteAttribute(n, "original-href");
                JSOUP.deleteAttribute(n, "suppressed-by");

                updated = true;
            }
        }

        /*
         * STYLE tags
         */
        for (Node n : JSOUP.findElements(parser.pageRoot, "style"))
        {
            String suppressed_by = JSOUP.getAttribute(n, "suppressed-by");
            String generated_by = JSOUP.getAttribute(n, "generated-by");

            if (generated_by != null && generated_by.trim().equalsIgnoreCase(StyleManagerSignature))
            {
                JSOUP.removeElement(parser.pageRoot, n);
                updated = true;
            }

            if (suppressed_by != null && suppressed_by.trim().equalsIgnoreCase(StyleManagerSignature))
            {
                String original_type = JSOUP.getAttribute(n, "original-type");
                JSOUP.deleteAttribute(n, "type");
                if (original_type != null)
                {
                    JSOUP.setAttribute(n, "type", original_type);
                    JSOUP.deleteAttribute(n, "original-type");
                }
                JSOUP.deleteAttribute(n, "suppressed-by");
                updated = true;
            }
        }

        /*
         * STYLE attribute on regular tags
         */
        for (Node n : JSOUP.findElements(parser.pageRoot))
        {
            String style_altered_by = JSOUP.getAttribute(n, "style-altered-by");
            if (style_altered_by != null && style_altered_by.trim().equalsIgnoreCase(StyleManagerSignature))
            {
                String original_style = JSOUP.getAttribute(n, "original-style");
                if (original_style != null)
                {
                    JSOUP.deleteAttribute(n, "style");
                    JSOUP.setAttribute(n, "style", original_style);
                    JSOUP.deleteAttribute(n, "original-style");
                }
                JSOUP.deleteAttribute(n, "style-altered-by");
                updated = true;
            }
        }

        return updated;
    }
}
