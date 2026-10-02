package com.intechcore.polarion.extension.github.settings;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositorySettingsModelTest {

    private static RepositorySettingsModel valid() {
        return RepositorySettingsModel.builder()
                .repository("acme/tool")
                .shortName("Tool")
                .enabled(true)
                .issues(ItemSettings.builder()
                        .enabled(true)
                        .workItemType("task")
                        .duplicateKey(DuplicateKey.CUSTOM_FIELD)
                        .duplicateKeyField("githubUrl")
                        .epicId("EL-1")
                        .epicLinkRole("parent")
                        .fields(Map.of("severity", "major"))
                        .build())
                .discussions(new ItemSettings())
                .build();
    }

    private static void assertRejected(Consumer<RepositorySettingsModel> change, String message) {
        RepositorySettingsModel model = valid();
        change.accept(model);
        assertThatThrownBy(model::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    @Test
    void survivesSerialization() {
        RepositorySettingsModel model = valid();
        model.setName("tool");
        model.setBundleTimestamp("2026-10-02");

        RepositorySettingsModel read = new RepositorySettingsModel();
        read.deserialize(model.serialize());

        assertThat(read).isEqualTo(model);
        assertThat(read.getName()).isEqualTo("tool");
        assertThat(read.getIssues().getFields()).containsEntry("severity", "major");
    }

    @Test
    void readsMissingEntriesAsDisabledDefaults() {
        RepositorySettingsModel read = new RepositorySettingsModel();
        read.deserialize("");

        assertThat(read.isEnabled()).isFalse();
        assertThat(read.getRepository()).isNull();
        assertThat(read.getIssues().isEnabled()).isFalse();
        assertThat(read.getIssues().getTitleTemplate()).isEqualTo("[GitHub] {shortName} : {title}");
        assertThat(read.getIssues().getDuplicateKey()).isEqualTo(DuplicateKey.HYPERLINK);
        assertThat(read.getDiscussions().getDescriptionTemplate()).isEqualTo("<a href=\"{url}\">{url}</a>");
    }

    @Test
    void acceptsValidSettings() {
        assertThatCode(() -> valid().validate()).doesNotThrowAnyException();
    }

    @Test
    void acceptsDisabledItemSettingsWithoutDetails() {
        RepositorySettingsModel model = valid();
        model.setIssues(null);
        model.setDiscussions(new ItemSettings());

        assertThatCode(model::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsARepositoryThatIsNotOwnerAndName() {
        for (String repository : new String[]{null, "", "tool", "acme/tool/extra", "../tool", "acme/..", "acme/to ol"}) {
            assertRejected(model -> model.setRepository(repository), "owner/name");
        }
    }

    @Test
    void rejectsAMissingShortName() {
        assertRejected(model -> model.setShortName(" "), "short name");
        assertRejected(model -> model.setShortName(null), "short name");
    }

    @Test
    void rejectsEnabledItemSettingsWithoutAWorkItemType() {
        assertRejected(model -> model.getIssues().setWorkItemType(""), "work item type for issues");
        assertRejected(model -> model.setDiscussions(ItemSettings.builder().enabled(true).build()), "work item type for discussions");
    }

    @Test
    void rejectsAMissingTitleTemplate() {
        assertRejected(model -> model.getIssues().setTitleTemplate(null), "title template");
    }

    @Test
    void rejectsACustomFieldKeyWithoutTheField() {
        assertRejected(model -> model.getIssues().setDuplicateKeyField(null), "custom field");
    }

    @Test
    void rejectsAnEpicWithoutALinkRole() {
        assertRejected(model -> model.getIssues().setEpicLinkRole(""), "link role");
    }

    private static ItemRule rule(RuleMatch match, String value, boolean skip, String workItemType) {
        return ItemRule.builder().match(match).value(value).skip(skip).workItemType(workItemType).build();
    }

    @Test
    void survivesSerializationWithRules() {
        RepositorySettingsModel model = valid();
        model.getIssues().setRules(List.of(
                rule(RuleMatch.LABEL, "wontfix", true, null),
                ItemRule.builder().match(RuleMatch.TYPE).value("Bug").workItemType("defect").fields(Map.of("severity", "major")).build()));

        RepositorySettingsModel read = new RepositorySettingsModel();
        read.deserialize(model.serialize());

        assertThat(read).isEqualTo(model);
        assertThat(read.getIssues().getRules()).extracting(ItemRule::describe).containsExactly("Label = wontfix", "Type = Bug");
    }

    @Test
    void readsSettingsWrittenBeforeTheRulesExisted() {
        RepositorySettingsModel read = new RepositorySettingsModel();
        read.deserialize("-----BEGIN ISSUES-----\n{\"enabled\":true,\"workItemType\":\"task\"}\n-----END ISSUES-----\n");

        assertThat(read.getIssues().getRules()).isEmpty();
    }

    @Test
    void acceptsRulesThatFitTheirItems() {
        RepositorySettingsModel model = valid();
        model.getIssues().setRules(List.of(rule(RuleMatch.LABEL, "wontfix", true, null), rule(RuleMatch.TYPE, "Bug", false, "defect")));
        model.setDiscussions(ItemSettings.builder().enabled(true).workItemType("task")
                .rules(List.of(rule(RuleMatch.CATEGORY, "Q&A", false, "task"), rule(RuleMatch.LABEL, "docs", true, " "))).build());

        assertThatCode(model::validate).doesNotThrowAnyException();
        model.getIssues().setRules(null);
        assertThatCode(model::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnIncompleteRule() {
        assertRejected(model -> model.getIssues().setRules(List.of(rule(null, "bug", false, "task"))), "Rule 1 for issues needs what to compare");
        assertRejected(model -> model.getIssues().setRules(java.util.Collections.singletonList(null)), "Rule 1 for issues needs what to compare");
        assertRejected(model -> model.getIssues().setRules(List.of(rule(RuleMatch.LABEL, "ok", true, null), rule(RuleMatch.LABEL, " ", false, "task"))),
                "Rule 2 for issues needs the value");
        assertRejected(model -> model.getIssues().setRules(List.of(rule(RuleMatch.LABEL, "bug", false, ""))), "Rule 1 for issues needs a work item type");
    }

    @Test
    void rejectsARuleThatComparesWhatTheItemsDoNotHave() {
        assertRejected(model -> model.getIssues().setRules(List.of(rule(RuleMatch.CATEGORY, "Q&A", false, "task"))), "do not have");
        assertRejected(model -> model.setDiscussions(ItemSettings.builder().enabled(true).workItemType("task")
                .rules(List.of(rule(RuleMatch.TYPE, "Bug", false, "task"))).build()), "Rule 1 for discussions");
    }

    @Test
    void describesARuleWithoutAMatchKind() {
        assertThat(new ItemRule().describe()).isEqualTo(" = null");
    }

    @Test
    void acceptsNoEpicAndAHyperlinkKey() {
        RepositorySettingsModel model = valid();
        model.getIssues().setEpicId(null);
        model.getIssues().setEpicLinkRole(null);
        model.getIssues().setDuplicateKey(DuplicateKey.HYPERLINK);
        model.getIssues().setDuplicateKeyField(null);

        assertThatCode(model::validate).doesNotThrowAnyException();
    }
}
