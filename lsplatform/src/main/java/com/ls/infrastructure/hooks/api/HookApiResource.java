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
package com.ls.infrastructure.hooks.api;

import static com.ls.infrastructure.hooks.api.HookApiConstants.HOOK_RESOURCE_NAME;
import static com.ls.infrastructure.hooks.api.HookApiConstants.RESPONSE_DATA_PARAMETERS;

import java.util.Collection;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
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
import com.ls.infrastructure.hooks.data.HookData;
import com.ls.infrastructure.hooks.service.HookReadPlatformService;
import com.ls.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/hooks")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Component
@Scope("singleton")
public class HookApiResource {

	private final PlatformSecurityContext context;
	private final HookReadPlatformService readPlatformService;
	private final DefaultToApiJsonSerializer<HookData> toApiJsonSerializer;
	private final ApiRequestParameterHelper apiRequestParameterHelper;
	private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

	@Autowired
	public HookApiResource(
			final PlatformSecurityContext context,
			final HookReadPlatformService readPlatformService,
			final DefaultToApiJsonSerializer<HookData> toApiJsonSerializer,
			final ApiRequestParameterHelper apiRequestParameterHelper,
			final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService) {
		this.context = context;
		this.readPlatformService = readPlatformService;
		this.toApiJsonSerializer = toApiJsonSerializer;
		this.apiRequestParameterHelper = apiRequestParameterHelper;
		this.commandsSourceWritePlatformService = commandsSourceWritePlatformService;
	}

	@GET
	public String retrieveHooks(@Context final UriInfo uriInfo) {

		this.context.authenticatedUser().validateHasReadPermission(
				HOOK_RESOURCE_NAME);

		final Collection<HookData> hooks = this.readPlatformService
				.retrieveAllHooks();

		final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper
				.process(uriInfo.getQueryParameters());
		return this.toApiJsonSerializer.serialize(settings, hooks,
				RESPONSE_DATA_PARAMETERS);
	}

	@GET
	@Path("{hookId}")
	public String retrieveHook(@PathParam("hookId") final Long hookId,
			@Context final UriInfo uriInfo) {

		this.context.authenticatedUser().validateHasReadPermission(
				HOOK_RESOURCE_NAME);

		final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper
				.process(uriInfo.getQueryParameters());

		HookData hook = this.readPlatformService.retrieveHook(hookId);

		if (settings.isTemplate()) {
			final HookData hookData = this.readPlatformService
					.retrieveNewHookDetails(hook.getTemplateName());
			hook = HookData.templateExisting(hook, hookData.getTemplates(),
					hookData.getGroupings());
		}
		return this.toApiJsonSerializer.serialize(settings, hook,
				RESPONSE_DATA_PARAMETERS);
	}

	@GET
	@Path("template")
	public String template(@Context final UriInfo uriInfo) {

		this.context.authenticatedUser().validateHasReadPermission(
				HOOK_RESOURCE_NAME);

		final HookData hook = this.readPlatformService
				.retrieveNewHookDetails(null);

		final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper
				.process(uriInfo.getQueryParameters());
		return this.toApiJsonSerializer.serialize(settings, hook,
				RESPONSE_DATA_PARAMETERS);
	}

	@POST
	public String createHook(final String apiRequestBodyAsJson) {

		final CommandWrapper commandRequest = new CommandWrapperBuilder()
				.createHook().withJson(apiRequestBodyAsJson).build();

		final CommandProcessingResult result = this.commandsSourceWritePlatformService
				.logCommandSource(commandRequest);

		return this.toApiJsonSerializer.serialize(result);
	}

	@PUT
	@Path("{hookId}")
	public String updateHook(@PathParam("hookId") final Long hookId,
			final String apiRequestBodyAsJson) {

		final CommandWrapper commandRequest = new CommandWrapperBuilder()
				.updateHook(hookId).withJson(apiRequestBodyAsJson).build();

		final CommandProcessingResult result = this.commandsSourceWritePlatformService
				.logCommandSource(commandRequest);

		return this.toApiJsonSerializer.serialize(result);
	}

	@DELETE
	@Path("{hookId}")
	public String deleteHook(@PathParam("hookId") final Long hookId) {

		final CommandWrapper commandRequest = new CommandWrapperBuilder()
				.deleteHook(hookId).build();

		final CommandProcessingResult result = this.commandsSourceWritePlatformService
				.logCommandSource(commandRequest);

		return this.toApiJsonSerializer.serialize(result);
	}

}
