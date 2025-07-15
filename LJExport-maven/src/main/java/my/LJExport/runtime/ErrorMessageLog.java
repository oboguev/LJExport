package my.LJExport.runtime;

import java.util.HashSet;
import java.util.Set;

public class ErrorMessageLog
{
    private Set<String> contains = new HashSet<>();
    private StringBuilder sb = new StringBuilder();

    public synchronized void add(String s)
    {
        if (!contains.contains(s))
        {
            if (sb.length() != 0)
            {
                sb.append("--------------------------------------------------------------------");
                sb.append("\n");
            }
            
            sb.append(s);
            sb.append("\n");
            
            contains.add(s);
        }
    }

    @Override
    public synchronized String toString()
    {
        return sb.toString();
    }

    public synchronized long length()
    {
        return sb.length();
    }
}
