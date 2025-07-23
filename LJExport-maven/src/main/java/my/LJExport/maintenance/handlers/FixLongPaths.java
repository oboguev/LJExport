package my.LJExport.maintenance.handlers;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.maintenance.handlers.util.ShortFilePath;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.file.KVFile;
import my.LJExport.runtime.file.KVFile.KVEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.RelativeLink;

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
    private List<KVEntry> renames = null;

    private static final int MaxFilePath = 245;
    private final int MaxRelativeFilePath = MaxFilePath - linkDirSep.length();

    @Override
    protected void beginUser() throws Exception
    {
        txLog.writeLine(String.format("Beginning FixLongPaths in %s mode for user", DryRun ? "DRY" : "WET", Config.User));

        /* clear for new user */
        file_lc2ac = new HashMap<>();
        dir_lc2ac = new HashMap<>();
        updatedMap = false;
        linkMapEntries = null;
        relpath2entry = null;
        alreadyRenamed = new HashMap<>(); // rel -> rel

        txLog.writeLine("Starting user " + Config.User);
        super.beginUser();

        /* -------------------------------------------------- */

        build_lc2ac();
        renames = prepareRenames();

        // ### enduser: apply rename list to renames
        // ### renaming by copy + delete

        if (!DryRun)
        {
            Util.out("  >>> Deleting empty directories for user " + Config.User);
            trace("Deleting empty directories for user " + Config.User);

            deleteEmptyFolders(dir_lc2ac.values());

            Util.out("  >>> Deleted empty directories for user " + Config.User);
            trace("  >>> Deleted empty directories for user " + Config.User);
        }

        /* -------------------------------------------------- */

        updatedMap = false;
        loadLinkMapFile();

        // ###          apply rename list to link map
        // ###          save link map
        // ### if not in map, add to map using original path

        // ### apply rename list to HTML

        if (updatedMap && !DryRun)
        {
            String mapFilePath = this.linkDir + File.separator + LinkDownloader.LinkMapFileName;

            txLog.writeLine("updating links map " + mapFilePath);
            String content = FileBackedMap.recomposeMapFile(linkMapEntries);
            Util.writeToFileVerySafe(mapFilePath, content);
            txLog.writeLine("updated OK");
        }
    }

    @Override
    protected void endUser() throws Exception
    {
        txLog.writeLine(String.format("Completed FixLongPaths in %s mode for user", DryRun ? "DRY" : "WET", Config.User));
    }

    /* ===================================================================================================== */

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

    private List<KVEntry> prepareRenames() throws Exception
    {
        KVFile kvfile = new KVFile(this.linkDir + File.separator + "rename-history.txt");

        if (Util.True)
        {
            if (kvfile.exists())
            {
                List<KVEntry> list = kvfile.load(true);
                Util.out("Loaded existing rename history");
                trace("Loaded existing rename history");
                return list;
            }
        }

        Util.out("Generating rename instructions");
        trace("Generating rename instructions");

        List<KVEntry> list = new ArrayList<>();
        Map<String, String> lc_newrel2path = new HashMap<>();

        for (String path : file_lc2ac.keySet())
        {
            path = file_lc2ac.get(path);
            String rel = abs2rel(path);

            if (path.length() > MaxFilePath)
                list.add(new KVEntry(rel, makeShortFileRelPath(path, rel, lc_newrel2path)));
        }

        kvfile.save(list);

        Util.out("Generated rename instructions and saved them to rename history");
        trace("Generated rename instructions and saved them to rename history");

        return list;
    }

    private String makeShortFileRelPath(String path, String rel, Map<String, String> lc_newrel2path) throws Exception
    {
        ShortFilePath sfp = new ShortFilePath(MaxRelativeFilePath);

        String newrel = sfp.makeShorterFileRelativePath(rel);

        if (rel.equals(newrel) || newrel.length() > MaxRelativeFilePath)
        {
            Util.err("Cannot rename  " + rel);
            throwException("Cannot rename  " + rel);
        }

        /* --------------------------------------------------------------- */

        boolean good = true;

        do
        {

            good = true;

            /* --------------------------------------------------------------- */

            if (good)
            {
                String newabs = this.rel2abs(newrel);
                File fp = new File(newabs).getCanonicalFile();
                if (fp.exists() && !isSameFileContent(fp.getCanonicalPath(), path))
                {
                    if (Util.False)
                    {
                        Util.err("File already exists and has different content, old: " + path);
                        Util.err("                                               new: " + fp.getCanonicalPath());
                    }

                    good = false;
                }
            }

            /* --------------------------------------------------------------- */

            if (good)
            {
                if (lc_newrel2path.containsKey(newrel.toLowerCase()))
                {
                    if (Util.False)
                    {
                        Util.err("File already exists (double rename), newrel: " + newrel);
                        Util.err("                                     path 1: " + lc_newrel2path.get(newrel.toLowerCase()));
                        Util.err("                                     path 2: " + path);
                    }

                    good = false;
                }
            }

            /* --------------------------------------------------------------- */

            if (!good)
            {
                // regenerate newrel
                newrel = sfp.makeShorterFileRelativePathAfterCollision(rel);
            }
        }
        while (!good);

        lc_newrel2path.put(newrel.toLowerCase(), path);

        StringBuilder sb = new StringBuilder();
        sb.append("Rename  " + rel + nl);
        sb.append("    to  " + newrel + nl);

        boolean print = true;

        if (rel.contains(".userapi.com/"))
            print = false;

        if (rel.startsWith("imgprx.livejournal.net/") || rel.startsWith("xc3.services.livejournal.com/"))
            print = false;

        if (print || Util.True) // ###
            Util.out(sb.toString());

        trace(sb.toString());

        return newrel;
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

    private void deleteEmptyFolders(Collection<String> xset) throws Exception
    {
        List<String> xlist = new ArrayList<>(xset);

        // sort by longest first
        Collections.sort(xlist, new Comparator<String>()
        {
            @Override
            public int compare(String a, String b)
            {
                return Integer.compare(b.length(), a.length()); // Descending order
            }
        });

        for (String dir : xlist)
        {
            File fp = new File(dir).getCanonicalFile();

            if (fp.exists() && isEmptyDir(fp))
            {
                try
                {
                    Files.delete(fp.toPath());

                    if (fp.exists())
                    {
                        trace("Was unable to delete directory " + fp.getCanonicalPath());
                    }
                    else
                    {
                        trace("Deleted empty directory " + fp.getCanonicalPath());
                        Util.out("        Deleted empty directory " + fp.getCanonicalPath());
                    }
                }
                catch (Exception ex)
                {
                    trace("Was unable to delete directory " + fp.getCanonicalPath());
                }
            }
        }
    }

    private boolean isSameFileContent(String fp1, String fp2) throws Exception
    {
        byte[] ba1 = Util.readFileAsByteArray(fp1);
        byte[] ba2 = Util.readFileAsByteArray(fp2);
        return Arrays.equals(ba1, ba2);
    }

    private boolean isEmptyDir(File fp) throws Exception
    {
        return fp.isDirectory() && fp.list().length == 0;
    }

    private void trace(String msg) throws Exception
    {
        // errorMessageLog.add(msg);
        // Util.err(msg);
        traceWriter.write(msg + nl);
        traceWriter.flush();
    }

    @SuppressWarnings("unused")
    private void throwException(String msg) throws Exception
    {
        throw new Exception(msg);
    }
}
