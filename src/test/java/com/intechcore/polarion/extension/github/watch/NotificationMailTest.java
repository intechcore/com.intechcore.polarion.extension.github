package com.intechcore.polarion.extension.github.watch;

import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ItemKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMailTest {

    private static final ImportEntry ISSUE = ImportEntry.builder().kind(ItemKind.ISSUE).number(7).title("Crash <now>")
            .url("https://github.com/acme/tool/issues/7").build();
    private static final ImportEntry PULL_REQUEST = ImportEntry.builder().kind(ItemKind.PULL_REQUEST).number(421)
            .title("Update docx4j").url("javascript:alert(1)").failedChecks("build").build();

    @Test
    void listsTheNewItemsWithTheirLinksAndThePageOfTheProject() {
        NotificationMail mail = new NotificationMail("e library", "Tool", "http://polarion.example/");

        assertThat(mail.subject(List.of(ISSUE))).isEqualTo("[GitHub] Tool: 1 new item");
        assertThat(mail.subject(List.of(ISSUE, PULL_REQUEST))).isEqualTo("[GitHub] Tool: 2 new items");
        assertThat(mail.html(List.of(ISSUE, PULL_REQUEST)))
                .contains("<li>Issue <a href=\"https://github.com/acme/tool/issues/7\">#7</a> Crash &lt;now&gt;</li>")
                // Only a GitHub address becomes a link.
                .contains("<li>Pull request with failed checks #421 Update docx4j (failed: build)</li>")
                .doesNotContain("javascript:")
                .contains("<a href=\"http://polarion.example/polarion/#/project/e+library/github\">");
    }

    @Test
    void leavesOutThePageWithoutTheAddressOfPolarion() {
        assertThat(new NotificationMail("elibrary", "Tool", " ").html(List.of(ISSUE))).doesNotContain("Open the GitHub page");
        assertThat(new NotificationMail("elibrary", "Tool", null).html(List.of(ISSUE))).doesNotContain("Open the GitHub page");
        assertThat(NotificationMail.escape(null)).isEmpty();
    }

    /** Under embargo an advisory is secret: the mail names it and its severity, never its summary. */
    @Test
    void namesAnAdvisoryWithoutItsSummary() {
        ImportEntry advisory = ImportEntry.builder().kind(ItemKind.ADVISORY).ghsaId("GHSA-aaaa").githubType("medium")
                .title("Fetches any URL").url("https://github.com/acme/tool/security/advisories/GHSA-aaaa").build();

        assertThat(new NotificationMail("elibrary", "Tool", null).html(List.of(advisory)))
                .contains("<li>Security advisory <a href=\"https://github.com/acme/tool/security/advisories/GHSA-aaaa\">GHSA-aaaa</a> (medium)</li>")
                .doesNotContain("Fetches any URL");
    }
}
