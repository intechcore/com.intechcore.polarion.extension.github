package com.intechcore.polarion.extension.github.service;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the HTML GitHub renders for the Markdown of an item into rich text for a work item. GitHub's
 * HTML is external input, so only a safe list of elements and attributes passes.
 */
@UtilityClass
public class GithubHtml {

    // An uploaded image: GitHub links it with a signature that expires after minutes. The UUID in
    // the file name leads to the same image under a URL that stays.
    private static final Pattern SIGNED_ATTACHMENT = Pattern.compile(
            "^https://private-user-images\\.githubusercontent\\.com/\\d+/\\d+-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.\\w+(\\?.*)?$");
    private static final String ATTACHMENT_URL = "https://github.com/user-attachments/assets/";

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("hr", "del", "s", "kbd");

    /** The rich text of an item. Text from GitHub never becomes a script, a style or a form. */
    public static @NotNull String clean(@NotNull String html) {
        Document document = Jsoup.parseBodyFragment(html);
        for (Element element : document.select("[src], [href]")) {
            stableUrl(element, "src");
            stableUrl(element, "href");
        }
        // A task list item: the checkbox becomes a character, since the safe list drops forms.
        for (Element checkbox : document.select("input[type=checkbox]")) {
            checkbox.replaceWith(new TextNode(checkbox.hasAttr("checked") ? "☑ " : "☐ "));
        }
        // A collapsed section shows open: a work item has no element that folds.
        for (Element summary : document.select("details > summary")) {
            summary.tagName("p").html("<strong>" + summary.html() + "</strong>");
        }
        document.select("details").unwrap();

        Document cleaned = new Cleaner(SAFELIST).clean(document);
        cleaned.outputSettings().prettyPrint(false);
        return cleaned.body().html();
    }

    private static void stableUrl(Element element, String attribute) {
        Matcher matcher = SIGNED_ATTACHMENT.matcher(element.attr(attribute));
        if (matcher.matches()) {
            element.attr(attribute, ATTACHMENT_URL + matcher.group(1));
        }
    }
}
