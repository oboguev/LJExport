package my.LJExport.profile;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.readers.direct.PageParserDirectBase.AbsoluteLinkBase;
import my.LJExport.runtime.LJUtil;
import my.LJExport.runtime.SafeFileName;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.Web;
import my.LJExport.runtime.Web.Response;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.xml.JSOUP;

/*
 * Загрузить страницы профиля пользователя.
 * Входные сведения и состояние:
 * 
 *     Config.User
 *     Config.MangledUser
 *     Web инициализован и сконфигурирован (Web.init())
 *     желателен логин
 *     Util.mkdir(linksDir);
 *     LinkDownloader.init(linksDir);
 */
public class ReadProfile
{
    private final String userRoot;
    private final String linksDir;
    private final String pagesDir;
    private final String repostsDir;
    private final String profileDir;
    private final File fpProfileDir;
    private PageParserDirectBase parser;

    public ReadProfile() throws Exception
    {
        userRoot = Config.DownloadRoot + File.separator + Config.User;
        linksDir = userRoot + File.separator + "links";
        pagesDir = userRoot + File.separator + "pages";
        repostsDir = userRoot + File.separator + "reposts";
        profileDir = userRoot + File.separator + "profile";
        fpProfileDir = new File(profileDir).getCanonicalFile();
    }

    public void readAll() throws Exception
    {
        Util.out(">>> Downloading profile for user " + Config.User);

        if (Config.ReloadExistingFiles || !new File(fpProfileDir, "profile.html").exists())
            readProfile();

        if (Config.ReloadExistingFiles || !new File(fpProfileDir, "userpics.html").exists())
            readUserpics();

        if (!Config.StandaloneSite)
        {
            if (Config.ReloadExistingFiles || !new File(fpProfileDir, "memories.html").exists())
                readMemories();

            if (Config.ReloadExistingFiles || !new File(fpProfileDir, "images.html").exists())
                readImages();
        }
    }

    /* ================================================================================================== */

    private void readProfile() throws Exception
    {
        String url;

        if (Config.StandaloneSite)
        {
            url = String.format("%s/profile", LJUtil.userBase());
        }
        else
        {
            url = String.format("%s/profile?socconns=friends&mode_full_socconns=1&comms=cfriends&admins=subscribersof",
                    LJUtil.userBase());
        }

        AtomicReference<String> finalUrl = new AtomicReference<>();
        parser = new PageParserDirectBasePassive();
        parser.rid = parser.rurl = null;
        parser.pageSource = load(url, standardHeaders(), finalUrl);
        parser.parseHtmlWithBaseUrl(finalUrl.get());

        Node el_1 = JSOUP.exactlyOne(JSOUP.findElementsWithClass(parser.pageRoot, "div", "b-profile"));
        Node el_2 = JSOUP.optionalOne(JSOUP.findElementsWithClass(parser.pageRoot, "div", "b-myuserpic"));
        Node el_3 = JSOUP.optionalOne(JSOUP.findElementsWithClass(parser.pageRoot, "div", "b-profile-userpic"));
        parser.removeProfilePageJunk(Config.User + " - Profile", el_1, el_2, el_3);
        parser.rectifyProfileUserpic();

        JSOUP.removeElements(parser.pageRoot, JSOUP.findElementsWithClass(parser.pageRoot, "ul", "b-profile-actions"));

        parser.setLinkReferencePrefix(LinkDownloader.LINK_REFERENCE_PREFIX_PROFILE);
        parser.downloadExternalLinks(parser.pageRoot, linksDir, AbsoluteLinkBase.User);

        String html = JSOUP.emitHtml(parser.pageRoot);
        if (!fpProfileDir.exists())
            fpProfileDir.mkdirs();

        Util.writeToFileSafe(new File(fpProfileDir, "profile.html").getCanonicalPath(), html);
        Util.noop();
    }

    /* ================================================================================================== */

    private void readUserpics() throws Exception
    {
        String url = String.format("https://www.livejournal.com/allpics.bml?user=%s", Config.User);

        AtomicReference<String> finalUrl = new AtomicReference<>();
        parser = new PageParserDirectBasePassive();
        parser.rid = parser.rurl = null;
        parser.pageSource = load(url, standardHeaders(), finalUrl);
        parser.parseHtmlWithBaseUrl(finalUrl.get());

        // <font size="+2" face="Verdana, Arial, Helvetica" color="#000066">Userpics</font>
        Node el = findRequiredPivotElement(parser.pageRoot, "font", "Userpics");
        parser.removeProfilePageJunk(Config.User + " - Userpics", el);

        parser.setLinkReferencePrefix(LinkDownloader.LINK_REFERENCE_PREFIX_PROFILE);
        parser.downloadExternalLinks(parser.pageRoot, linksDir, AbsoluteLinkBase.WWW_Livejournal);

        String html = JSOUP.emitHtml(parser.pageRoot);
        if (!fpProfileDir.exists())
            fpProfileDir.mkdirs();

        Util.writeToFileSafe(new File(fpProfileDir, "userpics.html").getCanonicalPath(), html);
    }

    /* ================================================================================================== */

    private void readMemories() throws Exception
    {
        String url = String.format("https://www.livejournal.com/tools/memories.bml?user=%s", Config.User);

        AtomicReference<String> finalUrl = new AtomicReference<>();
        parser = new PageParserDirectBasePassive();
        parser.rid = parser.rurl = null;
        parser.pageSource = load(url, standardHeaders(), finalUrl);
        parser.parseHtmlWithBaseUrl(finalUrl.get());

        // <font size="+2" face="Verdana, Arial, Helvetica" color="#000066">Memorable Entries</font>
        Node el = findRequiredPivotElement(parser.pageRoot, "font", "Memorable Entries");
        parser.removeProfilePageJunk(Config.User + " - Memories", el);

        Node pageRoot = parser.pageRoot;
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "form"));

        parser.setLinkReferencePrefix(LinkDownloader.LINK_REFERENCE_PREFIX_PROFILE);
        parser.downloadExternalLinks(parser.pageRoot, linksDir, AbsoluteLinkBase.WWW_Livejournal);

        File fpMemoriesDir = new File(fpProfileDir, "memories").getCanonicalFile();
        if (fpMemoriesDir.exists())
            Util.deleteDirectoryTree(fpMemoriesDir.getCanonicalPath());

        for (Node an : JSOUP.findElements(pageRoot, "a"))
        {
            String href = JSOUP.getAttribute(an, "href");
            if (isMemory(href))
            {
                String title = JSOUP.asElement(an).text();
                title = Util.despace(title);

                /* self-reference */
                if (title.equals("Uncategorized") && containsKeyword(href, ""))
                    continue;

                String fn = SafeFileName.composeFileName(title, ".html");

                File fp = new File(fpMemoriesDir, fn);
                while (fp.exists())
                {
                    fn = SafeFileName.guidFileName(".html");
                    fp = new File(fpMemoriesDir, fn);
                }
                loadMemoriesEntryPage(href, fp.getCanonicalFile(), title);

                JSOUP.updateAttribute(an, "href", "memories/" + fn);
                JSOUP.setAttribute(an, "original-href", href);
            }
        }

        String html = JSOUP.emitHtml(parser.pageRoot);
        if (!fpProfileDir.exists())
            fpProfileDir.mkdirs();

        Util.writeToFileSafe(new File(fpProfileDir, "memories.html").getCanonicalPath(), html);
    }

    private boolean isMemory(String href) throws Exception
    {
        return href != null && href.contains(".livejournal.com/tools/memories.bml?");
    }

    private void loadMemoriesEntryPage(String href, File fp, String title) throws Exception
    {
        PageParserDirectBase parser = null;

        AtomicReference<String> finalUrl = new AtomicReference<>();
        parser = new PageParserDirectBasePassive();
        parser.rid = parser.rurl = null;
        parser.pageSource = load(href, standardHeaders(), finalUrl);
        parser.parseHtmlWithBaseUrl(finalUrl.get());

        String pivot_title;
        if (title.equals("Uncategorized") && containsKeyword(href, "*"))
        {
            pivot_title = "Memorable Entries";
        }
        else
        {
            pivot_title = String.format("Memorable %s Entries", title);
        }

        Node el = findRequiredPivotElement(parser.pageRoot, "font", pivot_title);
        parser.removeProfilePageJunk(Config.User + " - Memories - " + title, el);

        JSOUP.removeElements(parser.pageRoot, JSOUP.findElements(parser.pageRoot, "form"));

        List<Node> delvec = new ArrayList<>();
        for (Node n : JSOUP.findElements(parser.pageRoot, "a"))
        {
            String aref = JSOUP.getAttribute(n, "href");
            if (aref != null)
            {
                aref = Util.stripProtocol(aref).toLowerCase();
                if (aref.startsWith("www."))
                    aref = Util.stripStart(aref, "www.");
                if (aref.startsWith("livejournal.com/tools/memories.bml?") ||
                        aref.startsWith("livejournal.com/tools/memadd.bml?") ||
                        aref.startsWith("/tools/memories.bml?") ||
                        aref.startsWith("/tools/memadd.bml?"))
                {
                    delvec.add(n);
                    stripBrackets(delvec, JSOUP.asElement(n));
                }
            }
        }
        JSOUP.removeElements(parser.pageRoot, delvec);

        parser.setLinkReferencePrefix(LinkDownloader.LINK_REFERENCE_PREFIX_PROFILE_DOWN_1);
        parser.downloadExternalLinks(parser.pageRoot, linksDir, AbsoluteLinkBase.from(finalUrl.get()));

        remapPageLinksToLocalFiles(parser.pageRoot);

        String html = JSOUP.emitHtml(parser.pageRoot);
        if (!fp.getParentFile().exists())
            fp.getParentFile().mkdirs();

        Util.writeToFileSafe(fp.getCanonicalPath(), html);
    }

    private boolean containsKeyword(String urlString, String keywordValue) throws Exception
    {
        URI uri = new URI(urlString);
        String query = uri.getRawQuery(); // get encoded query
        if (query == null)
            return false;

        String[] pairs = query.split("&");
        for (String pair : pairs)
        {
            int idx = pair.indexOf('=');
            if (idx != -1)
            {
                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                if (key.equals("keyword") && value.equals(keywordValue))
                    return true;
            }
        }

        return false;
    }

    private void stripBrackets(List<Node> delvec, Element ael)
    {
        List<Node> siblings = ael.parent().childNodes();
        int idx = siblings.indexOf(ael);

        if (idx > 0 && idx < siblings.size() - 1)
        {
            Node prev = siblings.get(idx - 1);
            Node next = siblings.get(idx + 1);

            if (prev instanceof TextNode && ((TextNode) prev).text().trim().equals("["))
            {
                if (next instanceof TextNode && ((TextNode) next).text().trim().equals("]"))
                {
                    // delvec.add(prev);
                    // delvec.add(next);
                    ((TextNode) prev).text(" ");
                    ((TextNode) next).text(" ");
                }
            }
        }
    }

    private Set<String> userBases()
    {
        Set<String> userBases = new HashSet<>();
        userBases.add(String.format("users.%s/%s/", Config.Site, Config.User));
        userBases.add(String.format("users.%s/%s/", Config.Site, Config.MangledUser));
        userBases.add(String.format("%s.%s/", Config.User, Config.Site));
        userBases.add(String.format("%s.%s/", Config.MangledUser, Config.Site));
        return userBases;
    }

    private void remapPageLinksToLocalFiles(Node root) throws Exception
    {
        Set<String> userBases = userBases();

        Map<String, String> pagesMap = makePagesMap(pagesDir);

        Map<String, String> repostsMap = null;
        File fpReposts = new File(repostsDir).getCanonicalFile();
        if (fpReposts.exists() && fpReposts.isDirectory())
            repostsMap = makePagesMap(repostsDir);

        for (Node n : JSOUP.findElements(root, "a"))
        {
            String href = JSOUP.getAttribute(n, "href");
            if (href != null)
                remapPageLinkToLocalFile(n, href, userBases, pagesMap, repostsMap);
        }
    }

    private void remapPageLinkToLocalFile(Node n,
            String href,
            Set<String> userBases,
            Map<String, String> pagesMap,
            Map<String, String> repostsMap) throws Exception
    {
        String fn = extractUserPageFilenameIfMatches(href, userBases);
        if (fn != null)
        {
            if (pageFileExists(pagesDir, pagesMap, fn))
            {
                remapPageLinkToLocalFile(n, "pages/" + pagesMap.get(fn), href);
            }
            else if (repostsMap != null && pageFileExists(repostsDir, repostsMap, fn))
            {
                remapPageLinkToLocalFile(n, "reposts/" + repostsMap.get(fn), href);
            }
        }
    }

    private void remapPageLinkToLocalFile(Node n, String relPath, String original_href) throws Exception
    {
        relPath = "../../" + relPath;
        JSOUP.updateAttribute(n, "href", relPath);
        JSOUP.setAttribute(n, "original-href", original_href);
    }

    private String extractUserPageFilenameIfMatches(String href, Set<String> userBases)
    {
        if (href == null)
            return null;

        String lowerHref = href.toLowerCase(Locale.ROOT);
        String workingHref = lowerHref;

        // Strip protocol if present
        if (workingHref.startsWith("http://"))
        {
            workingHref = workingHref.substring("http://".length());
        }
        else if (workingHref.startsWith("https://"))
        {
            workingHref = workingHref.substring("https://".length());
        }

        for (String base : userBases)
        {
            String lowerBase = base.toLowerCase(Locale.ROOT);

            if (workingHref.startsWith(lowerBase))
            {
                String remainder = workingHref.substring(lowerBase.length());

                // Check if remainder is purely digits + ".html"
                if (remainder.endsWith(".html"))
                {
                    String numPart = remainder.substring(0, remainder.length() - ".html".length());
                    if (!numPart.isEmpty() && numPart.chars().allMatch(Character::isDigit))
                    {
                        return numPart + ".html";
                    }
                }
            }
        }

        // No match
        return null;
    }

    public static Map<String, String> makePagesMap(String rootDir)
    {
        Map<String, String> pagesMap = new HashMap<>();
        Path rootPath = Paths.get(rootDir).toAbsolutePath().normalize();
        File root = rootPath.toFile();

        if (!root.exists() || !root.isDirectory())
            return pagesMap;

        makePagesMapTraverse(root, rootPath, pagesMap);
        return pagesMap;
    }

    private static void makePagesMapTraverse(File file, Path rootPath, Map<String, String> map)
    {
        if (file.isFile())
        {
            String name = file.getName();
            if (!map.containsKey(name))
            {
                Path relativePath = rootPath.relativize(file.toPath());
                map.put(name, relativePath.toString().replace(File.separatorChar, '/'));
            }
        }
        else if (file.isDirectory())
        {
            File[] children = file.listFiles();
            if (children != null)
            {
                for (File child : children)
                    makePagesMapTraverse(child, rootPath, map);
            }
        }
    }

    private boolean pageFileExists(String filesDir, Map<String, String> filesMap, String fn)
    {
        String path = filesMap.get(fn);
        if (path == null)
            return false;
        File fp = new File(filesDir);
        fp = new File(fp, path.replace("/", File.separator));
        return fp.exists() && fp.isFile();
    }

    /* ================================================================================================== */

    private void readImages() throws Exception
    {
        String url = String.format("%s/pics/catalog", LJUtil.userBase());
        
        if (Config.True) // ###
            return;

        // ### 403 hokma.livejournal.com/pics/catalog
        // ### 403 ru-nationalism.livejournal.com/pics/catalog

        AtomicReference<String> finalUrl = new AtomicReference<>();
        parser = new PageParserDirectBasePassive();
        parser.rid = parser.rurl = null;
        parser.pageSource = load(url, standardHeaders(), finalUrl);
        parser.parseHtmlWithBaseUrl(finalUrl.get());

        Node el = JSOUP.exactlyOne(JSOUP.findElementsWithClass(parser.pageRoot, "div", "b-pics"));
        parser.removeProfilePageJunk(Config.User + " - Pictures", el);

        parser.setLinkReferencePrefix(LinkDownloader.LINK_REFERENCE_PREFIX_PROFILE);
        parser.downloadExternalLinks(parser.pageRoot, linksDir, AbsoluteLinkBase.User);

        File fpImagesDir = new File(fpProfileDir, "images").getCanonicalFile();
        if (fpImagesDir.exists())
            Util.deleteDirectoryTree(fpImagesDir.getCanonicalPath());

        Set<String> userBases = userBases();
        for (Node an : JSOUP.findElements(parser.pageRoot, "a"))
        {
            if (JSOUP.findElements(an, "img").size() != 0)
                continue;

            String href = JSOUP.getAttribute(an, "href");

            if (href != null && isAlbumUrl(href, userBases))
            {
                String title = JSOUP.asElement(an).text();
                title = Util.despace(title);
                if (title.isEmpty())
                    continue;

                String fn = SafeFileName.composeFileName(title, ".html");

                File fp = new File(fpImagesDir, fn);
                while (fp.exists())
                {
                    fn = SafeFileName.guidFileName(".html");
                    fp = new File(fpImagesDir, fn);
                }

                loadImageAlbum(href, fp.getCanonicalFile(), title);

                updateMatchingLinks(parser.pageRoot, "a", "href", href, "images/" + fn);
            }
        }

        String html = JSOUP.emitHtml(parser.pageRoot);
        if (!fpProfileDir.exists())
            fpProfileDir.mkdirs();

        Util.writeToFileSafe(new File(fpProfileDir, "images.html").getCanonicalPath(), html);
    }

    private void loadImageAlbum(String href, File fp, String title)
    {
        // ### album can have multiple pages
        // ### https://abcdefgh.livejournal.com/pics/catalog/342
    }

    private void updateMatchingLinks(Node root, String tag, String attr, String original, String replacement) throws Exception
    {
        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);
            if (href != null && href.equals(original))
            {
                JSOUP.updateAttribute(n, attr, replacement);
                JSOUP.setAttribute(n, "original-" + attr, original);
            }
        }
    }

    private boolean isAlbumUrl(String href, Set<String> userBases)
    {
        if (href == null)
            return false;

        String lowerHref = href.toLowerCase(Locale.ROOT);
        String workingHref = lowerHref;

        // Strip protocol if present
        if (workingHref.startsWith("http://"))
        {
            workingHref = workingHref.substring("http://".length());
        }
        else if (workingHref.startsWith("https://"))
        {
            workingHref = workingHref.substring("https://".length());
        }

        for (String base : userBases)
        {
            String lowerBase = base.toLowerCase(Locale.ROOT);

            if (workingHref.startsWith(lowerBase))
            {
                String remainder = workingHref.substring(lowerBase.length());

                if (remainder.startsWith("pics/catalog/"))
                    return true;
            }
        }

        return false;
    }

    /* ============================================================================ */

    private Element findRequiredPivotElement(Node root, String tag, String text) throws Exception
    {
        for (Node n : JSOUP.findElements(root, tag))
        {
            Element el = JSOUP.asElement(n);

            if (el.ownText().equals(text) || el.text().equals(text))
            {
                return el;
            }
        }

        throw new Exception("Unable to locate requested element " + tag + " [" + text + "]");
    }

    /* ============================================================================ */

    private Map<String, String> standardHeaders()
    {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.5");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/14");
        return headers;
    }

    @SuppressWarnings("unused")
    private String lastURL = null;

    @SuppressWarnings("unused")
    private String load(String url, AtomicReference<String> finalUrl) throws Exception
    {
        return load(url, null, finalUrl);
    }

    private String load(String url, Map<String, String> headers, AtomicReference<String> finalUrl) throws Exception
    {
        lastURL = url;

        boolean retry = true;
        int retries = 0;

        for (int pass = 0;; pass++)
        {
            Util.unused(pass);

            Main.checkAborting();

            if (retry)
            {
                retry = false;
                retries++;
                pass = 0;
            }

            Response r = Web.get(url, headers);
            if (finalUrl != null)
                finalUrl.set(r.finalUrl);

            if (r.code == 204)
            {
                return null;
            }
            else if (r.code == 404 || r.code == 410 || r.code == 451)
            {
                // 404 may mean a suspended journal
                return null;
            }
            else if (r.code != 200)
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else if (parser.isBadGatewayPage(r.body))
            {
                if (retries > 5)
                    return null;
                retry = true;
                Thread.sleep(1000 * (1 + retries));
            }
            else
            {
                return r.body;
            }
        }
    }
}
