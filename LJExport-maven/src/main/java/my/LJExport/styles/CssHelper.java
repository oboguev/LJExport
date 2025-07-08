package my.LJExport.styles;

import java.util.ArrayList;
import java.util.List;

import my.LJExport.runtime.Util;

public class CssHelper
{
    /*
     * Extract fully-qualified CSS links one at a time from href value in
     * link rel="stylesheet" type="text/css" href="...">
     * such as 
     * "https://l-stat.livejournal.net/??lj_base-journal.css,common-post.css,adv/native.css,widgets/threeposts.css,recaptcha.css,categories/category-panel.css,core/components/tag.css,core/components/basepopup.css,core/components/lightcontrols.css,core/components/note-inner.css,core/components/alert.css,popup/popup-suggestion.css,popup/popup-trump.css,popup/popup-map-invite.css,popup/push-woosh-popup.css,widgets/reactions.css,notifications/list.css,widgets/polls.css,schemius_v4/asap-news.css,components/interest.css,components/cookies-banner.css,components/modal-repost.css,components/buttons.css,components/promo-video.css,components/banners/sherry.css?v=1750939073"
     * "https://l-stat.livejournal.net/??lj_base.css,flatbutton.css,updateform.css,commentmanage.css,spelling.css,widgets/filter-settings.css,components/modal.css,components/form.css,widgets/rutos.css,journalpromo/journalpromo.css,widgets/calendar.css,controlstrip-local.css,medius/scheme/components.css,lj_base-app.css,dystopia.css,msgsystem.css?v=1750939073"
     */
    public static List<String> cssLinks(String href)
    {
        List<String> result = new ArrayList<>();
        if (href == null || href.isEmpty())
            return result;

        int firstCombo = href.indexOf("??");
        if (firstCombo == -1)
        {
            // No combo syntax — treat as a single CSS file
            if (href.toLowerCase().contains(".css"))
            {
                result.add(href);
            }
            return result;
        }

        int secondCombo = href.indexOf("??", firstCombo + 2);
        if (secondCombo != -1)
        {
            throw new IllegalArgumentException("Multiple '??' combo indicators found in href: " + href);
        }

        String base = href.substring(0, firstCombo);
        String remainder = href.substring(firstCombo + 2); // skip "??"

        // Extract optional query string
        String query = "";
        int queryIdx = remainder.indexOf('?');
        if (queryIdx != -1)
        {
            query = remainder.substring(queryIdx); // includes '?'
            remainder = remainder.substring(0, queryIdx);
        }

        // Split the combo list
        String[] parts = remainder.split(",");
        for (String part : parts)
        {
            part = part.trim();
            if (!part.isEmpty())
            {
                result.add(base + part + query);
            }
        }

        return result;
    }

    public static void main(String[] args)
    {
        List<String> links;
        links = cssLinks("../../styles/aaa/iconochive.css%3Fv=1B2M2Y8A");
        links = cssLinks("https://web-static.archive.org/_static/css/iconochive.css?v=1B2M2Y8A");
        links = cssLinks("https://l-stat.livejournal.net/??lj_base.css,flatbutton.css,updateform.css?v=1750939073");
        links = cssLinks(
                "https://l-stat.livejournal.net/??lj_base.css,flatbutton.css,updateform.css,commentmanage.css,spelling.css,widgets/filter-settings.css,components/modal.css,components/form.css,widgets/rutos.css,journalpromo/journalpromo.css,widgets/calendar.css,controlstrip-local.css,medius/scheme/components.css,lj_base-app.css,dystopia.css,msgsystem.css?v=1750939073");
        links = cssLinks(
                "https://l-stat.livejournal.net/??lj_base-journal.css,common-post.css,adv/native.css,widgets/threeposts.css,recaptcha.css,categories/category-panel.css,core/components/tag.css,core/components/basepopup.css,core/components/lightcontrols.css,core/components/note-inner.css,core/components/alert.css,popup/popup-suggestion.css,popup/popup-trump.css,popup/popup-map-invite.css,popup/push-woosh-popup.css,widgets/reactions.css,notifications/list.css,widgets/polls.css,schemius_v4/asap-news.css,components/interest.css,components/cookies-banner.css,components/modal-repost.css,components/buttons.css,components/promo-video.css,components/banners/sherry.css?v=1750939073");
        Util.unused(links);
    }
}
