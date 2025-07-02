package my.LJExport.profile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

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
    private final String profileDir;
    private final File fpProfileDir;
    private PageParserDirectBase parser;

    public ReadProfile() throws Exception
    {
        userRoot = Config.DownloadRoot + File.separator + Config.User;
        linksDir = userRoot + File.separator + "links";
        profileDir = userRoot + File.separator + "profile";
        fpProfileDir = new File(profileDir).getCanonicalFile();
    }

    public void readAll() throws Exception
    {
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
        Node el = findRequiredPivotElement("font", "Userpics");
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
        Node el = findRequiredPivotElement("font", "Memorable Entries");
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
                String fn = SafeFileName.composeFileName(title, ".html");

                File fp = new File(fpMemoriesDir, fn);
                if (fp.exists())
                {
                    fn = SafeFileName.guidFileName(".html");
                    fp = new File(fpProfileDir, "memories");
                    fp = new File(fp, fn);
                }
                loadMemoriesEntryPage(href, fp.getCanonicalFile(), title);

                JSOUP.updateAttribute(an, "href", "memories/" + fn);
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

        Node el = findRequiredPivotElement("font", "Memorable Entries");
        parser.removeProfilePageJunk(Config.User + " - Memories - " + title, el);
        
        // ### deletes excess, leaves empty page

        Node pageRoot = parser.pageRoot;
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "form"));

        parser.setLinkReferencePrefix(LinkDownloader.LINK_REFERENCE_PREFIX_PROFILE_DOWN_1);
        parser.downloadExternalLinks(parser.pageRoot, linksDir, AbsoluteLinkBase.from(finalUrl.get()));

        // ### change "a" links to own journal to local 

        String html = JSOUP.emitHtml(parser.pageRoot);
        if (!fp.getParentFile().exists())
            fp.getParentFile().mkdirs();

        Util.writeToFileSafe(fp.getCanonicalPath(), html);
    }

    /* ================================================================================================== */

    private void readImages() throws Exception
    {
        String url = String.format("%s/pics/catalog", LJUtil.userBase());
        
        AtomicReference<String> finalUrl = new AtomicReference<>(); 
        parser = new PageParserDirectBasePassive();
        parser.rid = parser.rurl = null;
        parser.pageSource = load(url, standardHeaders(), finalUrl);
        parser.parseHtmlWithBaseUrl(finalUrl.get());

        Node el = JSOUP.exactlyOne(JSOUP.findElementsWithClass(parser.pageRoot, "div", "b-pics"));
        parser.removeProfilePageJunk(Config.User + " - Pictures", el);

        parser.setLinkReferencePrefix(LinkDownloader.LINK_REFERENCE_PREFIX_PROFILE);
        parser.downloadExternalLinks(parser.pageRoot, linksDir, AbsoluteLinkBase.User);

        // ###

        String html = JSOUP.emitHtml(parser.pageRoot);
        if (!fpProfileDir.exists())
            fpProfileDir.mkdirs();

        Util.writeToFileSafe(new File(fpProfileDir, "images.html").getCanonicalPath(), html);
    }

    /* ============================================================================ */

    public Element findRequiredPivotElement(String tag, String text) throws Exception
    {
        for (Node n : JSOUP.findElements(parser.pageRoot, tag))
        {
            Element el = JSOUP.asElement(n);

            if (el.ownText().equals(text) || el.text().equals(text))
                return el;
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
