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
            Util.out("Rename  " + rel);
            Util.out("    to  " + newrel);
            Util.out("");

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
        String pclastExt = URLCodec.fullyDecodeMixed(components[components.length - 1]);

        for (int k = 1; k < components.length; k++)
            components[k] = LinkDownloader.makeSanePathComponent(components[k]);
        // String pc1 = components[0];
        // String pc2 = components[1];
        String pclast = components[components.length - 1];

        String newrel = null;

        {
            String[] xc = components.clone();
            reapplyExtension(xc, host, pclastExt);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
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
            xc[3] = pclast;
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        if (host.toLowerCase().endsWith(".yimg.com"))
        {
            String[] pcs = extractRemainderAfter(components, "http%3A");
            if (pcs == null)
                pcs = extractRemainderAfter(components, "https%3A");
            if (pcs != null && pcs.length >= 1)
                components = concat(host, pcs);
        }

        if (components.length >= 3)
        {
            String[] xc = new String[3];
            xc[0] = components[0];
            xc[1] = "@@@";
            xc[2] = LinkDownloader.makeSanePathComponent(pclast);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        if (components.length >= 3)
        {
            String[] xc = components.clone();
            xc[xc.length - 1] = "x - " + Util.uuid();
            reapplyExtension(xc, host, pclastExt);
            newrel = recompose(xc, "/");
            if (newrel.length() <= MaxRelativeFilePath)
                return newrel;
        }

        // ###

        return rel;
    }

    @SuppressWarnings("unused")
    private String makeSaneRelativeUnixPath(String path, String separator) throws Exception
    {
        return makeSaneRelativeUnixPath(path.split(separator), separator);
    }

    private String makeSaneRelativeUnixPath(String[] components, String separator) throws Exception
    {
        StringBuilder path = new StringBuilder();

        for (String x : components)
        {
            if (path.length() != 0)
                path.append(separator);
            path.append(LinkDownloader.makeSanePathComponent(x));
        }

        return path.toString();
    }
    
    private void reapplyExtension(String[] xc, String host, String pclast) throws Exception
    {
        String ext = LinkDownloader.getFileExtension(pclast);

        if (ext == null && host.startsWith("sun") && host.endsWith(".userapi.com"))
        {
            URI uri = new URI(URLCodec.fullyDecodeMixed(pclast));
            if (uri.getPath() != null)
                ext = LinkDownloader.getFileExtension(uri.getPath());
        }

        if (ext != null && !xc[xc.length - 1].toLowerCase().endsWith("." + ext.toLowerCase()))
            xc[xc.length - 1] += "." + ext;
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

    private String[] concat(String[] sa, String s)
    {
        if (sa == null || s == null)
            throw new NullPointerException("Arguments must not be null");

        String[] result = new String[sa.length + 1];
        System.arraycopy(sa, 0, result, 0, sa.length);
        result[sa.length] = s;
        return result;
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
