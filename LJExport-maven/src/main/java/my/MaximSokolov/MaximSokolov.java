package my.MaximSokolov;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import my.LJExport.Config;
import my.LJExport.readers.direct.PageParserDirectBase;
import my.LJExport.readers.direct.PageParserDirectBasePassive;
import my.LJExport.readers.direct.PageParserDirectClassic;
import my.LJExport.readers.direct.PageParserDirectNewStyle;
import my.LJExport.runtime.Util;
import my.LJExport.runtime.YYYY_MM_DD;
import my.LJExport.runtime.html.JSOUP;
import my.LJExport.runtime.ui.UIProgressDialog;

public class MaximSokolov
{
    public static void main(String[] args)
    {
        MaximSokolov self = new MaximSokolov();
        self.do_main();
    }

    private Set<String> allLinks = new HashSet<>();

    private void do_main()
    {
        try
        {
            String[] users = { "maxim_sokolov", "maxim_sokolov2", "m_yu_sokolov" };

            if (Util.False)
            {
                for (String user : users)
                    do_user(user);
            }
            else
            {
                do_user("maxim_sokolov"); // classic  (2004.12-2007.09)
                do_user("m_yu_sokolov"); // classic  (2007.09-2020.07)
                do_user("maxim_sokolov2"); // newstyle (2020.08)
            }

            List<String> xlist = new ArrayList<>(allLinks);
            
            final Pattern PROTOCOL_PATTERN = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
            
            xlist = xlist.stream()
                    .map(url -> PROTOCOL_PATTERN.matcher(url)
                                                .replaceFirst(""))
                    .sorted()
                    .collect(Collectors.toList());
            
            for (String url : xlist)
            {
                Util.out(url);
            }
        }
        catch (Exception ex)
        {
            Util.err("*** Exception: " + ex.getLocalizedMessage());
            ex.printStackTrace();
        }
    }

    private void do_user(String user) throws Exception
    {
        Config.User = user;
        String pagesDir = Config.DownloadRoot + File.separator + Config.User + File.separator + "pages";
        List<String> pageFiles = Util.enumerateOnlyHtmlFiles(pagesDir);

        UIProgressDialog cp = new UIProgressDialog("Processing " + user);
        cp.begin();
        int count = 0;

        for (String fn : pageFiles)
        {
            String pageFileFullPath = pagesDir + File.separator + fn;

            // Util.out("Processing " + pageFileFullPath);
            cp.update("", 100.0 * count++ / (double) pageFiles.size());

            try
            {
                PageParserDirectBase parser = new PageParserDirectBasePassive();
                parser.rurl = Util.extractFileName(pageFileFullPath);

                parser.pageSource = Util.readFileAsString(pageFileFullPath);
                parser.parseHtml(parser.pageSource);

                switch (parser.detectPageStyle(true))
                {
                case "classic":
                    parser = new PageParserDirectClassic(parser);
                    parser.deleteDivThreeposts();
                    break;

                case "new-style":
                    parser = new PageParserDirectNewStyle(parser);
                    parser.deleteDivThreeposts();
                    break;

                }
                
                parser.removeNonArticleBodyContent();
                parser.unsizeArticleHeight();

                // extract date
                String dt = parser.extractDateTimeString();
                YYYY_MM_DD ymd = YYYY_MM_DD.from(dt);
                Util.noop();

                // extract body
                Element body = parser.findContentWrapper();

                // extract links in body
                List<String> links = extractExternalLinks(body);

                allLinks.addAll(links);
            }
            catch (Exception ex)
            {
                if (cp != null)
                {
                    cp.end();
                    cp = null;
                }

                throw new Exception("While processing " + pageFileFullPath, ex);
            }
        }

        if (cp != null)
            cp.end();

        // ### see monthly tape inline text
        // um.plus
        // russian.rt.com/opinion/*
        // ria.ru/analytics/*
        // ria.ru/analytics/*
        // www.kp.ru/daily/*
    }

    private List<String> extractExternalLinks(Element body) throws Exception
    {
        List<String> list = new ArrayList<>();

        for (Node n : JSOUP.findElements(body, "a"))
        {
            String href = JSOUP.getAttribute(n, "href");
            if (href != null)
            {
                href = href.trim();
                String lc = href.toLowerCase();

                if (!lc.startsWith("http://") && !lc.startsWith("https://"))
                    continue;

                if (lc.equals("http://") || lc.equals("https://"))
                    continue;

                URI uri = null;
                try
                {
                    uri = new URI(href);
                }
                catch (Exception ex)
                {
                    throw new Exception("Invalid URI " + href, ex);
                }

                String host = uri.getHost();
                if (host == null)
                    continue;
                host = host.toLowerCase();
                if (host.equals("livejounal.com") || host.endsWith(".livejournal.com"))
                    continue;
                if (host.equals("livejounal.net") || host.endsWith(".livejournal.net"))
                    continue;
                
                if (host.startsWith("www."))
                    host = Util.stripStart(host, "www.");
                        
                switch (host)
                {
                case "az.lib.ru":
                case "bbc.co.uk":
                case "bbc.com":
                case "carnegie.ru":
                case "change.org":
                case "cia.gov":
                case "compromat.ru":
                case "delyagin.ru":
                case "en.wikipedia.org":
                case "dw.com":
                case "facebook.com":
                case "gordonua.com":
                case "kprf.ru":
                case "kreml.org":
                case "kremlin.ru":
                case "latimes.com":
                case "lefigaro.fr":
                case "lemonde.fr":
                case "lenta.ru":
                case "levada.ru":
                case "livejournal.ru":
                case "lib.ru":
                case "lj.rossia.org":
                case "logirus.ru":
                case "militera.lib.ru":
                case "mk-london.co.uk":
                case "mobile.twitter.com":
                case "navalny.com":
                case "news.kremlin.ru":
                case "news.mail.ru":
                case "news.rambler.ru":
                case "palm.newsru.com":
                case "palm.rus.newsru.ua":
                case "pda.lenta.ru":
                case "sharij.net":
                case "stihi-rus.ru":
                case "youtube.com":
                case "washingtonexaminer.com":
                case "twitter.com":
                case "ru.wikipedia.org":
                case "ru.wikisource.org":
                    continue;
                    
                default:
                    break;
                }

                list.add(href);
            }
        }

        return list;
    }
}
