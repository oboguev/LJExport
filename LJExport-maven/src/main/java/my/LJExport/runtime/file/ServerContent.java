package my.LJExport.runtime.file;

import java.net.URL;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.http.Web;

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

    public static Decision acceptContent(String href, String serverExt, String fnExt, byte[] content, Web.Response r)
            throws Exception
    {
        String host = new URL(href).getHost().toLowerCase();

        /*
         * www.lib.ru and lib.ru respond to TXT URL request with the reply of HTML content,
         * but inside is the <pre> block
         */
        if (Util.in(host, "lib.ru", "www.lib.ru") && Util.eqi(fnExt, "txt") && Util.eq(serverExt, "html"))
        {
            if (Util.containsCaseInsensitive(content, "<pre>"))
                return new Decision(DecisionStatus.Accept, "html");
        }

        return new Decision(DecisionStatus.Neutral);
    }
}
