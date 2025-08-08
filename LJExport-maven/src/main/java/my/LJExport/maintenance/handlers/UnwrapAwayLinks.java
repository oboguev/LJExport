package my.LJExport.maintenance.handlers;

import java.util.List;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.url.AwayLink;
import my.LJExport.runtime.url.LegacyPercentUEncoding;
import my.LJExport.runtime.url.UrlUtil;

public class UnwrapAwayLinks extends MaintenanceHandler
{
    private static boolean DryRun = true;

    private int UpdatedLinks = 0;

    public UnwrapAwayLinks() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> * Unwrap away links in A.HREF and IMG.SRC");
        super.beginUsers("Unwrapping away links");
        txLog.writeLine(String.format("Executing UnwrapAwayLinks in %s mode", DryRun ? "DRY RUN" : "WET RUN"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    @Override
    protected void beginUser() throws Exception
    {
        super.beginUser();
    }

    @Override
    protected void endUser() throws Exception
    {
        String msg = String.format("Links updated for user %s: %d", Config.User, UpdatedLinks);
        trace(msg);
        super.endUser();
    }

    /* ===================================================================================================== */

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

        boolean updated = false;

        updated |= process(fullHtmlFilePath, relativeFilePath, parser, pageFlat, "a", "href");
        updated |= process(fullHtmlFilePath, relativeFilePath, parser, pageFlat, "img", "src");

        if (updated && !DryRun)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullHtmlFilePath, html);
        }
    }

    private boolean process(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat, String tag, String attr) throws Exception
    {
        boolean updated = false;

        for (Node n : JSOUP.findElements(pageFlat, tag))
        {
            String old_encoded = JSOUP.getAttribute(n, attr);
            if (old_encoded == null)
                continue;

            LegacyPercentUEncoding lpu = new LegacyPercentUEncoding(0x0410, 0x0490);
            if (lpu.count(old_encoded) >= 5)
            {
                old_encoded = lpu.normalize(old_encoded);
                Util.err("Normalized %u in link in HTML file " + fullHtmlFilePath);
            }

            String old_decoded;
            try
            {
                old_decoded = UrlUtil.decodeHtmlAttrLink(old_encoded);
            }
            catch (Exception ex)
            {
                traceError("Undecodable URL: " + old_encoded);
                continue;
            }

            String unwrapped_decoded = AwayLink.unwrapDecoded(old_decoded);
            if (unwrapped_decoded.equals(old_decoded) || unwrapped_decoded.equals("null"))
                continue;

            String new_encoded;
            try
            {
                new_encoded = UrlUtil.encodeUrlForHtmlAttr(unwrapped_decoded, true);
            }
            catch (Exception ex)
            {
                traceError("Unencodable URL: " + unwrapped_decoded);
                continue;
            }

            JSOUP.updateAttribute(n, attr, new_encoded);

            if (null == JSOUP.getAttribute(n, "original-" + attr))
                JSOUP.setAttribute(n, "original-" + attr, old_encoded);

            updated = true;
            UpdatedLinks++;

        }

        return updated;
    }
}
