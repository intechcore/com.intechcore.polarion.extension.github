package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.service.PolarionService;
import ch.sbb.polarion.extension.generic.settings.SettingId;
import com.intechcore.polarion.extension.github.rest.model.HideRequest;
import com.intechcore.polarion.extension.github.rest.model.ImportRequest;
import com.intechcore.polarion.extension.github.rest.model.ProjectItems;
import com.intechcore.polarion.extension.github.service.ImportResult;
import com.intechcore.polarion.extension.github.service.ImportService;
import com.intechcore.polarion.extension.github.service.ProjectItemsReader;
import com.intechcore.polarion.extension.github.settings.HiddenItems;
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

import java.util.List;
import java.util.Set;

@Tag(name = "Import")
@Hidden
@Path("/internal")
@Singleton
public class ImportInternalController {

    protected final PolarionService polarionService;
    private final RepositorySettings repositorySettings;
    private final ImportService importService;
    private final HiddenItems hiddenItems;

    public ImportInternalController() {
        this(new PolarionService(), new RepositorySettings(), new ImportService(), new HiddenItems());
    }

    public ImportInternalController(PolarionService polarionService, RepositorySettings repositorySettings, ImportService importService,
                                    HiddenItems hiddenItems) {
        this.polarionService = polarionService;
        this.repositorySettings = repositorySettings;
        this.importService = importService;
        this.hiddenItems = hiddenItems;
    }

    @Operation(summary = "Returns the open issues and discussions of all repository settings of a project, with what the import would do with each")
    @GET
    @Path("/projects/{projectId}/items")
    @Produces(MediaType.APPLICATION_JSON)
    public ProjectItems getItems(@Parameter(description = "The project") @PathParam("projectId") String projectId,
                                 @Parameter(description = "True to read GitHub again instead of the lists of the last five minutes."
                                         + " A list read within the last minute stays.")
                                 @QueryParam("refresh") @DefaultValue("false") boolean refresh,
                                 @Parameter(description = "The settings to read, in the order to show them. None reads all settings of the project.")
                                 @QueryParam("settings") List<String> settings) {
        return new ProjectItemsReader(repositorySettings, importService, hiddenItems)
                .read(projectId, refresh, settings == null ? List.of() : settings);
    }

    @Operation(summary = "Hides GitHub items on the GitHub page of a project, or shows them again. Answers with all URLs the project hides")
    @POST
    @Path("/projects/{projectId}/hidden-items")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Set<String> hideItems(@Parameter(description = "The project") @PathParam("projectId") String projectId, HideRequest request) {
        if (request == null || request.getUrls() == null || request.getUrls().isEmpty()) {
            throw new IllegalArgumentException("Name the GitHub URLs to hide or to show");
        }
        return hiddenItems.change(projectId, request.getUrls(), request.isHidden());
    }

    @Operation(summary = "Updates the work items of the given GitHub items whose title, description, type, field values or epic link differ")
    @POST
    @Path("/projects/{projectId}/repositories/{name}/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ImportResult updateRepository(@Parameter(description = "The project of the work items") @PathParam("projectId") String projectId,
                                         @Parameter(description = "The name of the repository setting") @PathParam("name") String name,
                                         ImportRequest request) {
        if (request == null || request.getUrls() == null || request.getUrls().isEmpty()) {
            throw new IllegalArgumentException("Name the GitHub URLs whose work items to update");
        }
        RepositorySettingsModel settings = repositorySettings.load(projectId, SettingId.fromName(name));
        return importService.updateRepository(projectId, settings, request.getUrls());
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
