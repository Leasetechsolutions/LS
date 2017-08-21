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
package com.ls.portfolio.self.account.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.ls.infrastructure.configuration.domain.ConfigurationDomainService;
import com.ls.infrastructure.core.api.ApiRequestParameterHelper;
import com.ls.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import com.ls.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import com.ls.infrastructure.security.service.PlatformSecurityContext;
import com.ls.portfolio.account.api.AccountTransfersApiResource;
import com.ls.portfolio.account.service.AccountTransfersReadPlatformService;
import com.ls.portfolio.self.account.data.SelfAccountTemplateData;
import com.ls.portfolio.self.account.data.SelfAccountTransferData;
import com.ls.portfolio.self.account.data.SelfAccountTransferDataValidator;
import com.ls.portfolio.self.account.exception.BeneficiaryTransferLimitExceededException;
import com.ls.portfolio.self.account.exception.DailyTPTTransactionAmountLimitExceededException;
import com.ls.portfolio.self.account.service.SelfAccountTransferReadService;
import com.ls.portfolio.self.account.service.SelfBeneficiariesTPTReadPlatformService;
import com.ls.useradministration.domain.AppUser;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Path("/self/accounttransfers")
@Component
@Scope("singleton")
public class SelfAccountTransferApiResource {

	private final PlatformSecurityContext context;
	private final DefaultToApiJsonSerializer<SelfAccountTransferData> toApiJsonSerializer;
	private final AccountTransfersApiResource accountTransfersApiResource;
	private final SelfAccountTransferReadService selfAccountTransferReadService;
	private final ApiRequestParameterHelper apiRequestParameterHelper;
	private final SelfAccountTransferDataValidator dataValidator;
	private final SelfBeneficiariesTPTReadPlatformService tptBeneficiaryReadPlatformService;
	private final ConfigurationDomainService configurationDomainService;
	private final AccountTransfersReadPlatformService accountTransfersReadPlatformService;

	@Autowired
	public SelfAccountTransferApiResource(
			final PlatformSecurityContext context,
			final DefaultToApiJsonSerializer<SelfAccountTransferData> toApiJsonSerializer,
			final AccountTransfersApiResource accountTransfersApiResource,
			final SelfAccountTransferReadService selfAccountTransferReadService,
			final ApiRequestParameterHelper apiRequestParameterHelper,
			final SelfAccountTransferDataValidator dataValidator,
			final SelfBeneficiariesTPTReadPlatformService tptBeneficiaryReadPlatformService,
			final ConfigurationDomainService configurationDomainService,
			final AccountTransfersReadPlatformService accountTransfersReadPlatformService) {
		this.context = context;
		this.toApiJsonSerializer = toApiJsonSerializer;
		this.accountTransfersApiResource = accountTransfersApiResource;
		this.selfAccountTransferReadService = selfAccountTransferReadService;
		this.apiRequestParameterHelper = apiRequestParameterHelper;
		this.dataValidator = dataValidator;
		this.tptBeneficiaryReadPlatformService = tptBeneficiaryReadPlatformService;
		this.configurationDomainService = configurationDomainService;
		this.accountTransfersReadPlatformService = accountTransfersReadPlatformService;
	}

	@GET
	@Path("template")
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	public String template(
			@DefaultValue("") @QueryParam("type") final String type,
			@Context final UriInfo uriInfo) {

		AppUser user = this.context.authenticatedUser();
		final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper
				.process(uriInfo.getQueryParameters());
		Collection<SelfAccountTemplateData> selfTemplateData = this.selfAccountTransferReadService
				.retrieveSelfAccountTemplateData(user);

		if (type.equals("tpt")) {
			Collection<SelfAccountTemplateData> tptTemplateData = this.tptBeneficiaryReadPlatformService
					.retrieveTPTSelfAccountTemplateData(user);
			return this.toApiJsonSerializer.serialize(settings,
					new SelfAccountTransferData(selfTemplateData,
							tptTemplateData));
		}

		return this.toApiJsonSerializer
				.serialize(settings, new SelfAccountTransferData(
						selfTemplateData, selfTemplateData));
	}

	@POST
	@Consumes({ MediaType.APPLICATION_JSON })
	@Produces({ MediaType.APPLICATION_JSON })
	public String create(
			@DefaultValue("") @QueryParam("type") final String type,
			final String apiRequestBodyAsJson) {
		Map<String, Object> params = this.dataValidator.validateCreate(type,
				apiRequestBodyAsJson);
		if (type.equals("tpt")) {
			checkForLimits(params);
		}
		return this.accountTransfersApiResource.create(apiRequestBodyAsJson);
	}

	private void checkForLimits(Map<String, Object> params) {
		SelfAccountTemplateData fromAccount = (SelfAccountTemplateData) params
				.get("fromAccount");
		SelfAccountTemplateData toAccount = (SelfAccountTemplateData) params
				.get("toAccount");
		LocalDate transactionDate = (LocalDate) params.get("transactionDate");
		BigDecimal transactionAmount = (BigDecimal) params
				.get("transactionAmount");

		AppUser user = this.context.authenticatedUser();
		Long transferLimit = this.tptBeneficiaryReadPlatformService
				.getTransferLimit(user.getId(), toAccount.getAccountId(),
						toAccount.getAccountType());
		if (transferLimit != null && transferLimit > 0) {
			if (transactionAmount.compareTo(new BigDecimal(transferLimit)) > 0) {
				throw new BeneficiaryTransferLimitExceededException();
			}
		}

		if (this.configurationDomainService.isDailyTPTLimitEnabled()) {
			Long dailyTPTLimit = this.configurationDomainService
					.getDailyTPTLimit();
			if (dailyTPTLimit != null && dailyTPTLimit > 0) {
				BigDecimal dailyTPTLimitBD = new BigDecimal(dailyTPTLimit);
				BigDecimal totTransactionAmount = this.accountTransfersReadPlatformService
						.getTotalTransactionAmount(fromAccount.getAccountId(),
								fromAccount.getAccountType(), transactionDate);
				if (totTransactionAmount != null
						&& totTransactionAmount.compareTo(BigDecimal.ZERO) > 0) {
					if (dailyTPTLimitBD.compareTo(totTransactionAmount) <= 0
							|| dailyTPTLimitBD.compareTo(totTransactionAmount
									.add(transactionAmount)) < 0) {
						throw new DailyTPTTransactionAmountLimitExceededException(
								fromAccount.getAccountId(),
								fromAccount.getAccountType());
					}
				}
			}
		}
	}

}
