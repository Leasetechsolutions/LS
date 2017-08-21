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
package com.ls.infrastructure.reportmailingjob.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.ls.infrastructure.core.api.ApiRequestParameterHelper;
import com.ls.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import com.ls.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import com.ls.infrastructure.core.service.Page;
import com.ls.infrastructure.core.service.SearchParameters;
import com.ls.infrastructure.reportmailingjob.ReportMailingJobConstants;
import com.ls.infrastructure.reportmailingjob.data.ReportMailingJobRunHistoryData;
import com.ls.infrastructure.reportmailingjob.service.ReportMailingJobRunHistoryReadPlatformService;
import com.ls.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/" + ReportMailingJobConstants.REPORT_MAILING_JOB_RUN_HISTORY_RESOURCE_NAME)
@Component
@Scope("singleton")
public class ReportMailingJobRunHistoryApiResource {
    
    private final PlatformSecurityContext platformSecurityContext;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final DefaultToApiJsonSerializer<ReportMailingJobRunHistoryData> reportMailingToApiJsonSerializer;
    private final ReportMailingJobRunHistoryReadPlatformService reportMailingJobRunHistoryReadPlatformService;
    
    @Autowired
    public ReportMailingJobRunHistoryApiResource(final PlatformSecurityContext platformSecurityContext, 
            final ApiRequestParameterHelper apiRequestParameterHelper, 
            final DefaultToApiJsonSerializer<ReportMailingJobRunHistoryData> reportMailingToApiJsonSerializer, 
            final ReportMailingJobRunHistoryReadPlatformService reportMailingJobRunHistoryReadPlatformService) {
        this.platformSecurityContext = platformSecurityContext;
        this.apiRequestParameterHelper = apiRequestParameterHelper;
        this.reportMailingToApiJsonSerializer = reportMailingToApiJsonSerializer;
        this.reportMailingJobRunHistoryReadPlatformService = reportMailingJobRunHistoryReadPlatformService;
    }
    
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String retrieveAllByReportMailingJobId(@Context final UriInfo uriInfo, 
            @QueryParam("reportMailingJobId") final Long reportMailingJobId, @QueryParam("offset") final Integer offset,
            @QueryParam("limit") final Integer limit, @QueryParam("orderBy") final String orderBy,
            @QueryParam("sortOrder") final String sortOrder) {
        this.platformSecurityContext.authenticatedUser().validateHasReadPermission(ReportMailingJobConstants.REPORT_MAILING_JOB_ENTITY_NAME);
        final SearchParameters searchParameters = SearchParameters.fromReportMailingJobRunHistory(offset, limit, orderBy, sortOrder);
        
        final Page<ReportMailingJobRunHistoryData> reportMailingJobRunHistoryData = this.reportMailingJobRunHistoryReadPlatformService.
                retrieveRunHistoryByJobId(reportMailingJobId, searchParameters);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        
        return this.reportMailingToApiJsonSerializer.serialize(settings, reportMailingJobRunHistoryData);
    }
}
