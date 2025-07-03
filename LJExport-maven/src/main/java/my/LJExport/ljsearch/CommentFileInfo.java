package my.LJExport.ljsearch;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Comparator;

public class CommentFileInfo
{
    public String user;
    public int yyyy, mm, rid, tid;
    public String fullPath;
    public String relativePath;
    public Instant timestamp;

    public static List<String> getUsers(List<CommentFileInfo> list)
    {
        return list.stream()
                .map(info -> info.user)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public static List<CommentFileInfo> selectByUser(List<CommentFileInfo> list, String user)
    {
        return list.stream()
                .filter(info -> info.user.equals(user))
                .collect(Collectors.toList());
    }

    public static List<CommentFileInfo> sortByTimestamp(List<CommentFileInfo> list)
    {
        List<CommentFileInfo> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparing(info -> info.timestamp));
        return copy;
    }
}