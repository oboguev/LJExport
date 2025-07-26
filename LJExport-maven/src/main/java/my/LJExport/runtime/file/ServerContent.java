package my.LJExport.runtime.file;

import my.LJExport.runtime.ContentProvider;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.Web;
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

    public static Decision acceptContent(String href, String serverExt, String fnExt,
            ContentProvider contentProvider, Web.Response r)
            throws Exception
    {
        if (ArchiveOrgUrl.isArchiveOrgUrl(href))
            href = ArchiveOrgUrl.extractArchivedUrlPart(href);

        String host = Util.extractHost(href);

        /*
         * www.lib.ru and lib.ru respond to TXT URL request with the reply of HTML content,
         * but inside is the <pre> block
         */
        if (host != null && Util.in(host, "lib.ru", "www.lib.ru") && Util.eqi(fnExt, "txt") && Util.eq(serverExt, "html"))
        {
            if (Util.containsCaseInsensitive(contentProvider.get(), "<pre>"))
                return new Decision(DecisionStatus.Accept, "html");
        }

        return DecisionNeutral;
    }
}
