package my.LJExport.readers.direct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

import my.LJExport.Config;
import my.LJExport.Main;
import my.LJExport.readers.CommentsTree;
import my.LJExport.readers.PageContentSource;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.http.Web;
import my.LJExport.runtime.links.LinkDownloader;
import my.LJExport.runtime.links.ShouldDownload;
import my.LJExport.runtime.lj.LJUtil;
import my.LJExport.runtime.lj.Sites;
import my.LJExport.runtime.synch.AppendToThreadName;
import my.LJExport.runtime.synch.FutureProcessor;
import my.LJExport.runtime.synch.ThreadsControl;
import my.LJExport.runtime.url.AwayLink;
import my.LJExport.runtime.url.UrlUtil;

public abstract class PageParserDirectBase
{
    public static enum AbsoluteLinkBase
    {
        User, WWW_Livejournal, None;

        public static AbsoluteLinkBase from(String url) throws Exception
        {
            String host = Util.urlHost(url).toLowerCase();
            if (host.equals("livejournal.com") || host.equals("www.livejournal.com"))
                return WWW_Livejournal;
            if (host.endsWith(".livejournal.com"))
                return User;
            throw new Exception("Unable to determine link basis for URL " + url); // ###
        }
    };

    private final PageContentSource pageContentSource;

    public PageParserDirectBase(PageContentSource pageContentSource)
    {
        this.pageContentSource = pageContentSource;
    }

    public PageParserDirectBase(PageParserDirectBase other)
    {
        this.pageContentSource = other.pageContentSource;

        this.npages = other.npages;
        this.hasComments = other.hasComments;
        this.pageRoot = other.pageRoot;
        this.pageSource = other.pageSource;
        this.rurl = other.rurl;
        this.rid = other.rid;
        this.commentsJson = other.commentsJson;

        this.cachedPageFlat = other.cachedPageFlat;
        this.cachedHead = other.cachedHead;
        this.cachedBody = other.cachedBody;
    }

    protected String getPageSource() throws Exception
    {
        return pageContentSource.getPageSource();
    }

    /* ============================================================== */

    public final static int COUNT_PAGES = (1 << 0);
    public final static int CHECK_HAS_COMMENTS = (1 << 1);
    public final static int REMOVE_MAIN_TEXT = (1 << 2);
    public final static int REMOVE_SCRIPTS = (1 << 3);
    public final static int EXTRACT_COMMENTS_JSON = (1 << 4);

    public int npages = -1;
    public Boolean hasComments = null;

    public Node pageRoot;
    public String pageSource;
    public String commentsJson;

    public String rurl;
    public String rid;

    private List<Node> cachedPageFlat;
    private Element cachedHead;
    private Element cachedBody;

    public void resetParser()
    {
        npages = -1;
        hasComments = null;

        pageRoot = null;
        pageSource = null;

        rurl = null;
        rid = null;

        resetCache();
    }

    private String linkReferencePrefix = LinkDownloader.LINK_REFERENCE_PREFIX_PAGES;

    public void setLinkReferencePrefix(String linkReferencePrefix)
    {
        this.linkReferencePrefix = linkReferencePrefix;
    }

    /* ============================================================== */

    public static void out(String s)
    {
        Main.out(s);
    }

    public static void err(String s)
    {
        Main.err(s);
    }

    public void parseHtml() throws Exception
    {
        parseHtml(this.pageSource);
    }

    public void parseHtml(String html) throws Exception
    {
        resetCache();
        this.pageSource = html;
        this.pageRoot = JSOUP.parseHtml(html);
        evalCache();
    }

    public void parseHtmlWithBaseUrl(String baseUrl) throws Exception
    {
        resetCache();
        this.pageRoot = JSOUP.parseHtml(this.pageSource, baseUrl);
        evalCache();
    }

    public void parseHtmlWithBaseUrl(String html, String baseUrl) throws Exception
    {
        resetCache();
        this.pageSource = html;
        this.pageRoot = JSOUP.parseHtml(html, baseUrl);
        evalCache();
    }

    public void resetCache()
    {
        cachedPageFlat = null;
        cachedHead = null;
        cachedBody = null;
    }

    public void evalCache() throws Exception
    {
        resetCache();
        this.cachedPageFlat = JSOUP.flatten(pageRoot);
        this.cachedHead = findHead();
        this.cachedBody = getBodyTag();
    }

    public List<Node> getCachedPageFlat() throws Exception
    {
        if (cachedPageFlat != null)
            return cachedPageFlat;
        else
            return JSOUP.flatten(pageRoot);
    }

    protected Element getCachedHead()
    {
        return cachedHead;
    }

    protected Element getCachedBody()
    {
        return cachedBody;
    }

    /* ============================================================== */

    public boolean isBadGatewayPage(String html) throws Exception
    {
        if (html.contains("Bad Gateway:") || html.contains("Gateway Timeout"))
        {
            Node pageRoot = JSOUP.parseHtml(html);
            List<Node> vel = JSOUP.findElements(pageRoot, "body");
            if (vel.size() != 1)
                throw new Exception("Unable to find BODY element in the html page");

            for (Node n : JSOUP.getChildren(vel.get(0)))
            {
                if (n instanceof TextNode)
                {
                    TextNode tn = (TextNode) n;
                    if (tn.text().contains("Bad Gateway:") || tn.text().contains("Gateway Timeout"))
                        return true;
                }
            }
        }

        return false;
    }

    /*static*/ public boolean downloadExternalLinks(Node root, AbsoluteLinkBase absoluteLinkBase) throws Exception
    {
        if (Main.linkDownloader == null || !Main.linkDownloader.isInitialized() || Config.DownloadFileTypes == null
                || Config.DownloadFileTypes.size() == 0)
            return false;

        boolean downloaded = false;
        boolean unwrapped = false;

        if (applyProtocolAndBaseDefaults(root, absoluteLinkBase))
            unwrapped = true;

        FutureProcessor<AsyncUnwrapImgPrx> fpUnwrap = new FutureProcessor<>();
        FutureProcessor<AsyncDownloadExternalLinks> fpDownload = new FutureProcessor<>();

        try
        {
            unwrapped |= unwrapImgPrx(root, "img", "src", fpUnwrap);
            // unwrapped |= unwrapImgPrx(root, "img", "original-src", fpUnwrap);
            unwrapped |= unwrapImgPrx(root, "a", "href", fpUnwrap);
            // unwrapped |= unwrapImgPrx(root, "a", "original-href", fpUnwrap);

            fpUnwrap.start();

            try (AppendToThreadName a = new AppendToThreadName(" mapping imgprx links"))
            {
                for (AsyncUnwrapImgPrx async : fpUnwrap.expectedResults())
                    unwrapped |= async.apply();
            }

            /* ----------------------------------------------------------------- */

            downloaded |= downloadExternalLinks(root, "a", "href", fpDownload);
            downloaded |= downloadExternalLinks(root, "img", "src", fpDownload);

            fpDownload.start();

            try (AppendToThreadName a = new AppendToThreadName(" downloading links"))
            {
                for (AsyncDownloadExternalLinks async : fpDownload.expectedResults())
                    downloaded |= async.apply();
            }
        }
        catch (Exception ex)
        {
            fpUnwrap.shutdown();
            fpDownload.shutdown();
            throw ex;
        }

        return downloaded || unwrapped;
    }

    /* ==================================================================================================================== */

    private boolean unwrapImgPrx(Node root, String tag, String attr, FutureProcessor<AsyncUnwrapImgPrx> fpUnwrap) throws Exception
    {
        boolean unwrapped = false;

        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);

            if (href != null && Web.isLivejournalImgPrx(href))
            {
                if (ThreadsControl.useLinkDownloadThreads())
                {
                    fpUnwrap.add(new AsyncUnwrapImgPrx(n, attr, href, rurl));
                }
                else
                {
                    String newref = resolveImgPrxRedirect(href, rurl);
                    if (newref != null)
                    {
                        if (JSOUP.getAttribute(n, "original-" + attr) == null)
                            JSOUP.setAttribute(n, "original-" + attr, href);

                        JSOUP.updateAttribute(n, attr, newref);
                        unwrapped = true;
                    }
                }
            }
        }

        return unwrapped;
    }

    public static class AsyncUnwrapImgPrx implements Callable<AsyncUnwrapImgPrx>
    {
        // in
        private final Node n;
        private final String attr;
        private final String href;
        private final String rurl;

        // out
        private String newref;
        private Exception ex;

        public AsyncUnwrapImgPrx(Node n, String attr, String href, String rurl)
        {
            this.n = n;
            this.attr = attr;
            this.href = href;
            this.rurl = rurl;
        }

        @Override
        public AsyncUnwrapImgPrx call() throws Exception
        {
            String threadName = Thread.currentThread().getName();

            try
            {
                Thread.currentThread().setName("webload");
                newref = resolveImgPrxRedirect(href, rurl);
            }
            catch (Exception ex)
            {
                this.ex = ex;
            }
            finally
            {
                Thread.currentThread().setName(threadName);
            }

            return this;
        }

        public boolean apply() throws Exception
        {
            if (ex != null)
                throw ex;

            if (newref != null)
            {
                if (JSOUP.getAttribute(n, "original-" + attr) == null)
                    JSOUP.setAttribute(n, "original-" + attr, href);

                JSOUP.updateAttribute(n, attr, newref);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    private static final ConcurrentHashMap<String, Optional<String>> resolvedImgPrxLinks = new ConcurrentHashMap<>();

    private static String resolveImgPrxRedirect(String href, String rurl) throws Exception
    {
        String href_noprotocol = Util.stripProtocol(href);

        Optional<String> opt_newref = resolvedImgPrxLinks.get(href_noprotocol);

        if (opt_newref != null)
        {
            if (opt_newref.isPresent())
            {
                return opt_newref.get();
            }
            else
            {
                return null;
            }
        }

        // already in St format?
        String newref = LJUtil.decodeImgPrxStLink(href);
        String newref_noprotocol = Util.stripProtocol(newref);
        if (newref_noprotocol.equals(href_noprotocol))
            newref = null;

        if (newref == null || newref.equals(href))
        {
            String referer = (rurl == null) ? null : LJUtil.recordPageURL(rurl);
            newref = Web.getRedirectLocation(href, referer, LinkDownloader.getImageHeaders());

            if (newref != null)
            {
                newref = LJUtil.decodeImgPrxStLink(newref);
                newref_noprotocol = Util.stripProtocol(newref);
                if (newref_noprotocol.equals(href_noprotocol))
                    newref = null;
            }
        }

        if (newref != null)
        {
            resolvedImgPrxLinks.put(href_noprotocol, Optional.of(newref));
        }
        else
        {
            resolvedImgPrxLinks.put(href_noprotocol, Optional.empty());
        }

        return newref;
    }

    /* ==================================================================================================================== */

    /*static*/ private boolean downloadExternalLinks(Node root, String tag, String attr,
            FutureProcessor<AsyncDownloadExternalLinks> fpDownload) throws Exception
    {
        boolean downloaded = false;

        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);
            href = UrlUtil.decodeHtmlAttrLink(href);

            if (ShouldDownload.shouldDownload(tag.equalsIgnoreCase("img"), href, Main.linkDownloader.isOnlineOnly()))
            {
                String referer = (rurl == null) ? null : LJUtil.recordPageURL(rurl);

                if (ThreadsControl.useLinkDownloadThreads())
                {
                    fpDownload.add(new AsyncDownloadExternalLinks(n, tag, attr, href, referer, linkReferencePrefix));
                }
                else
                {
                    boolean image = tag.equalsIgnoreCase("img");
                    String newref = Main.linkDownloader.download(image, href, referer, linkReferencePrefix);
                    if (newref != null)
                    {
                        JSOUP.updateAttribute(n, attr, newref);
                        if (JSOUP.getAttribute(n, "original-" + attr) == null)
                            JSOUP.setAttribute(n, "original-" + attr, href);
                        downloaded = true;
                    }
                }
            }
        }

        return downloaded;
    }

    public static class AsyncDownloadExternalLinks implements Callable<AsyncDownloadExternalLinks>
    {
        // in
        private final Node n;
        private final String tag;
        private final String attr;
        private final String href;
        private final String referer;
        private final String linkReferencePrefix;

        // out
        private String newref;
        private Exception ex;

        public AsyncDownloadExternalLinks(Node n, String tag, String attr, String href, String referer, String linkReferencePrefix)
        {
            this.n = n;
            this.tag = tag;
            this.attr = attr;
            this.href = href;
            this.referer = referer;
            this.linkReferencePrefix = linkReferencePrefix;
        }

        @Override
        public AsyncDownloadExternalLinks call() throws Exception
        {
            String threadName = Thread.currentThread().getName();

            try
            {
                Thread.currentThread().setName("webload");
                boolean image = tag.equalsIgnoreCase("img");
                newref = Main.linkDownloader.download(image, href, referer, linkReferencePrefix);
            }
            catch (Exception ex)
            {
                this.ex = ex;
            }
            finally
            {
                Thread.currentThread().setName(threadName);
            }

            return this;
        }

        public boolean apply() throws Exception
        {
            if (ex != null)
                throw ex;

            if (newref != null)
            {
                JSOUP.updateAttribute(n, attr, newref);
                if (JSOUP.getAttribute(n, "original-" + attr) == null)
                    JSOUP.setAttribute(n, "original-" + attr, href);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    /* ==================================================================================================================== */

    public boolean remapLocalRelativeLinks(String oldPrefix, String newPrefix) throws Exception
    {
        boolean remapped = false;

        if (remapLocalRelativeLinks(oldPrefix, newPrefix, "a", "href"))
            remapped = true;

        if (remapLocalRelativeLinks(oldPrefix, newPrefix, "img", "src"))
            remapped = true;

        return remapped;
    }

    private boolean remapLocalRelativeLinks(String oldPrefix, String newPrefix, String tag, String attr) throws Exception
    {
        boolean remapped = false;
        final String fileProtocol = "file://";

        for (Node n : JSOUP.findElements(this.pageRoot, tag))
        {
            String ref = JSOUP.getAttribute(n, attr);
            if (ref != null)
            {
                if (ref.startsWith(fileProtocol))
                    ref = ref.substring(fileProtocol.length());

                if (ref.startsWith(oldPrefix))
                {
                    ref = newPrefix + ref.substring(oldPrefix.length());
                    JSOUP.updateAttribute(n, attr, ref);
                    remapped = true;
                }
            }
        }

        return remapped;
    }

    /* ==================================================================================================================== */

    private boolean applyProtocolAndBaseDefaults(Node root, AbsoluteLinkBase absoluteLinkBase) throws Exception
    {
        boolean applied = false;

        /* use of | rather than || prevents evaluation short-cut */
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "link", "href");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "a", "href");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "iframe", "src");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "img", "src");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "video", "src");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "audio", "src");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "source", "src");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "embed", "src");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "track", "src");
        applied |= applyProtocolAndBaseDefaults(root, absoluteLinkBase, "object", "data");

        return applied;
    }

    private boolean applyProtocolAndBaseDefaults(Node root, AbsoluteLinkBase absoluteLinkBase, String tag, String attr)
            throws Exception
    {
        boolean applied = false;

        for (Node n : JSOUP.findElements(root, tag))
        {
            String href = JSOUP.getAttribute(n, attr);

            if (href != null)
            {
                String newref = null;

                if (href.startsWith("//"))
                {
                    newref = "https:" + href;
                }
                else if (href.startsWith("/") && Config.isRossiaOrg())
                {
                    switch (absoluteLinkBase)
                    {
                    case WWW_Livejournal:
                    case User:
                        newref = String.format("https://lj.rossia.org%s", href);
                        break;
                    case None:
                        newref = null;
                        break;
                    }

                }
                else if (href.startsWith("/"))
                {
                    switch (absoluteLinkBase)
                    {
                    case User:
                        newref = String.format("https://%s.%s%s", Config.MangledUser, Config.Site, href);
                        break;
                    case WWW_Livejournal:
                        newref = String.format("https://www.%s%s", Config.Site, href);
                        break;
                    case None:
                        newref = null;
                        break;
                    }
                }

                if (newref != null)
                {
                    JSOUP.updateAttribute(n, attr, newref);
                    applied = true;
                }
            }
        }

        return applied;
    }

    /* ==================================================================================================================== */

    public String detectPageStyle() throws Exception
    {
        String style = null;

        if (Config.isRossiaOrg())
            return Sites.RossiaOrg;

        if (Config.isDreamwidthOrg())
            return Sites.DreamwidthOrg;

        for (Node n : JSOUP.findElements(pageRoot, "link"))
        {
            String rel = JSOUP.getAttribute(n, "rel");
            String href = JSOUP.getAttribute(n, "href");

            if (rel != null && href != null)
            {
                if (rel.toLowerCase().equals("next") || rel.toLowerCase().equals("prev") ||
                        rel.toLowerCase().equals("previous") || rel.toLowerCase().equals("help"))
                {
                    String host = Util.urlHost(href).toLowerCase();
                    if (host.endsWith("." + Sites.DreamwidthOrg) || host.equals(Sites.DreamwidthOrg))
                        style = detectPageStyle(style, Sites.DreamwidthOrg);
                }
            }
        }

        if (style != null)
            return style;

        List<Node> vnodes = JSOUP.findElements(pageRoot, "article");

        for (Node n : vnodes)
        {
            Set<String> classes = JSOUP.getClassesLowercase(n);

            if (classes.contains("aentry") && classes.contains("aentry--post2017"))
                style = detectPageStyle(style, "new-style");

            if (classes.contains("aentry") && classes.contains("ng-scope"))
                style = detectPageStyle(style, "new-style");

            if (classes.contains("b-singlepost") && classes.contains("hentry"))
                style = detectPageStyle(style, "classic");

            if (classes.contains("b-singlepost-body") && classes.contains("entry-content"))
                style = detectPageStyle(style, "classic");
        }

        for (Node n : JSOUP.findElements(pageRoot, "link"))
        {
            String rel = JSOUP.getAttribute(n, "rel");
            String href = JSOUP.getAttribute(n, "href");

            if (rel != null && href != null)
            {
                if (rel.toLowerCase().equals("next") || rel.toLowerCase().equals("prev") || rel.toLowerCase().equals("previous"))
                {
                    String host = Util.urlHost(href).toLowerCase();
                    if (host.equals(Sites.RossiaOrg))
                        style = detectPageStyle(style, Sites.RossiaOrg);
                }

                if (rel.toLowerCase().equals("next") || rel.toLowerCase().equals("prev") ||
                        rel.toLowerCase().equals("previous") || rel.toLowerCase().equals("help"))
                {
                    String host = Util.urlHost(href).toLowerCase();
                    if (host.endsWith("." + Sites.DreamwidthOrg) || host.equals(Sites.DreamwidthOrg))
                        style = detectPageStyle(style, Sites.DreamwidthOrg);
                }
            }
        }

        if (style == null)
            throw new Exception("Unable to detect page style (missing indicators)");

        return style;
    }

    private String detectPageStyle(String s1, String s2) throws Exception
    {
        if (s1 == null || s1.equals(s2))
            return s2;

        throw new Exception("Unable to detect page style (conflicting indicators)");
    }

    /* ============================================================== */

    public abstract void removeJunk(int flags) throws Exception;

    public abstract Element findCommentsSection(Node pageRootCurrent, boolean required) throws Exception;

    public abstract void injectComments(Element commentsSection, CommentsTree commentTree) throws Exception;

    public abstract Element findMainArticle() throws Exception;

    /* ============================================================== */

    protected void injectHtml(Element commentsSection, String html, String base_url)
    {
        List<Node> nodes = org.jsoup.parser.Parser.parseFragment(html, commentsSection, base_url);
        nodes = new ArrayList<>(nodes);

        for (Node node : nodes)
            commentsSection.appendChild(node);
    }

    protected String expandVars(String template, Map<String, String> vars)
    {
        String res = template;

        for (String key : vars.keySet())
        {
            String value = vars.get(key);
            if (value != null)
                res = res.replace("{$" + key + "}", value);
        }

        return res;
    }

    protected void removeNonArticleParents() throws Exception
    {
        // find article tags
        List<Node> articles = JSOUP.findElements(pageRoot, "article");

        // something wrong? leave it alone
        if (articles.size() == 0)
            return;

        // in older LJ page styles <div id=comments> can be not under article, but standalone 
        List<Node> alones = findStandaloneCommentsSections(pageRoot);
        List<Node> articles_and_alones = JSOUP.union(articles, alones);

        // traverse upwards from articles and mark the nodes to keep
        Set<Node> keepSet = new HashSet<Node>();
        for (Node n : articles_and_alones)
        {
            JSOUP.enumParents(keepSet, n);
            keepSet.add(n);
        }

        // traverse from root recursively downwards (like in flatten)
        // marking all <table>, <tr> and <td> not in created keep set
        // to be deleted
        List<Node> delvec = new ArrayList<>();
        removeNonArticleParents_enum_deletes(delvec, keepSet, new HashSet<Node>(articles_and_alones), pageRoot);

        // delete these elements
        if (delvec.size() != 0)
            JSOUP.removeElements(pageRoot, delvec);
    }

    private void removeNonArticleParents_enum_deletes(List<Node> delvec, Set<Node> keepSet, Set<Node> stopSet, Node n)
            throws Exception
    {
        if (n == null)
            return;

        if (Util.containsIdentity(stopSet, n))
        {
            // JSOUP.dumpNodeOffset(n, "STOP *** ");
            return;
        }

        if (n instanceof Element)
        {
            Element el = (Element) n;
            String name = JSOUP.nodeName(el);
            if (name.equalsIgnoreCase("table") || name.equalsIgnoreCase("tr") || name.equalsIgnoreCase("td"))
            {
                if (!Util.containsIdentity(keepSet, n))
                {
                    delvec.add(n);
                    // JSOUP.dumpNodeOffset(n, "DELETE *** ");
                    removeNonArticleParents_enum_deletes(delvec, keepSet, stopSet, JSOUP.nextSibling(n));
                    return;
                }
            }
        }

        // JSOUP.dumpNodeOffset(n);

        removeNonArticleParents_enum_deletes(delvec, keepSet, stopSet, JSOUP.firstChild(n));
        removeNonArticleParents_enum_deletes(delvec, keepSet, stopSet, JSOUP.nextSibling(n));
    }

    protected boolean isPositiveNumber(String s)
    {
        try
        {
            int i = Integer.parseInt(s);
            return i >= 1;
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    protected Boolean hasComments(Boolean has1, Boolean has2)
    {
        if (has1 == null || has1.equals(has2))
            return has2;
        throw new RuntimeException("Page has conflicting comment count sections");
    }

    // in older LJ page styles <div id=comments> can be not under article, but standalone 
    // find <div id=comments> sections that are not under <article>  
    protected List<Node> findStandaloneCommentsSections(Node root) throws Exception
    {
        List<Node> vn = new ArrayList<>();

        for (Node n : JSOUP.findElements(root, "div", "id", "comments"))
        {
            if (!JSOUP.hasParent(n, "article"))
                vn.add(n);
        }

        return vn;
    }

    /* ============================================================== */

    public String extractCleanedHead() throws Exception
    {
        // perform deep clone
        Node head = findHead().clone();

        // remove individual entries
        JSOUP.removeElements(head, JSOUP.findElements(head, "title"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "meta"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "next"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "prev"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "Previous"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "canonical"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "noscript"));
        JSOUP.removeElements(head, JSOUP.findComments(head));
        JSOUP.removeWhitespaceNodes(head);

        String outerHtml = head.outerHtml();
        return outerHtml;
    }

    public void cleanHead(String titleText) throws Exception
    {
        Element head = findHead();

        // remove individual entries
        JSOUP.removeElements(head, JSOUP.findElements(head, "title"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "meta"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "next"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "prev"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "Previous"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "link", "rel", "canonical"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "noscript"));
        // JSOUP.removeElements(head, JSOUP.findComments(head));

        // Create and append <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
        Element meta = new Element(Tag.valueOf("meta"), "");
        meta.attr("http-equiv", "Content-Type");
        meta.attr("content", "text/html; charset=utf-8");
        head.appendChild(meta);

        // Create and append <title>....</title>
        Element title = new Element(Tag.valueOf("title"), "");
        title.text(titleText);
        head.appendChild(title);
    }

    // remove comments section and other parts except article body
    public void removeNonArticleBodyContent() throws Exception
    {
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "comments"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(pageRoot, "div", "acomments"));

        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "div", "id", "hello-world"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(pageRoot, "div", "b-fader"));
        JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(pageRoot, "div", "b-singlepost-reactions"));

        List<Node> vel = new ArrayList<>();

        for (Node n : JSOUP.findElements(pageRoot, "div"))
        {
            if (null != JSOUP.getAttribute(n, "suggestion-for-unlogged") ||
                    null != JSOUP.getAttribute(n, "rd-post-view-related-list"))
            {
                vel.add(n);
            }
        }

        JSOUP.removeElements(pageRoot, vel);

        /*
         * Older pages have comments under 
         * <form id="multiform" name="multiform" method="post" action="https://www.livejournal.com/talkmulti.bml" class="ng-pristine ng-valid">
         */
        vel.clear();
        for (Node n : JSOUP.findElements(pageRoot, "form"))
        {
            String action = JSOUP.getAttribute(n, "action");
            if (action != null && action.contains("livejournal.com/talkmulti.bml"))
                vel.add(n);
        }
        JSOUP.removeElements(pageRoot, vel);
    }

    public Element getBodyTag() throws Exception
    {
        Element body = getCachedBody();
        if (body != null)
            return body;

        List<Node> bodies = JSOUP.findElements(getCachedPageFlat(), "body");
        if (bodies.size() != 1)
            throw new Exception("Unable to locate the BODY tag");
        return (Element) bodies.get(0);
    }

    // from article tag scan upwards
    // in all "td" remove numeric "height"
    // such as td height=585
    public void unsizeArticleHeight() throws Exception
    {
        List<Node> articles = JSOUP.findElements(pageRoot, "article");

        for (Node article : articles)
        {
            for (Node ap : JSOUP.enumParents(article))
            {
                if (ap instanceof Element)
                {
                    Element el = (Element) ap;

                    if (el.tagName().equalsIgnoreCase("td"))
                    {
                        String height = JSOUP.getAttribute(el, "height");
                        if (height != null && isNumber(height))
                            JSOUP.deleteAttribute(el, "height");
                    }
                }
            }
        }
    }

    private boolean isNumber(String s)
    {
        try
        {
            int v = Integer.parseInt(s);
            Util.unused(v);
            return true;
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    public void deleteDivThreeposts() throws Exception
    {
        List<Node> delvec = new ArrayList<>();

        // div class=" threeposts threeposts--count-9 "
        List<Node> divs = JSOUP.findElementsWithAllClasses(pageRoot, "div", Util.setOf("threeposts"));
        for (Node div : divs)
        {
            boolean hasElementChild = false;
            for (Node n : JSOUP.flatten(div))
            {
                if (n instanceof Element && n != div)
                    hasElementChild = true;
            }

            if (!hasElementChild)
                delvec.add(div);
        }

        JSOUP.removeNodes(delvec);
    }

    /* ============================================================== */

    public String extractCommentsJson() throws Exception
    {
        Element head = findHead();

        final String DELIMITER_1 = " Site.page = {";
        final String DELIMITER_2 = " Site.page.template = {";

        String commentsScript = null;

        for (Node scriptNode : JSOUP.findElements(head, "script", "type", "text/javascript"))
        {
            String script = JSOUP.asElement(scriptNode).data();

            if (Util.countOccurrences(script, DELIMITER_1) == 1 && Util.countOccurrences(script, DELIMITER_2) == 1)
            {
                if (commentsScript != null)
                    throw new Exception("Unexpected multiple comment scripts");
                commentsScript = script;
            }
        }

        if (commentsScript == null)
            throw new Exception("Unexpected missing comment script");

        String s = Util.extractBetween(commentsScript, DELIMITER_1, DELIMITER_2);
        if (s == null)
            throw new Exception("Unexpected missing comment script");
        s = "{" + s;

        /* remove trailing spaces, tabs and newlines */
        s = s.replaceAll("[ \\t\\n\\r]+$", "");

        if (!s.endsWith("};"))
            throw new Exception("Unexpected missing comment script");

        s = Util.stripTail(s, ";");

        return s;
    }

    public Element findHead() throws Exception
    {
        Element head = getCachedHead();
        if (head != null)
            return head;

        List<Node> heads = JSOUP.findElements(getCachedPageFlat(), "head");
        if (heads.size() != 1)
            throw new Exception("Unable to locate the HEAD tag");
        head = (Element) heads.get(0);
        return head;
    }

    public Element findBody() throws Exception
    {
        return getBodyTag();
    }

    /* ============================================================== */

    public void removeProfilePageJunk(String titleText, Node... nn) throws Exception
    {
        cleanHead(titleText);

        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "script"));
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "noscript"));
        JSOUP.removeElementsWithClass(pageRoot, "ul", "b-profile-actions");
        JSOUP.removeElementsWithClass(pageRoot, "div", "s-switchv3");
        JSOUP.removeElements(pageRoot, JSOUP.findElements(pageRoot, "header", "role", "banner"));

        /*
         * Delete all table rows/cells and tables except directly containing @n
         * and also contained within the the same "td" cell as @n 
         */
        List<Node> preserve = new ArrayList<>();

        for (Node n : nn)
        {
            if (n != null)
            {
                for (Node p = n;; p = p.parentNode())
                {
                    if (p == null)
                        throw new Exception("missing BODY element");
                    preserve.add(p);
                    if (p instanceof Element && JSOUP.asElement(p).tagName().equalsIgnoreCase("body"))
                        break;
                }

                Element td = JSOUP.locateUpwardElement(n, "td");
                preserve.addAll(JSOUP.findElements(td, "td"));
                preserve.addAll(JSOUP.findElements(td, "tr"));
                preserve.addAll(JSOUP.findElements(td, "table"));
            }
        }

        deleteExcept("td", preserve);
        deleteExcept("tr", preserve);
        deleteExcept("table", preserve);
    }

    private void deleteExcept(String tag, List<Node> preserve) throws Exception
    {
        List<Node> vn = JSOUP.findElements(pageRoot, tag);
        for (Node n : vn)
        {
            if (!Util.containsIdentity(preserve, n))
            {
                n.remove();
            }
        }
    }

    public void rectifyProfileUserpic() throws Exception
    {
        for (Node n : JSOUP.findElementsWithClass(pageRoot, "a", "b-myuserpic-current"))
        {
            String style = JSOUP.getAttribute(n, "style");
            if (style != null)
            {
                style = Util.despace(style);
                String url = extractBackgroundImageUrl(style);
                if (url != null)
                {
                    url = Util.despace(url);
                    Element img = new Element(Tag.valueOf("img"), "");
                    img.attr("src", url);
                    JSOUP.asElement(n).prependChild(img);
                }
            }
        }

        Node frame = JSOUP.optionalOne(JSOUP.findElementsWithClass(pageRoot, "div", "b-ljuserpic"));
        if (frame != null)
        {
            JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(frame, "span", "b-ljuserpic-default"));
            JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(frame, "div", "b-myuserpic-options"));
            JSOUP.removeElements(pageRoot, JSOUP.findElementsWithClass(frame, "select", "b-ljuserpic-selector"));
        }
    }

    /**
     * Extracts the background-image URL from the style attribute value.
     *
     * @param style
     *            The value of the style attribute, e.g., "background-image: url( https://example.com/image.jpg );"
     * @return The extracted URL, or null if not found.
     */
    private String extractBackgroundImageUrl(String style)
    {
        if (style != null)
        {
            // Regex to find background-image: url(...) with optional whitespace
            Pattern pattern = Pattern.compile("background-image\\s*:\\s*url\\s*\\(\\s*(['\"]?)([^'\")\\s]+)\\1\\s*\\)",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(style);

            if (matcher.find())
                return matcher.group(2); // The actual URL
        }

        return null;
    }

    /* ============================================================== */

    public String extractCleanedHeadLJSearch() throws Exception
    {
        // perform deep clone
        Node head = findHead().clone();

        // remove individual entries
        JSOUP.removeElements(head, JSOUP.findElements(head, "title"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "meta"));
        JSOUP.removeElements(head, JSOUP.findComments(head));
        JSOUP.removeWhitespaceNodes(head);

        String outerHtml = head.outerHtml();
        return outerHtml;
    }

    public void cleanHeadLJSearch(String titleText) throws Exception
    {
        Element head = findHead();

        // remove individual entries
        JSOUP.removeElements(head, JSOUP.findElements(head, "title"));
        JSOUP.removeElements(head, JSOUP.findElements(head, "meta"));

        // Create and append <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
        Element meta = new Element(Tag.valueOf("meta"), "");
        meta.attr("http-equiv", "Content-Type");
        meta.attr("content", "text/html; charset=utf-8");
        head.appendChild(meta);

        // Create and append <title>....</title>
        Element title = new Element(Tag.valueOf("title"), "");
        title.text(titleText);
        head.appendChild(title);
    }

    public Element findContentWrapper() throws Exception
    {
        return findBody();
    }

    /* ============================================================== */

    public String extractDateTimeString() throws Exception
    {
        throw new Exception("Not implemented");
    }

    /* ============================================================== */

    public boolean unwrapAwayLinks() throws Exception
    {
        boolean updated = false;

        for (Node n : JSOUP.findElements(pageRoot, "a"))
            updated |= AwayLink.unwrap(n, "href");

        for (Node n : JSOUP.findElements(pageRoot, "img"))
            updated |= AwayLink.unwrap(n, "src");

        return updated;
    }
}
