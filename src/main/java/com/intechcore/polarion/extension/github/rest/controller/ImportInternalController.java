package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import ch.sbb.polarion.extension.generic.settings.SettingName;
import ch.sbb.polarion.extension.generic.util.ScopeUtils;
import com.intechcore.polarion.extension.github.client.GithubClientException;
import com.intechcore.polarion.extension.github.rest.model.ImportRequest;
import com.intechcore.polarion.extension.github.rest.model.ProjectItems;
import com.intechcore.polarion.extension.github.rest.model.RepositoryState;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.settings.RepositorySettings;
import com.intechcore.polarion.extension.github.settings.RepositorySettingsModel;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jetbrains.annotations.Nullable;

@Tag(name = "Import")
@Hidden
@Path("/internal")
@Singleton
public class ImportInternalController {

    protected final PolarionService polarionService;
    private final RepositorySettings repositorySettings;
    private final ImportService importService;

    public ImportInternalController() {
        this(new PolarionService(), new RepositorySettings(), new ImportService());
    }

    public ImportInternalController(PolarionService polarionService, RepositorySettings repositorySettings, ImportService importService) {
        this.polarionService = polarionService;
        this.repositorySettings = repositorySettings;
        this.importService = importService;
    }

    @Operation(summary = "Returns the open issues and discussions of all repository settings of a project, with what the import would do with each")
    @GET
    @Path("/projects/{projectId}/items")
    @Produces(MediaType.APPLICATION_JSON)
    public ProjectItems getItems(@Parameter(description = "The project") @PathParam("projectId") String projectId,
                                 @Parameter(description = "True to read GitHub again instead of the lists of the last five minutes."
                                         + " A list read within the last minute stays.")
                                 @QueryParam("refresh") @DefaultValue("false") boolean refresh) {
        String scope = ScopeUtils.getScopeFromProject(projectId);
        ProjectItems items = new ProjectItems();
        for (SettingName name : repositorySettings.readNames(scope)) {
            RepositoryState state = new RepositoryState(name.getName(), null, null, null);
            items.getRepositories().add(state);
            try {
                RepositorySettingsModel settings = repositorySettings.read(scope, SettingId.fromName(name.getName()), null);
                state.setRepository(settings.getRepository());
                if (refresh) {
                    importService.refresh(settings);
                }
                ImportResult result = importService.importRepository(projectId, settings, true, null);
                state.setReadAt(result.getReadAt());
                items.getEntries().addAll(result.getEntries());
            } catch (GithubClientException | IllegalArgumentException e) {
                // One repository that fails leaves the others readable.
                state.setError(e.getMessage());
            }
        }
        return items;
    }

    @Operation(summary = "Creates work items from the open issues and discussions of a configured repository")
    @POST
    @Path("/projects/{projectId}/repositories/{name}/import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ImportResult importRepository(@Parameter(description = "The project that gets the work items") @PathParam("projectId") String projectId,
                                         @Parameter(description = "The name of the repository setting") @PathParam("name") String name,
                                         @Parameter(description = "True to report only what the import would do") @QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
                                         @Nullable ImportRequest request) {
        RepositorySettingsModel settings = repositorySettings.load(projectId, SettingId.fromName(name));
        return importService.importRepository(projectId, settings, dryRun, request == null ? null : request.getUrls());
    }
}
