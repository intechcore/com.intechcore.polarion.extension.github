package com.intechcore.polarion.extension.github.integration;

import ch.sbb.polarion.extension.generic.context.CurrentContextConfig;
import ch.sbb.polarion.extension.generic.context.CurrentContextExtension;
import ch.sbb.polarion.extension.generic.rest.controller.settings.NamedSettingsInternalController;
import ch.sbb.polarion.extension.generic.rest.model.ErrorEntity;
import ch.sbb.polarion.extension.generic.settings.NamedSettingsRegistry;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intechcore.polarion.extension.github.client.GithubClient;
import com.intechcore.polarion.extension.github.client.GithubClientException;
import com.intechcore.polarion.extension.github.job.GithubImportJobUnitImpl;
import com.intechcore.polarion.extension.github.rest.controller.ImportApiController;
import com.intechcore.polarion.extension.github.rest.controller.ImportInternalController;
import com.intechcore.polarion.extension.github.rest.exception.GithubClientExceptionMapper;
import com.intechcore.polarion.extension.github.rest.model.ImportRequest;
import com.intechcore.polarion.extension.github.service.ImportEntry;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.service.ImportStatus;
import com.intechcore.polarion.extension.github.service.WriteTransaction;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.platform.context.IContext;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.ILogger;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.location.Location;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the import end to end, as the pages and the job drive it: the settings go in as JSON through
 * the settings endpoint of generic, the import endpoint and the job run the real import, the client
 * talks HTTP to a server that answers like GitHub, and the work items land in an in-memory Polarion
 * whose search reads the SQL the import sends. Only Polarion itself is replaced, so these run in CI.
 */
@ExtendWith(CurrentContextExtension.class)
@CurrentContextConfig("github")
class ImportIntegrationTest {

    private static final String SCOPE = "project/elibrary/";
    private static final String ISSUES = "https://github.com/acme/tool/issues/";

    private FakeGithub github;
    private FakePolarion polarion;
    private MockedStatic<ScopeUtils> scopeUtils;
    private RepositorySettings repositorySettings;
    private NamedSettingsInternalController settingsEndpoint;
    private ImportInternalController importEndpoint;
    private ImportService importService;

    @BeforeEach
    void setUp() {
        github = new FakeGithub();
        polarion = new FakePolarion(Set.of("task", "defect", "changerequest", "question"), Set.of("parent", "relates_to"));

        // The project folder is the one thing generic asks the running platform for.
        scopeUtils = mockStatic(ScopeUtils.class, Mockito.CALLS_REAL_METHODS);
        scopeUtils.when(() -> ScopeUtils.getContextLocationByProject(FakePolarion.PROJECT))
                .thenReturn(Location.getLocation("default:/Demo Projects/elibrary"));

        repositorySettings = new RepositorySettings(new InMemorySettingsService());
        NamedSettingsRegistry.INSTANCE.getAll().removeIf(settings -> RepositorySettings.FEATURE_NAME.equals(settings.getFeatureName()));
        NamedSettingsRegistry.INSTANCE.register(List.of(repositorySettings));
        settingsEndpoint = new NamedSettingsInternalController(polarion.polarionService);

        importService = new ImportService(polarion.polarionService, new GithubClient(github.url()), new WriteTransaction() {
            @Override
            public <T> T execute(Supplier<T> action) {
                return action.get();
            }
        });
        importEndpoint = new ImportInternalController(polarion.polarionService, repositorySettings, importService);
    }

    @AfterEach
    void tearDown() {
        NamedSettingsRegistry.INSTANCE.getAll().remove(repositorySettings);
        scopeUtils.close();
        github.close();
    }

    /** Saves a repository setting the way the Repositories page does. */
    private void saveSetting(String name, String json) {
        settingsEndpoint.saveSetting(RepositorySettings.FEATURE_NAME, name, SCOPE, json);
    }

    private static List<String> outcome(ImportResult result) {
        return result.getEntries().stream().map(entry -> entry.getKind() + " " + entry.getNumber() + " " + entry.getStatus()).toList();
    }

    private static final String ISSUES_AND_DISCUSSIONS = """
            {
              "repository": "acme/tool",
              "shortName": "Tool",
              "enabled": true,
              "issues": {
                "enabled": true,
                "workItemType": "task",
                "titleTemplate": "[GitHub] {shortName} : {title}",
                "descriptionTemplate": "<a href=\\"{url}\\">{url}</a><p>{body}</p>",
                "duplicateKey": "HYPERLINK",
                "epicId": "EL-1",
                "epicLinkRole": "parent",
                "fields": {"severity": "minor", "component": "core"},
                "rules": [
                  {"match": "LABEL", "value": "wontfix", "skip": true},
                  {"match": "TYPE", "value": "bug", "workItemType": "defect", "fields": {"severity": "major"}},
                  {"match": "LABEL", "value": "enhancement", "workItemType": "changerequest"}
                ]
              },
              "discussions": {
                "enabled": true,
                "workItemType": "task",
                "titleTemplate": "[GitHub discussion] {shortName} : {category} : {title}",
                "rules": [{"match": "CATEGORY", "value": "Q&A", "workItemType": "question"}]
              }
            }""";

    @Test
    void aDryRunListsTheOpenItemsOfBothPagesAndCreatesNothing() {
        saveSetting("tool", ISSUES_AND_DISCUSSIONS);

        ImportResult result = importEndpoint.importRepository(FakePolarion.PROJECT, "tool", true, null);

        // The pull request and the closed discussion stay out. The rule leaves the "WontFix" issue out.
        assertThat(outcome(result)).containsExactly(
                "ISSUE 7 NEW", "ISSUE 9 SKIPPED", "ISSUE 10 NEW", "DISCUSSION 30 NEW");
        assertThat(result.getEntries().get(1).getMessage()).isEqualTo("by the rule Label = wontfix");
        assertThat(polarion.saved).isEmpty();
        assertThat(github.requests()).containsExactly(
                "/repos/acme/tool/issues?state=open&per_page=100",
                "/repos/acme/tool/issues?state=open&per_page=100&page=2",
                "/repos/acme/tool/discussions?per_page=100");
    }

    @Test
    void anImportCreatesTheWorkItemsAndTheNextRunFindsThem() {
        saveSetting("tool", ISSUES_AND_DISCUSSIONS);

        ImportResult first = importEndpoint.importRepository(FakePolarion.PROJECT, "tool", false, null);

        assertThat(outcome(first)).containsExactly(
                "ISSUE 7 CREATED", "ISSUE 9 SKIPPED", "ISSUE 10 CREATED", "DISCUSSION 30 CREATED");
        assertThat(polarion.saved).hasSize(3);

        FakePolarion.WorkItem bug = polarion.saved.get(0);
        assertThat(bug.type).isEqualTo("defect");
        assertThat(bug.title).isEqualTo("[GitHub] Tool : Crash on start");
        assertThat(bug.description.getContent()).isEqualTo(
                "<a href=\"" + ISSUES + "7\">" + ISSUES + "7</a><p>It crashes &lt;b&gt;at once&lt;/b&gt;.</p>");
        assertThat(bug.hyperlinks).containsExactly(ISSUES + "7");
        assertThat(bug.fields).containsEntry("severity", "major").containsEntry("component", "core");
        assertThat(bug.links).containsExactly("parent:EL-1");

        FakePolarion.WorkItem enhancement = polarion.saved.get(1);
        assertThat(enhancement.type).isEqualTo("changerequest");
        assertThat(enhancement.fields).containsEntry("severity", "minor");

        FakePolarion.WorkItem discussion = polarion.saved.get(2);
        assertThat(discussion.type).isEqualTo("question");
        assertThat(discussion.title).isEqualTo("[GitHub discussion] Tool : Q&A : How to configure");
        assertThat(discussion.description.getContent()).isEqualTo(
                "<a href=\"https://github.com/acme/tool/discussions/30\">https://github.com/acme/tool/discussions/30</a>");

        ImportResult second = importEndpoint.importRepository(FakePolarion.PROJECT, "tool", false, null);

        assertThat(outcome(second)).containsExactly(
                "ISSUE 7 EXISTS", "ISSUE 9 SKIPPED", "ISSUE 10 EXISTS", "DISCUSSION 30 EXISTS");
        assertThat(second.getEntries()).extracting(ImportEntry::getWorkItemId).containsExactly("EL-100", null, "EL-101", "EL-102");
        assertThat(polarion.saved).hasSize(3);
    }

    @Test
    void aCustomFieldCanKeepTheUrl() {
        saveSetting("tool", """
                {"repository": "acme/tool", "shortName": "Tool",
                 "issues": {"enabled": true, "workItemType": "task", "titleTemplate": "{title}",
                            "duplicateKey": "CUSTOM_FIELD", "duplicateKeyField": "githubUrl"}}""");

        importEndpoint.importRepository(FakePolarion.PROJECT, "tool", false, null);
        ImportResult second = importEndpoint.importRepository(FakePolarion.PROJECT, "tool", true, null);

        assertThat(polarion.saved).extracting(item -> item.fields.get("githubUrl"))
                .containsExactly(ISSUES + "7", ISSUES + "9", ISSUES + "10");
        assertThat(polarion.saved).allSatisfy(item -> assertThat(item.hyperlinks).isEmpty());
        assertThat(second.getEntries()).extracting(ImportEntry::getStatus).containsOnly(ImportStatus.EXISTS);
        assertThat(polarion.queries).last().asString().contains("CF.C_NAME = 'githubUrl'");
    }

    @Test
    void theImportPageCreatesOnlyTheSelectedItems() {
        saveSetting("tool", ISSUES_AND_DISCUSSIONS);

        ImportResult result = importEndpoint.importRepository(FakePolarion.PROJECT, "tool", false, new ImportRequest(List.of(ISSUES + "10")));

        assertThat(outcome(result)).containsExactly("ISSUE 10 CREATED");
        assertThat(polarion.saved).extracting(item -> item.title).containsExactly("[GitHub] Tool : Export to CSV");
    }

    @Test
    void theTokenEndpointRunsTheSameImport() {
        saveSetting("tool", ISSUES_AND_DISCUSSIONS);

        ImportResult result = new ImportApiController(polarion.polarionService, repositorySettings, importService)
                .importRepository(FakePolarion.PROJECT, "tool", true, null);

        assertThat(result.getEntries()).hasSize(4);
    }

    @Test
    void theResultReachesTheImportPageInTheShapeItReads() throws Exception {
        saveSetting("tool", ISSUES_AND_DISCUSSIONS);

        JsonNode json = new ObjectMapper().valueToTree(importEndpoint.importRepository(FakePolarion.PROJECT, "tool", true, null));

        // The fields of ImportResult and ImportEntry in ui/src/types.ts.
        assertThat(fieldNames(json)).containsExactlyInAnyOrder("repository", "dryRun", "entries");
        assertThat(fieldNames(json.get("entries").get(1)))
                .containsExactlyInAnyOrder("kind", "number", "title", "url", "status", "workItemId", "message");
        assertThat(json.get("entries").get(1).get("status").asText()).isEqualTo("SKIPPED");
        assertThat(json.get("entries").get(0).get("kind").asText()).isEqualTo("ISSUE");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    @Test
    void turnedOffDiscussionsFailTheImportAsBadGatewayAndCreateNothing() {
        saveSetting("tool", ISSUES_AND_DISCUSSIONS);
        github.discussionsTurnedOff();

        assertThatThrownBy(() -> importEndpoint.importRepository(FakePolarion.PROJECT, "tool", false, null))
                .isInstanceOfSatisfying(GithubClientException.class, e -> {
                    Response response = new GithubClientExceptionMapper().toResponse(e);
                    assertThat(response.getStatus()).isEqualTo(502);
                    assertThat(((ErrorEntity) response.getEntity()).getMessage())
                            .isEqualTo("Discussions are turned off in the repository acme/tool");
                });
        assertThat(polarion.saved).isEmpty();
    }

    @Test
    void anExhaustedRateLimitAnswersTooManyRequests() {
        saveSetting("tool", ISSUES_AND_DISCUSSIONS);
        github.rateLimitUsedUp();

        assertThatThrownBy(() -> importEndpoint.importRepository(FakePolarion.PROJECT, "tool", true, null))
                .isInstanceOfSatisfying(GithubClientException.class, e ->
                        assertThat(new GithubClientExceptionMapper().toResponse(e).getStatus()).isEqualTo(429));
    }

    @Test
    void theSettingsEndpointRefusesASettingTheImportCannotRun() {
        assertThatThrownBy(() -> saveSetting("tool", """
                {"repository": "acme/tool", "shortName": "Tool",
                 "issues": {"enabled": true, "workItemType": "task", "titleTemplate": "{title}",
                            "rules": [{"match": "CATEGORY", "value": "Q&A", "workItemType": "task"}]}}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rule 1 for issues compares what these items do not have");
        assertThat(settingsEndpoint.readSettingNames(RepositorySettings.FEATURE_NAME, SCOPE)).isEmpty();
    }

    @Test
    void theJobImportsTheEnabledSettingsOfItsProject() {
        saveSetting("tool", ISSUES_AND_DISCUSSIONS);
        saveSetting("paused", ISSUES_AND_DISCUSSIONS.replace("\"enabled\": true,\n  \"issues\"", "\"enabled\": false,\n  \"issues\""));
        assertThat(settingsEndpoint.readSettingNames(RepositorySettings.FEATURE_NAME, SCOPE)).hasSize(2);

        IProjectService projectService = mock(IProjectService.class);
        IProject project = mock(IProject.class);
        IContext scope = mock(IContext.class);
        IContextId contextId = mock(IContextId.class);
        when(polarion.polarionService.getProjectService()).thenReturn(projectService);
        when(scope.getId()).thenReturn(contextId);
        when(projectService.getProjectForContextId(contextId)).thenReturn(project);
        when(project.getId()).thenReturn(FakePolarion.PROJECT);
        ILogger logger = mock(ILogger.class);
        GithubImportJobUnitImpl job = new GithubImportJobUnitImpl("import", mock(IJobUnitFactory.class),
                polarion.polarionService, repositorySettings, importService);
        job.setScope(scope);
        job.setLogger(logger);
        job.setJob(mock(IJob.class));

        IJobStatus status = job.run(mock(IProgressMonitor.class));

        assertThat(status.getType()).isEqualTo(IJobStatus.JobStatusType.STATUS_TYPE_OK);
        assertThat(polarion.saved).hasSize(3);
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeastOnce()).info(lines.capture());
        assertThat(lines.getAllValues())
                .contains("Repository setting 'paused' is disabled, skipped")
                .contains("Repository setting 'tool' (acme/tool): 3 created, 0 new, 0 existing, 1 left out, 0 failed");
    }
}
