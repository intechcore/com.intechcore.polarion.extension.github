package com.intechcore.polarion.extension.github.rest.controller;

import com.intechcore.polarion.extension.github.rest.model.UserMappingContent;
import com.intechcore.polarion.extension.github.settings.UserMapping;
import com.intechcore.polarion.extension.github.settings.UserMappingModel;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * The Polarion users of GitHub logins, globally or for a project. The import assigns work items to them.
 */
@Tag(name = "User mapping")
@Hidden
@Path("/internal")
@Singleton
public class UserMappingInternalController {

    private static final String SCOPE_DESCRIPTION = "Empty for the global mapping, or the scope of a project: project/{projectId}/";

    private final UserMapping userMapping;

    public UserMappingInternalController() {
        this(new UserMapping());
    }

    public UserMappingInternalController(UserMapping userMapping) {
        this.userMapping = userMapping;
    }

    @Operation(summary = "Returns the user mapping a scope uses: its own, or the global one")
    @GET
    @Path("/user-mapping")
    @Produces(MediaType.APPLICATION_JSON)
    public UserMappingContent getUserMapping(@Parameter(description = SCOPE_DESCRIPTION) @QueryParam("scope") @DefaultValue("") String scope) {
        return content(scope);
    }

    @Operation(summary = "Saves the user mapping of a scope")
    @PUT
    @Path("/user-mapping")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserMappingContent saveUserMapping(@Parameter(description = SCOPE_DESCRIPTION) @QueryParam("scope") @DefaultValue("") String scope,
                                              UserMappingModel mapping) {
        if (mapping == null || mapping.getUsers() == null) {
            throw new IllegalArgumentException("Give the users of the mapping");
        }
        userMapping.save(scope, mapping);
        return content(scope);
    }

    @Operation(summary = "Removes the user mapping of a project, which then uses the global one")
    @DELETE
    @Path("/user-mapping")
    @Produces(MediaType.APPLICATION_JSON)
    public UserMappingContent resetUserMapping(@Parameter(description = "The scope of a project: project/{projectId}/") @QueryParam("scope") @DefaultValue("") String scope) {
        userMapping.reset(scope);
        return content(scope);
    }

    private UserMappingContent content(String scope) {
        return new UserMappingContent(userMapping.read(scope).getUsers(), !userMapping.isOwn(scope));
    }
}
