package com.intechcore.polarion.extension.github.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GithubHtmlTest {

    private static final String UUID = "d440e652-4cf3-4ca6-b3ea-131fb496cecc";
    private static final String SIGNED = "https://private-user-images.githubusercontent.com/293140534/666611264-" + UUID
            + ".png?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.part";

    @Test
    void keepsTheMarkupOfMarkdownAndDropsTheAttributesOfGithub() {
        String html = "<h3 dir=\"auto\">Description</h3><p dir=\"auto\">Use <strong>bold</strong>, <em>italic</em> and"
                + " <code>code</code>.<br>Next line</p><ul dir=\"auto\"><li>one</li></ul>"
                + "<div class=\"highlight highlight-source-java\"><pre><span class=\"pl-k\">int</span> a;</pre></div>"
                + "<table><thead><tr><th>A</th></tr></thead><tbody><tr><td>1</td></tr></tbody></table>"
                + "<p><g-emoji class=\"g-emoji\" alias=\"robot\">🤖</g-emoji> <del>old</del></p>";

        assertThat(GithubHtml.clean(html)).isEqualTo("<h3>Description</h3><p>Use <strong>bold</strong>, <em>italic</em> and"
                + " <code>code</code>.<br>Next line</p><ul><li>one</li></ul>"
                + "<div><pre><span>int</span> a;</pre></div>"
                + "<table><thead><tr><th>A</th></tr></thead><tbody><tr><td>1</td></tr></tbody></table>"
                + "<p>🤖 <del>old</del></p>");
    }

    @Test
    void dropsScriptsStylesAndUnsafeLinks() {
        String html = "<p onclick=\"steal()\" style=\"color:red\">Text</p><script>steal()</script><style>p{}</style>"
                + "<a href=\"javascript:steal()\">click</a><a href=\"https://github.com/alice\" class=\"user-mention\">@alice</a>"
                + "<iframe src=\"https://evil.example\"></iframe><form><input type=\"text\"></form>";

        assertThat(GithubHtml.clean(html)).isEqualTo(
                "<p>Text</p><a>click</a><a href=\"https://github.com/alice\">@alice</a>");
    }

    @Test
    void givesAnUploadedImageItsStableUrl() {
        String html = "<p><a target=\"_blank\" rel=\"noopener noreferrer\" href=\"" + SIGNED + "\"><img width=\"907\" height=\"472\""
                + " alt=\"Image\" src=\"" + SIGNED + "\" style=\"max-width: 100%;\" class=\"js-gh-image-fallback\"></a></p>"
                + "<img src=\"https://camo.githubusercontent.com/abc\" alt=\"badge\">";

        String stable = "https://github.com/user-attachments/assets/" + UUID;
        assertThat(GithubHtml.clean(html)).isEqualTo("<p><a href=\"" + stable + "\"><img width=\"907\" height=\"472\" alt=\"Image\""
                + " src=\"" + stable + "\"></a></p><img src=\"https://camo.githubusercontent.com/abc\" alt=\"badge\">");
    }

    @Test
    void showsTaskListsAndCollapsedSections() {
        String html = "<ul class=\"contains-task-list\"><li class=\"task-list-item\"><input type=\"checkbox\" disabled checked> done</li>"
                + "<li><input type=\"checkbox\" disabled> open</li></ul>"
                + "<details><summary>Logs</summary><pre>trace</pre></details>";

        assertThat(GithubHtml.clean(html)).isEqualTo("<ul><li>☑  done</li><li>☐  open</li></ul>"
                + "<p><strong>Logs</strong></p><pre>trace</pre>");
    }

    @Test
    void cleansAnEmptyBody() {
        assertThat(GithubHtml.clean("")).isEmpty();
    }
}
