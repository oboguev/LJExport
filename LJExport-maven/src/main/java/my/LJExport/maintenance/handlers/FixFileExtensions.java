package my.LJExport.maintenance.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.file.FileTypeDetector;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;

/*
 * Fix linked file extensions according to their actual content. 
 * Scan IMG.SRC and A.HREF links.
 * If link does not have extension in it s name, or points to a file of different type
 * than implied by extension, then adjust the link.
 * Adjust repository map file as well.
 * 
 * Execute AFTER ResolveLinkCaseDifferences
 *     and AFTER FixDirectoryLinks.
 */
public class FixFileExtensions extends MaintenanceHandler
{
    private static boolean DryRun = true; // ###

    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Fix linked file extensions");
        super.beginUsers("Fix linked file extensions");
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

    @Override
    protected void beginUser() throws Exception
    {
        txLog.writeLine("Starting user " + Config.User);
        super.beginUser();
        build_lc2ac();
        updatedMap = false;
        loadLinkMapFile();
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
            String href = JSOUP.getAttribute(n, attr);

            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href, true);
            if (linkInfo == null)
                continue;

            String ac = file_lc2ac.get(linkInfo.linkFullFilePath.toLowerCase());
            if (ac == null)
            {
                String msg = String.format("Link file/dir [%s] is not present in the repository map, href=[%s], filepath=[%s]",
                        Config.User, href, linkInfo.linkFullFilePath);

                if (DryRun)
                {
                    trace(msg);
                    continue;
                }
                else
                {
                    throwException(msg);
                }
            }

            if (!ac.equals(linkInfo.linkFullFilePath))
                throwException("Mismatching link case");

            /*
             * Detect implied file extension from actual file content 
             */
            byte[] content = Util.readFileAsByteArray(linkInfo.linkFullFilePath);
            String contentExtension = FileTypeDetector.fileExtensionFromActualFileContent(content);
            if (contentExtension == null || contentExtension.length() == 0)
                continue;
            
            /*
             * Get extension from file name 
             */
            File fp = new File(linkInfo.linkFullFilePath);
            String fnExt =  getFileExtension(fp.getName());
            if (fnExt != null && (fnExt.length() == 0 || fnExt.length() > 4))
                fnExt = null;
            
            /*
             * If it is not one of common media extensions, disregard it  
             */
            if (fnExt != null && !FileTypeDetector.commonExtensions().contains(fnExt))
                fnExt = null;
            
            /*
             * If it is equivalent to detected file content, do not make any change  
             */
            if (fnExt != null && FileTypeDetector.isEquivalentExtensions(fnExt, contentExtension))
                continue;
            
            /*
             * Strip file extension if existed and append new extension
             */
            String newLinkFullFilePath = linkInfo.linkFullFilePath;
            String newref = href;
            
            if (fnExt != null)
            {
                String tail = "." + fnExt;
                tail = tail.toLowerCase();
                
                if (!newLinkFullFilePath.toLowerCase().endsWith(tail))
                    throwException("Internal error check");
                if (!newref.toLowerCase().endsWith(tail))
                    throwException("Internal error check");
                
                newLinkFullFilePath = newLinkFullFilePath.substring(0, newLinkFullFilePath.length() - tail.length());
                newref = newref.substring(0, newref.length() - tail.length());
            }
            
            newLinkFullFilePath += "." + contentExtension;
            newref += "." + contentExtension;
            
            /*
             * Check for conflicts
             */
            if (new File(newLinkFullFilePath).exists())
                throwException("Target file already exists on disk: " + newLinkFullFilePath);
            
            // ### check if target file already exists in map -> exception
            
            // ### txLog renaming file
            // ### rename file linkInfo.linkFullFilePath -> newname
            // ### txLog complete
            
            // ### fix link
            // ### updated = true
            
            // ### fix map
            // ### updatedMap = true
            
            // ### what if ALREADY renamed previous???
        }

        return updated;
    }

    /* ===================================================================================================== */

    private String getFileExtension(String fn)
    {
        int dotIndex = fn.lastIndexOf('.');
        // no extension or dot is at the end
        if (dotIndex == -1 || dotIndex == fn.length() - 1)
            return null;
        else
            return fn.substring(dotIndex + 1);
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