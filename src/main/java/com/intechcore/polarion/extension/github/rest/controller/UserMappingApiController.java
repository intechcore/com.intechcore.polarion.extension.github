package com.intechcore.polarion.extension.github.rest.controller;

import ch.sbb.polarion.extension.generic.rest.filter.Secured;
import com.intechcore.polarion.extension.github.settings.UserMapping;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Path;

/**
 * The user mapping for callers with a bearer token. It reads and saves with the rights of the caller.
 */
@Secured
@Path("/api")
@Singleton
public class UserMappingApiController extends UserMappingInternalController {

    public UserMappingApiController() {
        super();
    }

    public UserMappingApiController(UserMapping userMapping) {
        super(userMapping);
    }
}
