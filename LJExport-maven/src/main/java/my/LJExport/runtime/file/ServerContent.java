package my.LJExport.runtime.file;

import java.net.URL;

import my.LJExport.runtime.ContentProvider;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.url.AwayLink;
import my.LJExport.runtime.url.UrlUtil;
import my.WebArchiveOrg.ArchiveOrgUrl;

public class ServerContent
{
    public static enum DecisionStatus
    {
        Accept, Reject, Neutral
    }

    public static class Decision
    {
        public final DecisionStatus decisionStatus;
        public final String finalExtension;

        public Decision(DecisionStatus decisionStatus, String finalExtension)
        {
            this.decisionStatus = decisionStatus;
            this.finalExtension = finalExtension;
        }

        public Decision(DecisionStatus decisionStatus)
        {
            this.decisionStatus = decisionStatus;
            this.finalExtension = null;
        }

        public boolean isAccept()
        {
            return decisionStatus == DecisionStatus.Accept;
        }

        public boolean isReject()
        {
            return decisionStatus == DecisionStatus.Reject;
        }

        public boolean isNeutral()
        {
            return decisionStatus == DecisionStatus.Neutral;
        }
    }

    public static final Decision DecisionNeutral = new Decision(DecisionStatus.Neutral);
    public static final Decision DecisionReject = new Decision(DecisionStatus.Reject);

    public static Decision acceptContent(String href, String serverExt, String headerExt, String fnExt,
            ContentProvider contentProvider, Web.Response r)
            throws Exception
    {
        if (UrlUtil.looksLikeUrlWithoutScheme(href))
            href = "https://" + href;
        
        href = AwayLink.unwrapAwayLinkDecoded(href);
        
        if (ArchiveOrgUrl.isArchiveOrgUrl(href))
            href = ArchiveOrgUrl.extractArchivedUrlPart(href);

        if (UrlUtil.looksLikeUrlWithoutScheme(href))
            href = "https://" + href;
        
        href = AwayLink.unwrapAwayLinkDecoded(href);

        String host = UrlUtil.extractHost(href);
        String path = new URL(href).getPath();
        if (path == null)
            path = "";
        
        /*
         * CSS files
         */
        if (Util.eq(fnExt, "css"))
        {
            if (Util.eqi(headerExt, "css") || Util.eqi(serverExt, "css"))
                return new Decision(DecisionStatus.Accept, "css");

            if (Util.eqi(headerExt, "txt") || Util.eqi(serverExt, "txt"))
                return new Decision(DecisionStatus.Accept, "css");
        }

        /*
         * For TXT extension. accept all kind of text files such as email messages (message/rfc822, normally .EML)
         */
        if (fnExt != null && fnExt.equalsIgnoreCase("txt"))
        {
            String mimeType = FileTypeDetector.mimeTypeFromActualFileContent(contentProvider.get(), "txt");
            if (mimeType == null)
                mimeType = "";
            
            switch (mimeType)
            {
            case "text/plain":
            case "text/x-markdown":
            case "text/x-c":
            case "text/x-c++":
            case "text/x-latex":
            case "text/x-log":
            case "text/x-diff":
            case "text/x-setext":
            //
            case "message/rfc822":
            //
            case "application/x-sh":
            case "application/x-perl":
            case "application/x-python":
            case "application/x-tex":
            case "application/x-troff":
            case "application/x-subrip":
            case "application/x-httpd-php":
            case "application/x-csrc":
            case "application/x-c++src":
            case "application/x-java-source":
                return new Decision(DecisionStatus.Accept, "txt");
            }
        }

        /*
         * www.lib.ru and lib.ru respond to TXT URL request with the reply of HTML content,
         * but inside is the <pre> block
         */
        if (host != null && Util.in(host, "lib.ru", "www.lib.ru", "lib.kharkov.ua") && Util.eqi(fnExt, "txt")
                && Util.eq(serverExt, "html"))
        {
            if (Util.containsCaseInsensitive(contentProvider.get(), "<pre>"))
                return new Decision(DecisionStatus.Accept, "html");
        }

        /*
         * Same for http://www.kulichki.com/moshkow/HISTORY/FELSHTINSKY/f17.txt
         */
        if (host != null && Util.in(host, "kulichki.com", "www.kulichki.com", "kulichki.ru", "www.kulichki.ru") &&
                path.toLowerCase().startsWith("/moshkow/") &&
                Util.eqi(fnExt, "txt") && Util.eq(serverExt, "html"))
        {
            if (Util.containsCaseInsensitive(contentProvider.get(), "<pre>"))
                return new Decision(DecisionStatus.Accept, "html");
        }

        return DecisionNeutral;
    }
}
