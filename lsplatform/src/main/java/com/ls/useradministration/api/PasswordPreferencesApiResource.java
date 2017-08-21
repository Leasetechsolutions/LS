/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ls.useradministration.api;

import java.util.Collection;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.ls.commands.domain.CommandWrapper;
import com.ls.commands.service.CommandWrapperBuilder;
import com.ls.commands.service.PortfolioCommandSourceWritePlatformService;
import com.ls.infrastructure.core.api.ApiRequestParameterHelper;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import com.ls.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import com.ls.infrastructure.security.service.PlatformSecurityContext;
import com.ls.useradministration.data.PasswordValidationPolicyData;
import com.ls.useradministration.service.PasswordValidationPolicyReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/" + PasswordPreferencesApiConstants.RESOURCE_NAME)
@Component
@Scope("singleton")
public class PasswordPreferencesApiResource {

    private final PlatformSecurityContext context;
    private final PasswordValidationPolicyReadPlatformService passwordValidationPolicyReadPlatformService;
    private final DefaultToApiJsonSerializer<PasswordValidationPolicyData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Autowired
    public PasswordPreferencesApiResource(final PlatformSecurityContext context,
            final PasswordValidationPolicyReadPlatformService readPlatformService,
            final DefaultToApiJsonSerializer<PasswordValidationPolicyData> toApiJsonSerializer,
            final ApiRequestParameterHelper apiRequestParameterHelper,
            final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService) {
        this.context = context;
        this.passwordValidationPolicyReadPlatformService = readPlatformService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
    }

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieve(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(PasswordPreferencesApiConstants.ENTITY_NAME);

        final PasswordValidationPolicyData passwordValidationPolicyData = this.passwordValidationPolicyReadPlatformService
                .retrieveActiveValidationPolicy();

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, passwordValidationPolicyData,
                PasswordPreferencesApiConstants.RESPONSE_DATA_PARAMETERS);
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String update(final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder() //
                .updatePasswordPreferences() //
                .withJson(apiRequestBodyAsJson) //
                .build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("/template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String template(@Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(PasswordPreferencesApiConstants.ENTITY_NAME);

        final Collection<PasswordValidationPolicyData> validationPolicies = this.passwordValidationPolicyReadPlatformService.retrieveAll();

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, validationPolicies, PasswordPreferencesApiConstants.RESPONSE_DATA_PARAMETERS);
    }

}