package my.LJExport.maintenance.handlers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Node;

import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.file.FileBackedMap;
import my.LJExport.runtime.file.FileBackedMap.LinkMapEntry;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.links.LinkDownloader;

/*
 * Lowercase link file and dir names in:
 * 
 *   - link repository
 *   - link repository map file
 *   - HTML files properties A.HREF and IMG.SRC 
 *   
 * Do NOT actually use it !!!  
 */
public class LowercaseLinks extends MaintenanceHandler
{
    @Override
    protected void beginUsers() throws Exception
    {
        Util.out(">>> Lowercasing link file paths and names");
        super.beginUsers("Lowercasing link file paths and names");
    }

    @Override
    protected void endUsers() throws Exception
    {
        super.endUsers();
    }

    @Override
    protected void beginUser() throws Exception
    {
        super.beginUser();
        lowercaseLinkFilePaths();
        lowercaseLinksMap();
    }

    /*
     * Update A.HREF and IMG.SRC in HTML page files
     */
    @Override
    protected void processHtmlFile(String fullHtmlFilePath, String relativeFilePath, PageParserDirectBasePassive parser,
            List<Node> pageFlat) throws Exception
    {
        super.processHtmlFile(fullHtmlFilePath, relativeFilePath, parser, pageFlat);

        boolean update = false;

        for (Node n : JSOUP.findElements(pageFlat, "img"))
        {
            String href = JSOUP.getAttribute(n, "src");
            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
            if (linkInfo != null)
            {
                String lc = href.toLowerCase();
                if (!lc.equals(href))
                {
                    JSOUP.updateAttribute(n, "src", lc);
                    update = true;
                }
            }
        }

        for (Node n : JSOUP.findElements(pageFlat, "a"))
        {
            String href = JSOUP.getAttribute(n, "href");
            LinkInfo linkInfo = linkInfo(fullHtmlFilePath, href);
            if (linkInfo != null)
            {
                String lc = href.toLowerCase();
                if (!lc.equals(href))
                {
                    JSOUP.updateAttribute(n, "href", lc);
                    update = true;
                }
            }
        }

        if (update)
        {
            String html = JSOUP.emitHtml(parser.pageRoot);
            Util.writeToFileSafe(fullHtmlFilePath, html);
            trace("Updated HTML file " + fullHtmlFilePath);
        }
    }

    /* =================================================================================== */

    private void lowercaseLinkFilePaths() throws Exception
    {
        lowercaseFilesysNames(new File(this.linkDir), false);
        lowercaseFilesysNames(new File(this.linkDir), true);
    }

    private void lowercaseFilesysNames(File indir, boolean apply) throws Exception
    {
        if (!indir.exists() || !indir.isDirectory())
            throw new Exception("Unexpected: does not exist or not a directory: " + indir);

        String[] fns = indir.list();
        checkCaseClashes(indir, fns);

        if (apply)
        {
            for (String fn : fns)
            {
                String lc = fn.toLowerCase();
                if (!lc.equals(fn))
                    lowercaseFilesysName(indir, fn, lc);
            }
        }

        for (File fp : indir.listFiles())
        {
            if (fp.isDirectory())
                lowercaseFilesysNames(fp, apply);
        }
    }

    private void checkCaseClashes(File indir, String[] fns) throws Exception
    {
        Set<String> xs = new HashSet<>();

        for (String fn : fns)
        {
            String lc = fn.toLowerCase();
            if (xs.contains(lc))
                throw new Exception("File or dir name case clash in " + indir + ", file/dir " + fn);
            xs.add(lc);
        }
    }

    private void lowercaseFilesysName(File indir, String fn, String lcfn) throws Exception
    {
        String tname = "~~~ren~~~." + Util.uuid();

        if (!new File(indir, fn).exists())
            throw new Exception("Unexpected: file does not exist");

        if (new File(indir, tname).exists())
            throw new Exception("Unexpected: file exists");

        StringBuilder sb = new StringBuilder();
        sb.append("renaming in directory " + indir + nl);
        sb.append("                 from " + fn + nl);
        sb.append("                   to " + lcfn + nl);
        sb.append("                  via " + tname);
        txLog.writeLine(sb.toString());

        trace(String.format("Renaming LinksDir file %s => %s", 
                new File(indir, fn).toPath().toString(), 
                new File(indir, lcfn).toPath().toString()));
        
        Files.move(new File(indir, fn).toPath(), new File(indir, tname).toPath(), StandardCopyOption.ATOMIC_MOVE);
        Files.move(new File(indir, tname).toPath(), new File(indir, lcfn).toPath(), StandardCopyOption.ATOMIC_MOVE);
        txLog.writeLine("renamed OK");
    }

    /* =================================================================================== */

    private void lowercaseLinksMap() throws Exception
    {
        String mapFilePath = this.linkDir + File.separator + LinkDownloader.LinkMapFileName;
        boolean update = false;

        List<LinkMapEntry> list = FileBackedMap.readMapFile(mapFilePath);
        
        for (LinkMapEntry e : list)
        {
            String lc = e.value.toLowerCase();
            if (!lc.equals(e.value))
            {
                trace(String.format("Changing LinksDir map %s => %s", e.value, lc));
                e.value = lc;
                update = true;
            }
        }
        
        if (update)
        {
            txLog.writeLine("updating links map " + mapFilePath);
            String content = FileBackedMap.recomposeMapFile(list);
            Util.writeToFileVerySafe(mapFilePath, content);
            txLog.writeLine("updated OK");
        }
    }
    
    private void trace(String msg)
    {
        Util.err(msg);
    }
}
