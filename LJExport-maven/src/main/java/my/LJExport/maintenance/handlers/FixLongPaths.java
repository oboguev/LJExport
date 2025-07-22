package my.LJExport.maintenance.handlers;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.RelativeLink;
import my.LJExport.runtime.url.URLCodec;

public class FixLongPaths extends MaintenanceHandler
{
    private static boolean DryRun = true;

    public FixLongPaths() throws Exception
    {
    }

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Fix long file paths");
        super.beginUsers("Fixing long file paths");
        txLog.writeLine(String.format("Executing FixLongPaths in %s mode", DryRun ? "DRY" : "WET"));
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    private Map<String, String> file_lc2ac = new HashMap<>();
    private Map<String, String> dir_lc2ac = new HashMap<>();
    private boolean updatedMap = false;
    private List<LinkMapEntry> linkMapEntries;
    private Map<String, List<LinkMapEntry>> relpath2entry;
    private Map<String, String> alreadyRenamed = new HashMap<>(); // rel -> rel

    private static final int MaxFilePath = 250;
    private final int MaxRelativeFilePath = MaxFilePath - this.linkDirSep.length();

    @Override
    protected void beginUser() throws Exception
    {
        /* clear for new user */
        file_lc2ac = new HashMap<>();
        dir_lc2ac = new HashMap<>();
        updatedMap = false;
        linkMapEntries = null;
        relpath2entry = null;
        alreadyRenamed = new HashMap<>(); // rel -> rel

        txLog.writeLine("Starting user " + Config.User);
        super.beginUser();
        build_lc2ac();
        updatedMap = false;
        loadLinkMapFile();

        for (String path : file_lc2ac.keySet())
        {
            path = file_lc2ac.get(path);
            if (path.length() >= MaxFilePath)
                analyzeLongFilePath(path);
        }

        // ### build rename list
        // ### save it to file unless aleady exists (KV)
        // ### apply rename list to HTML
        // ### enduser: apply rename list to renames
        // ###          apply rename list to link map
        // ###          save link map
        // ### delete rename list file

        // ### if not in map, add to map using original path
        // ### renaming by copy + delete
    }

    @Override
    protected void endUser() throws Exception
    {
        if (updatedMap && !DryRun)
        {
            String mapFilePath = this.linkDir + File.separator + LinkDownloader.LinkMapFileName;

            txLog.writeLine("updating links map " + mapFilePath);
            String content = FileBackedMap.recomposeMapFile(linkMapEntries);
            Util.writeToFileVerySafe(mapFilePath, content);
            txLog.writeLine("updated OK");
        }

        // ### recursively remove empty dirs using dir_lc2ac.values() sorted by length

        txLog.writeLine("Completed user " + Config.User);
    }

    private void build_lc2ac() throws Exception
    {
        for (String fp : Util.enumerateFiles(linkDir, null))
        {
            fp = linkDir + File.separator + fp;
            file_lc2ac.put(fp.toLowerCase(), fp);
        }

        for (String fp : Util.enumerateDirectories(linkDir))
        {
            fp = linkDir + File.separator + fp;
            dir_lc2ac.put(fp.toLowerCase(), fp);
        }

        /*
         * consistency validation
         */
        for (String fn : file_lc2ac.keySet())
            if (dir_lc2ac.containsKey(fn))
                throwException("Error enumerating files and directories");

        for (String fn : dir_lc2ac.keySet())
            if (file_lc2ac.containsKey(fn))
                throwException("Error enumerating files and directories");
    }

    private void loadLinkMapFile() throws Exception
    {
        String mapFilePath = this.linkDir + File.separator + LinkDownloader.LinkMapFileName;
        linkMapEntries = FileBackedMap.readMapFile(mapFilePath);

        relpath2entry = new HashMap<>();

        for (LinkMapEntry e : linkMapEntries)
        {
            String relpath = e.value;

            if (relpath.contains("\\") || relpath.endsWith("/"))
                throwException("Invalid map entry");

            List<LinkMapEntry> list = relpath2entry.get(relpath.toLowerCase());
            if (list == null)
            {
                list = new ArrayList<LinkMapEntry>();
                relpath2entry.put(relpath.toLowerCase(), list);
            }
            list.add(e);
        }
    }

    /* ===================================================================================================== */

    private void analyzeLongFilePath(String path) throws Exception
    {
        String rel = this.abs2rel(path);
        String newrel = makeShorterFileRelativePath(rel);

        // ### check for already existing newrel
        // ### monitor collisions with added

        Map<String, String> lc_newrel2path = new HashMap<>();

        if (rel.equals(newrel))
        {
            Util.err("Cannot rename  " + rel);
            Util.err("");
        }
        else
        {
            boolean print = true;

            if (rel.contains(".userapi.com/"))
                print = false;

            if (rel.startsWith("imgprx.livejournal.net/") || rel.startsWith("xc3.services.livejournal.com/"))
                print = false;

            if (print)
            {
                Util.out("Rename  " + rel);
                Util.out("    to  " + newrel);
                Util.out("");
            }

            String newabs = this.rel2abs(newrel);
            File fp = new File(newabs).getCanonicalFile();
            if (fp.exists() && !isSameFileContent(fp.getCanonicalPath(), path))
            {
                // throwException("File already exists and has different content: " + fp.getAbsolutePath());
                Util.err("File already exists and has different content, old: " + path);
                Util.err("                                               new: " + fp.getCanonicalPath());
            }

            if (lc_newrel2path.containsKey(newrel.toLowerCase()))
            {
                Util.err("File already exists (double rename), newrel: " + newrel);
                Util.err("                                     path 1: " + lc_newrel2path.get(newrel.toLowerCase()));
                Util.err("                                     path 2: " + path);
            }

            lc_newrel2path.put(newrel.toLowerCase(), path);
        }
    }

    private String makeShorterFileRelativePath(String rel) throws Exception
    {
        String[] components = rel.split("/");

        String host = components[0];

        for (int k = 1; k < components.length; k++)
            components[k] = URLCodec.fullyDecodeMixed(components[k]);

        String pc1 = components.length <= 1 ? null : components[1];
        String pc2 = components.length <= 2 ? null : components[2];
        // String pc3 = components.length <= 3 ? null : components[3];
        String pclast = components[components.length - 1];

        String newrel = null;

        if (host.startsWith("sun") && host.endsWith(".userapi.com") ||
                host.startsWith("scontent") && host.endsWith(".fbcdn.net"))
        {
            URI uri = new URI(pclast);

            if (uri.getPath() != null)
            {
                String[] xc = components.clone();
                xc[xc.length - 1] = uri.getPath();
                newrel = recompose(xc, "/");
                if (newrel.length() <= MaxRelativeFilePath)
                    return newrel;
            }
        }

        if ((host.equals("substackcdn.com") || host.equals("cdn.substack.com")) &&
                components.length >= 5 && pc1.equals("image") && pc2.equals("fetch"))
        {
            String xclast = null;
            
            if (pclast.startsWith("https://"))
            {
                xclast = Util.stripStart(pclast, "https://");
            }
            else if (pclast.startsWith("http://"))
            {
                xclast = Util.stripStart(pclast, "http://");
            }
            
            if (xclast != null)
            {
                String[] xc = concat(host, "image", "fetch");
                xc = concat(xc, xclast.split("/"));
                xc = saneExceptFirst(xc, true);
                newrel = recompose(xc, "/");
                if (newrel.length() <= MaxRelativeFilePath)
                    return newrel;
                
            }
        }

        if (host.toLowerCase().endsWith(".yimg.com"))
        {
            String[] pcs = extractRemainderAfter(components, "http:");
            if (pcs == null)
                pcs = extractRemainderAfter(components, "https:");
            if (pcs != null && pcs.length >= 1)
            {
                String[] xc = concat(host, pcs);
                xc = saneExceptFirst(xc, true);
                newrel = recompose(xc, "/");
                if (newrel.length() <= MaxRelativeFilePath)
                    return newrel;
            }
        }

        if (isBotchedImgPrx(components))
        {
            String[] xc = new String[4];
            xc[0] = components[0];
            xc[1] = "@@@";

            int folder = (int) (Math.random() * 100);
            if (folder >= 100)
                folder = 99;
            xc[2] = String.format("x-%02d", folder);
            xc[3] = sane(pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        if (Util.True)
        {
            String[] xc = saneExceptFirst(components, true);
            reapplyExtension(xc, pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        if (components.length >= 3)
        {
            String[] xc = new String[3];
            xc[0] = components[0];
            xc[1] = "@@@";
            xc[2] = sane(pclast);
            reapplyExtension(xc, pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        if (components.length >= 3)
        {
            String[] xc = saneExceptFirst(components, false);
            xc[xc.length - 1] = "x - " + Util.uuid();
            reapplyExtension(xc, pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        // ###

        return rel;
    }

    private String sane(String component) throws Exception
    {
        return LinkDownloader.makeSanePathComponent(component);
    }

    @SuppressWarnings("unused")
    private String[] saneAll(String[] components, boolean tryAvoidGuid) throws Exception
    {
        components = components.clone();

        String pclast = components[components.length -1];

        for (int k = 0; k < components.length; k++)
            components[k] = sane(components[k]);
        
        if (tryAvoidGuid)
            tryAvoidGuid(components, pclast);

        return components;
    }

    private String[] saneExceptFirst(String[] components, boolean tryAvoidGuid) throws Exception
    {
        components = components.clone();
        
        String pclast = components[components.length -1];

        for (int k = 1; k < components.length; k++)
            components[k] = sane(components[k]);
        
        if (tryAvoidGuid)
            tryAvoidGuid(components, pclast);

        return components;
    }
    
    private void tryAvoidGuid(String[] components, String pclast) throws Exception
    {
        if (!isLowercaseGuid(components[components.length -1]))
            return;

        String path = null;
        try
        {
            URI uri = new URI(URLCodec.fullyDecodeMixed(pclast));
            path = uri.getPath();
        }
        catch (Exception ex)
        {
            return;
        }
        
        if (path == null)
            return;
        
        components[components.length -1] = sane(path);
    }

    private void reapplyExtension(String[] xc, String pclast) throws Exception
    {
        String ext = LinkDownloader.getFileExtension(pclast);

        if (ext == null)
        {
            URI uri = null;
            try
            {
                uri = new URI(URLCodec.fullyDecodeMixed(pclast));
            }
            catch (Exception ex)
            {
                // disregard
            }
            if (uri != null && uri.getPath() != null)
                ext = LinkDownloader.getFileExtension(uri.getPath());
        }

        if (ext != null && !xc[xc.length - 1].toLowerCase().endsWith("." + ext.toLowerCase()))
            xc[xc.length - 1] += "." + ext;
    }

    private boolean isBotchedImgPrx(String[] components)
    {
        if (!components[0].equals("imgprx.livejournal.net"))
            return false;

        for (int k = 1; k < components.length; k++)
        {
            String pc = components[k];
            if (!pc.startsWith("x-") || !isLowercaseGuid(pc.substring(2)))
                return false;
        }

        return true;
    }

    public static boolean isLowercaseGuid(String s)
    {
        if (s == null || s.length() != 32)
            return false;

        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')))
                return false;
        }

        return true;
    }

    // String[] tokens = {"A", "B", "BREAK", "C", "D"};
    // String[] result = extractRemainderAfter(tokens, "BREAK");
    // result is ["C", "D"]
    public static String[] extractRemainderAfter(String[] tokens, String breaker)
    {
        if (tokens == null || breaker == null)
            throw new NullPointerException("Arguments must not be null");

        for (int i = 0; i < tokens.length; i++)
        {
            if (breaker.equals(tokens[i]))
            {
                int remainderLength = tokens.length - (i + 1);
                if (remainderLength <= 0)
                    return new String[0]; // no elements after breaker

                String[] remainder = new String[remainderLength];
                System.arraycopy(tokens, i + 1, remainder, 0, remainderLength);
                return remainder;
            }
        }

        return null; // breaker not found
    }
    
    private String recompose(String[] components, String separator) throws Exception
    {
        StringBuilder path = new StringBuilder();

        for (String x : components)
        {
            if (path.length() != 0)
                path.append(separator);
            path.append(x);
        }

        return path.toString();
    }

    private String[] concat(String[] sa1, String[] sa2)
    {
        if (sa1 == null || sa2 == null)
            throw new NullPointerException("Arguments must not be null");

        String[] result = new String[sa1.length + sa2.length];
        System.arraycopy(sa1, 0, result, 0, sa1.length);
        System.arraycopy(sa2, 0, result, sa1.length, sa2.length);
        return result;
    }

    private String[] concat(String s, String[] sa)
    {
        if (s == null || sa == null)
            throw new NullPointerException("Arguments must not be null");

        String[] result = new String[1 + sa.length];
        result[0] = s;
        System.arraycopy(sa, 0, result, 1, sa.length);
        return result;
    }

    @SuppressWarnings("unused")
    private String[] concat(String[] sa, String s)
    {
        if (sa == null || s == null)
            throw new NullPointerException("Arguments must not be null");

        String[] result = new String[sa.length + 1];
        System.arraycopy(sa, 0, result, 0, sa.length);
        result[sa.length] = s;
        return result;
    }
    
    private String[] concat(String ... s)
    {
        return s.clone();
    }

    /* ===================================================================================================== */

    @Override
    protected boolean onEnumFiles(String which, List<String> enumeratedFiles)
    {
        return false;
    }

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

        if (Util.True)
            return;

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
            String href = getLinkAttribute(n, attr);
            String href_original = href;

            if (href == null || !isLinksRepositoryReference(fullHtmlFilePath, href))
                continue;

            if (handleAlreadyRenamed(href, href_original, fullHtmlFilePath, n, tag, attr))
            {
                updated = true;
                continue;
            }

            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
            if (linkInfo == null)
                continue;

            // regular file?
            if (null != file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase()))
                continue;

            String ac = dir_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());

            if (ac != null && !ac.equals(linkInfo.linkFullFilePath))
                throwException("Mismatching link case");

        }

        return updated;
    }

    private boolean handleAlreadyRenamed(String href, String href_original, String fullHtmlFilePath, Node n, String tag,
            String attr) throws Exception
    {
        String rel = href2rel(href, fullHtmlFilePath);
        String rel_original = href2rel(href_original, fullHtmlFilePath);

        String newrel = this.alreadyRenamed.get(rel.toLowerCase());
        if (newrel == null)
            newrel = this.alreadyRenamed.get(rel_original.toLowerCase());

        if (newrel != null)
        {
            String newref = rel2href(newrel, fullHtmlFilePath);
            updateLinkAttribute(n, attr, newref);

            txLog.writeLine(changeMessage(tag, attr, href_original, newref));
            trace(changeMessage(tag, attr, href_original, newref));

            changeLinksMap(href_original, newref, false, fullHtmlFilePath);
            changeLinksMap(href, newref, false, fullHtmlFilePath);

            return true;
        }

        return false;
    }

    private void changeLinksMap(String href, String newref, boolean required, String fullHtmlFilePath) throws Exception
    {
        String rel = href2rel(href, fullHtmlFilePath);
        String newrel = href2rel(newref, fullHtmlFilePath);

        List<LinkMapEntry> list = relpath2entry.get(rel.toLowerCase());
        if (list == null || list.size() == 0)
        {
            if (required)
                throwException("Old link is missing in the map");
        }
        else
        {
            for (LinkMapEntry e : list)
            {
                if (e.value.equals(rel))
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("Changing [%s] LinksDir map  %s" + nl, Config.User, e.value));
                    sb.append(String.format("          %s            to  %s" + nl, spaces(Config.User), newrel));
                    trace(sb.toString());

                    e.value = newrel;
                    updatedMap = true;
                }
                else if (e.value.equalsIgnoreCase(rel))
                {
                    throwException("Misimatching LinkDir case");
                }
                else
                {
                    // already changed
                    Util.noop();
                }
            }
        }
    }

    private void redirect_dir2file(String fullHtmlFilePath, Node n, String tag, String attr, String href, String href_original,
            String ac) throws Exception
    {
        // redirect link to ac
        String newref = RelativeLink.fileRelativeLink(ac, fullHtmlFilePath, this.userDir);
        updateLinkAttribute(n, attr, newref);

        // trace
        txLog.writeLine(changeMessage(tag, attr, href_original, newref));
        trace(changeMessage(tag, attr, href_original, newref));

        // change map to ac
        String rel = href2rel(href, fullHtmlFilePath);
        String rel_original = href2rel(href_original, fullHtmlFilePath);
        String newrel = abs2rel(ac);
        boolean required = false;

        List<LinkMapEntry> list = relpath2entry.get(rel.toLowerCase());
        if (list == null || list.size() == 0)
        {
            if (required)
                throwException("Old link is missing in the map");
        }
        else
        {
            for (LinkMapEntry e : list)
            {
                if (e.value.equals(rel))
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("Changing [%s] LinksDir map  %s" + nl, Config.User, e.value));
                    sb.append(String.format("          %s            to  %s" + nl, spaces(Config.User), newrel));
                    trace(sb.toString());

                    e.value = newrel;
                    updatedMap = true;
                }
                else if (e.value.equalsIgnoreCase(rel))
                {
                    throwException("Misimatching LinkDir case");
                }
                else
                {
                    // already changed
                    Util.noop();
                }
            }
        }

        // add to alreadyRenamed
        alreadyRenamed.put(rel.toLowerCase(), newrel);
        alreadyRenamed.put(rel_original.toLowerCase(), newrel);

        Util.noop();
    }

    private String changeMessage(String tag, String attr, String href_original, String newref)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Changing [%s] HTML %s.%s from  %s" + nl, Config.User, tag, attr, href_original));
        sb.append(String.format("          %s       %s %s   to  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr), newref));
        return sb.toString();
    }

    /* ===================================================================================================== */

    private boolean isSameFileContent(String fp1, String fp2) throws Exception
    {
        byte[] ba1 = Util.readFileAsByteArray(fp1);
        byte[] ba2 = Util.readFileAsByteArray(fp2);
        return Arrays.equals(ba1, ba2);
    }

    private void trace(String msg)
    {
        errorMessageLog.add(msg);
        Util.err(msg);
    }

    @SuppressWarnings("unused")
    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }
}
