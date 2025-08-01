package my.WebArchiveOrg;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.LinkRedownloader;
import my.LJExport.runtime.url.UrlUtil;

public class ArchiveOrgQuery
{
    private static final ObjectMapper mapper = new ObjectMapper();

    /*
     * Query available acrhive.org snapshots 
     */
    public static List<KVEntry> querySnapshots(String originalUrl, int limit) throws Exception
    {
        List<KVEntry> result = new ArrayList<>();

        String cdxQueryUrl = String.format("https://web.archive.org/cdx/search/cdx?output=json&fl=timestamp,original,statuscode&" +
                "filter=statuscode:200&matchType=exact&limit=%d&url=%s", limit, UrlUtil.encodeSegment(originalUrl));

        String json = load_json(cdxQueryUrl, null);
        if (json == null)
            return null;
        
        while (json.endsWith("\n") || json.endsWith("\r"))
        {
            if (json.endsWith("\n"))
                json = Util.stripTail(json, "\n");
            else
                json = Util.stripTail(json, "\r");
        }
        if (json.trim().equals("[]"))
            return null;

        JsonNode jroot = mapper.readTree(json);

        // Unexpected JSON structure
        if (!jroot.isArray() || jroot.size() < 1 || !jroot.get(0).isArray())
            throw new IllegalArgumentException("Unexpected JSON structure: " + json);

        // Parse header and determine column indexes
        JsonNode header = jroot.get(0);
        int idxTimestamp = -1, idxOriginal = -1, idxStatus = -1;

        for (int i = 0; i < header.size(); i++)
        {
            String name = header.get(i).asText();
            switch (name)
            {
            case "timestamp":
                idxTimestamp = i;
                break;
            case "original":
                idxOriginal = i;
                break;
            case "statuscode":
                idxStatus = i;
                break;
            }
        }

        if (idxTimestamp == -1 || idxOriginal == -1 || idxStatus == -1)
            throw new IllegalArgumentException("Missing expected columns in header");

        // Iterate through data rows
        for (int i = 1; i < jroot.size(); i++)
        {
            JsonNode row = jroot.get(i);
            if (!row.isArray() || row.size() != header.size())
                throw new Exception("Malformed row at index " + i);

            String timestamp = row.get(idxTimestamp).asText();
            String original = row.get(idxOriginal).asText();
            String status = row.get(idxStatus).asText();

            if (timestamp == null || original == null || status == null)
                throw new IllegalArgumentException("Unexpected JSON structure");

            if (!status.equals("200"))
                continue;

            result.add(new KVEntry(timestamp, original));
        }

        KVEntry.sortByKey(result);
        return result;
    }

    private static String load_json(String href, String referer) throws Exception
    {
        Web.Response r = LinkRedownloader.redownload_json(href, referer);
        if (r == null || r.code != 200)
            return null;

        Charset cs = r.extractCharset(true);
        if (cs == null)
            throw new Exception("Incompatible charset");
        String json = new String(r.binaryBody, cs);
        return json;
    }
}
