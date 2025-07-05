package my.LJExport.runtime;

import java.util.*;

public class CaseCollisions
{
    private final Map<String, String> firstSeen = new HashMap<>();
    private final Set<String> conflicts = new HashSet<>();

    public void add(String fn)
    {
        String lcfn = fn.toLowerCase(Locale.ROOT);

        String existing = firstSeen.putIfAbsent(lcfn, fn);
        if (existing != null)
        {
            conflicts.add(existing);
            conflicts.add(fn);
        }
    }

    public Set<String> conflicts()
    {
        return conflicts;
    }

    public void reset()
    {
        firstSeen.clear();
        conflicts.clear();
    }
}
