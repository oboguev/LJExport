package my.LJExport.readers.direct;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Node;
import org.json.JSONArray;
import org.json.JSONObject;

import my.LJExport.readers.Comment;
import my.LJExport.runtime.html.JSOUP;

public class CommentHelper
{
    public static List<Comment> extractCommentsBlockUnordered(Node pageRoot) throws Exception
    {
        String jsonString = extractCommentsBlockJson(pageRoot);
        if (jsonString == null)
            return null;
        
        return extractCommentsBlockUnordered(jsonString);
    }

    public static List<Comment> extractCommentsBlockUnordered(String jsonString) throws Exception
    {
        JSONObject jo = new JSONObject(jsonString);
        JSONArray jcs = jo.getJSONArray("comments");
        if (jcs == null)
            return null;
        
        List<Comment> list = null;
        
        for (int k = 0; k < jcs.length(); k ++)
        {
            JSONObject jx = (JSONObject) jcs.get(k);
            if (list == null)
                list = new ArrayList<>();
            list.add(Comment.from(jx, true, 0));
        }
        
        return list;
    }
    
    public static List<Comment> extractCommentsBlockUnordered(String jsonString, Comment cload) throws Exception
    {
        JSONObject jo = new JSONObject(jsonString);
        JSONArray jcs = jo.getJSONArray("comments");
        if (jcs == null)
            return null;
        
        List<Comment> list = null;
        
        if (jcs.length() == 0)
            return list;
        
        Comment c0 = Comment.from((JSONObject) jcs.get(0), false, 0);
        
        if (c0.thread == null || !c0.thread.equals(cload.thread))
            throw new Exception("Mismatching root comment in a response to expand comment thread");
        
        if (c0.level == null)
            throw new Exception("Root comment in a response to expand comment thread has no level value");
        
        for (int k = 0; k < jcs.length(); k ++)
        {
            JSONObject jx = (JSONObject) jcs.get(k);
            if (list == null)
                list = new ArrayList<>();
            list.add(Comment.from(jx, true, cload.level - c0.level));
        }
        
        return list;
    }

    public static String extractCommentsBlockJson(Node pageRoot) throws Exception
    {
        String json = null;

        for (Node n : JSOUP.findElements(JSOUP.flatten(pageRoot), "script"))
        {
            final String prefix = "Site.page = {";
            final String postfix = "};";

            String stype = JSOUP.getAttribute(n, "type");
            if (stype != null && stype.contains("text/javascript"))
            {
                String s = n.toString();
                int k = s.indexOf(prefix);
                if (k == -1)
                    continue;
                s = s.substring(k + prefix.length());
                k = s.indexOf(postfix);
                if (k == -1)
                    continue;
                s = s.substring(0, k);

                if (json != null)
                    throwRuntimeException("Multiple comment blocks");

                json = "{" + s + "}";
            }
        }

        return json;
    }

    private static void throwRuntimeException(String msg)
    {
        throw new RuntimeException(msg);
    }
}
