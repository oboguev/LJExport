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
import my.LJExport.runtime.url.URLCodec;

public class FixLongPaths extends MaintenanceHandler
{
    static enum FixPhase
    {
        GenerateRenames, ExecuteRenames, RelocateLinksMap, FixHtmlPages
    }

    private static FixPhase phase = FixPhase.GenerateRenames;
    // private static FixPhase phase = FixPhase.ExecuteRenames;
    // private static FixPhase phase = FixPhase.RelocateLinksMap;
    // private static FixPhase phase = FixPhase.FixHtmlPages;

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

    private List<KVEntry> renames = null;
    private Map<String, String> renames_old2new = new HashMap<>();
    private Map<String, String> renames_lc_old2new = new HashMap<>();
    private Map<String, String> unused_renames_old_lc2ac = new HashMap<>();

    private static final int MaxFilePath = 245;
    private final int MaxRelativeFilePath = MaxFilePath - linksDirSep.length();

    @Override
    protected void beginUser() throws Exception
    {
        txLog.writeLine(String.format("Beginning FixLongPaths in %s mode for user %s, phase %s",
                DryRun ? "DRY" : "WET", Config.User, phase.name()));

        trace(String.format("Beginning FixLongPaths in %s mode for user %s, phase %s",
                DryRun ? "DRY" : "WET", Config.User, phase.name()));

        /* clear for new user */
        file_lc2ac = new HashMap<>();
        dir_lc2ac = new HashMap<>();
        updatedMap = false;
        linkMapEntries = null;
        relpath2entry = null;
        renames = null;
        renames_old2new = new HashMap<>();
        renames_lc_old2new = new HashMap<>();
        unused_renames_old_lc2ac = new HashMap<>();

        txLog.writeLine("Starting user " + Config.User);
        super.beginUser();

        /* -------------------------------------------------- */

        build_lc2ac();
        renames = prepareRenames();
        mapRenames();

        if (!DryRun && phase == FixPhase.ExecuteRenames)
        {
            Util.out("  >>> Executing rename instructions for user " + Config.User);
            trace("Executing rename instructions for user " + Config.User);

            executeRenames(renames);

            Util.out("  >>> Completed rename instructions for user " + Config.User);
            trace("Completed rename instructions for user " + Config.User);
        }

        if (!DryRun && phase == FixPhase.ExecuteRenames)
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

        if (phase == FixPhase.RelocateLinksMap)
        {
            applyRenamesToLinkMap();

            if (updatedMap && !DryRun)
            {
                String mapFilePath = this.linksDir + File.separator + LinkDownloader.LinkMapFileName;

                Util.out("Updating links map " + mapFilePath);
                txLog.writeLine("updating links map " + mapFilePath);

                String content = FileBackedMap.recomposeMapFile(linkMapEntries);
                Util.writeToFileVerySafe(mapFilePath, content);

                txLog.writeLine("updated OK");
                Util.out("    updated OK");
            }
        }
    }

    @Override
    protected void endUser() throws Exception
    {
        txLog.writeLine(String.format("Completed FixLongPaths in %s mode for user %s", DryRun ? "DRY" : "WET", Config.User));
        trace(String.format("Completed FixLongPaths in %s mode for user %s", DryRun ? "DRY" : "WET", Config.User));

        if (phase == FixPhase.FixHtmlPages)
        {

            StringBuilder sb = new StringBuilder();
            if (unused_renames_old_lc2ac.size() == 0)
            {
                sb.append("All renames have been used in HTML files" + nl);

                trace(sb.toString());
                Util.out(sb.toString());
            }
            else
            {
                sb.append("Renames unused in HTML files:" + nl);
                for (String rel : unused_renames_old_lc2ac.values())
                    sb.append("    " + rel + nl);

                trace(sb.toString());
                Util.err(sb.toString());
            }
        }
    }

    /* ===================================================================================================== */

    private void build_lc2ac() throws Exception
    {
        for (String fp : Util.enumerateFiles(linksDir, null))
        {
            if (isLinksRootFileRelativePathSyntax(fp))
                continue;

            fp = linksDir + File.separator + fp;
            file_lc2ac.put(fp.toLowerCase(), fp);
        }

        for (String fp : Util.enumerateDirectories(linksDir))
        {
            fp = linksDir + File.separator + fp;
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
        String mapFilePath = this.linksDir + File.separator + LinkDownloader.LinkMapFileName;
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
        KVFile kvfile = new KVFile(this.linksDir + File.separator + "rename-history.txt");

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

        if (phase != FixPhase.GenerateRenames)
            throwException("Rename insrtuctions have not been pre-generated");

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

        if (DryRun)
        {
            Util.out("Generated rename instructions");
            trace("Generated rename instructions");
        }
        else
        {
            kvfile.save(list);
            Util.out("Generated rename instructions and saved them to rename history");
            trace("Generated rename instructions and saved them to rename history");
        }

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

        if (rel.equals(newrel) || newrel.length() > MaxRelativeFilePath)
        {
            Util.err("Cannot rename  " + rel);
            throwException("Cannot rename  " + rel);
        }

        lc_newrel2path.put(newrel.toLowerCase(), path);

        StringBuilder sb = new StringBuilder();
        sb.append("Rename  " + rel + nl);
        sb.append("    to  " + newrel + nl);

        boolean print = true;

        if (rel.contains(".userapi.com/"))
            print = false;

        if (rel.startsWith("imgprx.livejournal.net/") || rel.startsWith("xc3.services.livejournal.com/"))
            print = false;

        if (print || Util.True)
            Util.out(sb.toString());

        trace(sb.toString());

        return newrel;
    }

    private void mapRenames() throws Exception
    {
        for (KVEntry e : renames)
        {
            renames_old2new.put(e.key, e.value);
            renames_lc_old2new.put(e.key.toLowerCase(), e.value);
            unused_renames_old_lc2ac.put(e.key.toLowerCase(), e.key);
        }
    }

    /* ===================================================================================================== */

    private void executeRenames(List<KVEntry> renames) throws Exception
    {
        for (KVEntry e : renames)
        {
            String srcrel = e.key;
            String dstrel = e.value;

            String srcabs = rel2abs(srcrel);
            String dstabs = rel2abs(dstrel);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Executing rename [%s]  %s" + nl, Config.User, srcrel));
            sb.append(String.format("              to  %s   %s" + nl, spaces(Config.User), dstrel));
            sb.append(String.format("            file  %s   %s" + nl, spaces(Config.User), srcabs));
            sb.append(String.format("              to  %s   %s" + nl, spaces(Config.User), dstabs));

            Util.out(sb.toString());
            trace(sb.toString());

            try
            {
                executeRename(srcabs, dstabs);
            }
            catch (Exception ex)
            {
                Util.err("Rename failed: " + ex.getLocalizedMessage());
                trace("Rename failed: " + ex.getLocalizedMessage());
                throwException("Rename failed", ex);
            }

            trace("Renamed OK");
        }
    }

    private void executeRename(String src, String dst) throws Exception
    {
        File fpsrc = new File(src).getCanonicalFile();
        File fpdst = new File(dst).getCanonicalFile();

        if (!fpsrc.exists() && fpdst.exists() && Config.False)
            return;

        fpdst.getParentFile().getCanonicalFile().mkdirs();

        byte[] ba = Util.readFileAsByteArray(src);
        Util.writeToFileSafe(dst, ba);

        Files.delete(fpsrc.toPath());
    }

    /* ===================================================================================================== */

    private void applyRenamesToLinkMap() throws Exception
    {
        for (KVEntry e : renames)
            applyRenamesToLinkMap(e.key, e.value);
    }

    private void applyRenamesToLinkMap(String src, String dst) throws Exception
    {
        List<LinkMapEntry> entries = relpath2entry.get(src.toLowerCase());

        if (entries != null)
        {
            for (LinkMapEntry e : entries)
            {
                e.value = dst;
                updatedMap = true;
            }
        }

        // if not in the map, add to the map using reconstructed original path
        if (entries == null || entries.size() == 0)
        {
            ShortFilePath sfp = new ShortFilePath(MaxRelativeFilePath);
            String url = sfp.reconstructURL(src);
            if (url != null)
            {
                linkMapEntries.add(new LinkMapEntry(url, dst));
                updatedMap = true;
            }
        }
    }

    /* ===================================================================================================== */

    @Override
    protected boolean onEnumFiles(String which, List<String> enumeratedFiles)
    {
        return phase == FixPhase.FixHtmlPages;
    }

    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

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
            String href_raw = getLinkAttributeUndecoded(n, attr);

            if (href == null || !isLinksRepositoryReference(fullHtmlFilePath, href))
                continue;

            if (isArchiveOrg())
            {
                /* ignore bad links due to former bug in archive loader */
                if (href.startsWith("../") && href.endsWith("../links/null"))
                    continue;
            }

            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href))
            {
                String rel = href2rel(href, fullHtmlFilePath);
                if (tryChange(rel, href, n, tag, attr, fullHtmlFilePath))
                {
                    updated = true;
                    continue;
                }
            }

            String href2 = URLCodec.encode(href).replace("%2F", "/");
            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href2))
            {
                String rel2 = href2rel(href2, fullHtmlFilePath);
                if (tryChange(rel2, href, n, tag, attr, fullHtmlFilePath))
                {
                    updated = true;
                    continue;
                }
            }

            String href3 = URLCodec.encodeFilename(href).replace("%2F", "/");
            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href3))
            {
                String rel3 = href2rel(href3, fullHtmlFilePath);
                if (tryChange(rel3, href, n, tag, attr, fullHtmlFilePath))
                {
                    updated = true;
                    continue;
                }
            }

            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href_raw))
            {
                String rel_raw = href2rel(href_raw, fullHtmlFilePath);
                if (tryChange(rel_raw, href_raw, n, tag, attr, fullHtmlFilePath))
                {
                    updated = true;
                    continue;
                }
            }

            href2 = URLCodec.encode(href_raw).replace("%2F", "/");
            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href2))
            {
                String rel2 = href2rel(href2, fullHtmlFilePath);
                if (tryChange(rel2, href_raw, n, tag, attr, fullHtmlFilePath))
                {
                    updated = true;
                    continue;
                }
            }

            href3 = URLCodec.encodeFilename(href_raw).replace("%2F", "/");
            if (!URLCodec.unixRelativePathContainsFilesysReservedChars(href3))
            {
                String rel3 = href2rel(href3, fullHtmlFilePath);
                if (tryChange(rel3, href_raw, n, tag, attr, fullHtmlFilePath))
                {
                    updated = true;
                    continue;
                }
            }
        }

        return updated;
    }

    private boolean tryChange(String rel, String href, Node n, String tag, String attr, String fullHtmlFilePath) throws Exception
    {
        String newrel = renames_lc_old2new.get(rel.toLowerCase());

        if (newrel != null)
        {
            String newref = rel2href(newrel, fullHtmlFilePath);
            updateLinkAttribute(n, attr, newref);

            String msg = changeMessage(tag, attr, href, newref, fullHtmlFilePath);
            trace(msg);
            Util.out(msg);

            unused_renames_old_lc2ac.remove(rel.toLowerCase());

            return true;
        }
        else
        {
            return false;
        }
    }

    private String changeMessage(String tag, String attr, String href_original, String newref, String fullHtmlFilePath)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Changing [%s] HTML %s.%s from  %s" + nl, Config.User, tag, attr, href_original));
        sb.append(String.format("          %s       %s %s   to  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr), newref));
        sb.append(String.format("          %s       %s %s   in  %s" + nl, spaces(Config.User), spaces(tag), spaces(attr),
                fullHtmlFilePath));
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
                        Util.err("        Was unable to delete directory " + fp.getCanonicalPath());
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

    private void throwException(String msg, Exception ex) throws Exception
    {
        throw new Exception(msg, ex);
    }
}
