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
package com.ls.portfolio.shareproducts.service;

import java.math.BigDecimal;
import java.util.Map;

import javax.persistence.PersistenceException;

import org.apache.commons.lang.exception.ExceptionUtils;
import com.ls.accounting.producttoaccountmapping.service.ProductToGLAccountMappingWritePlatformService;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.infrastructure.core.data.CommandProcessingResultBuilder;
import com.ls.infrastructure.core.exception.PlatformDataIntegrityException;
import com.ls.infrastructure.core.serialization.FromJsonHelper;
import com.ls.portfolio.shareproducts.constants.ShareProductApiConstants;
import com.ls.portfolio.shareproducts.domain.ShareProduct;
import com.ls.portfolio.shareproducts.domain.ShareProductDividendPayOutDetails;
import com.ls.portfolio.shareproducts.domain.ShareProductDividentPayOutDetailsRepositoryWrapper;
import com.ls.portfolio.shareproducts.domain.ShareProductRepositoryWrapper;
import com.ls.portfolio.shareproducts.exception.DividentProcessingException;
import com.ls.portfolio.shareproducts.serialization.ShareProductDataSerializer;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;

@Service
public class ShareProductWritePlatformServiceJpaRepositoryImpl implements ShareProductWritePlatformService {

    private final ShareProductRepositoryWrapper repository;
    private final ShareProductDataSerializer serializer;
    private final FromJsonHelper fromApiJsonHelper;
    private final ShareProductDividentPayOutDetailsRepositoryWrapper shareProductDividentPayOutDetailsRepository;
    private final ShareProductDividendAssembler shareProductDividendAssembler;
    private final ProductToGLAccountMappingWritePlatformService accountMappingWritePlatformService;

    @Autowired
    public ShareProductWritePlatformServiceJpaRepositoryImpl(final ShareProductRepositoryWrapper repository,
            final ShareProductDataSerializer serializer, final FromJsonHelper fromApiJsonHelper,
            final ShareProductDividentPayOutDetailsRepositoryWrapper shareProductDividentPayOutDetailsRepositor,
            final ShareProductDividendAssembler shareProductDividendAssembler,
            final ProductToGLAccountMappingWritePlatformService accountMappingWritePlatformService) {
        this.repository = repository;
        this.serializer = serializer;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.shareProductDividentPayOutDetailsRepository = shareProductDividentPayOutDetailsRepositor;
        this.shareProductDividendAssembler = shareProductDividendAssembler;
        this.accountMappingWritePlatformService = accountMappingWritePlatformService;
    }

    @Override
    public CommandProcessingResult createShareProduct(JsonCommand jsonCommand) {
        try {
            ShareProduct product = this.serializer.validateAndCreate(jsonCommand);
            this.repository.save(product);

            // save accounting mappings
            this.accountMappingWritePlatformService.createShareProductToGLAccountMapping(product.getId(), jsonCommand);

            return new CommandProcessingResultBuilder() //
                    .withCommandId(jsonCommand.commandId()) //
                    .withEntityId(product.getId()) //
                    .build();
        }catch (DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(jsonCommand, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        }catch (PersistenceException dve) {
        	Throwable throwable = ExceptionUtils.getRootCause(dve.getCause()) ;
        	handleDataIntegrityIssues(jsonCommand, throwable, dve);
        	return CommandProcessingResult.empty();
        }

    }

    @Override
    public CommandProcessingResult updateProduct(Long productId, JsonCommand jsonCommand) {
        try {
            ShareProduct product = this.repository.findOneWithNotFoundDetection(productId);
            final Map<String, Object> changes = this.serializer.validateAndUpdate(jsonCommand, product);

            // accounting related changes
            final boolean accountingTypeChanged = changes.containsKey(ShareProductApiConstants.accountingRuleParamName);
            final Map<String, Object> accountingMappingChanges = this.accountMappingWritePlatformService
                    .updateShareProductToGLAccountMapping(product.getId(), jsonCommand, accountingTypeChanged, product.getAccountingType());
            changes.putAll(accountingMappingChanges);

            if (!changes.isEmpty()) {
                this.repository.saveAndFlush(product);
            }
            return new CommandProcessingResultBuilder() //
                    .withCommandId(jsonCommand.commandId()) //
                    .withEntityId(productId) //
                    .with(changes) //
                    .build();
        }catch (DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(jsonCommand, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        }catch (PersistenceException dve) {
        	Throwable throwable = ExceptionUtils.getRootCause(dve.getCause()) ;
        	handleDataIntegrityIssues(jsonCommand, throwable, dve);
        	return CommandProcessingResult.empty();
        }
    }

    @Override
    public CommandProcessingResult createShareProductDividend(final Long productId, final JsonCommand jsonCommand) {
        try {
            this.serializer.validateDividendDetails(jsonCommand);
            JsonElement element = jsonCommand.parsedJson();
            final LocalDate dividendPeriodStartDate = this.fromApiJsonHelper.extractLocalDateNamed(
                    ShareProductApiConstants.dividendPeriodStartDateParamName, element);
            final LocalDate dividendPeriodEndDate = this.fromApiJsonHelper.extractLocalDateNamed(
                    ShareProductApiConstants.dividendPeriodEndDateParamName, element);
            final BigDecimal dividendAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(
                    ShareProductApiConstants.dividendAmountParamName, element);

            final ShareProductDividendPayOutDetails dividendPayOutDetails = this.shareProductDividendAssembler.calculateDividends(
                    productId, dividendAmount, dividendPeriodStartDate, dividendPeriodEndDate);
            if (dividendPayOutDetails == null) { throw new DividentProcessingException("eligible.shares.not.found",
                    "No eligible shares for creating dividends"); }
            this.shareProductDividentPayOutDetailsRepository.save(dividendPayOutDetails);

            return new CommandProcessingResultBuilder() //
                    .withCommandId(jsonCommand.commandId()) //
                    .withEntityId(productId) //
                    .withSubEntityId(dividendPayOutDetails.getId())//
                    .build();
        } catch (DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(dve);
            return CommandProcessingResult.empty();
        }
    }

    @Override
    public CommandProcessingResult approveShareProductDividend(final Long PayOutDetailId) {
        try {
            ShareProductDividendPayOutDetails dividendPayOutDetails = this.shareProductDividentPayOutDetailsRepository
                    .findOneWithNotFoundDetection(PayOutDetailId);
            if (dividendPayOutDetails.getStatus().isApproved()) { throw new DividentProcessingException("alreay.approved",
                    "Can't approve already appoved  dividends "); }
            dividendPayOutDetails.approveDividendPayout();
            this.shareProductDividentPayOutDetailsRepository.save(dividendPayOutDetails);
            return new CommandProcessingResultBuilder() //
                    .withEntityId(PayOutDetailId) //
                    .build();
        } catch (DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(dve);
            return CommandProcessingResult.empty();
        }
    }

    @Override
    public CommandProcessingResult deleteShareProductDividend(final Long PayOutDetailId) {
        try {
            ShareProductDividendPayOutDetails dividendPayOutDetails = this.shareProductDividentPayOutDetailsRepository
                    .findOneWithNotFoundDetection(PayOutDetailId);
            if (dividendPayOutDetails.getStatus().isApproved()) { throw new DividentProcessingException("alreay.approved",
                    "Can't delete already appoved  dividends "); }
            this.shareProductDividentPayOutDetailsRepository.delete(dividendPayOutDetails);
            return new CommandProcessingResultBuilder() //
                    .withEntityId(PayOutDetailId) //
                    .build();
        } catch (DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(dve);
            return CommandProcessingResult.empty();
        }
    }

    private void handleDataIntegrityIssues(final Exception e) {
        throw new PlatformDataIntegrityException("error.msg.shareproduct.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource.");
    }

    
    private void handleDataIntegrityIssues(final JsonCommand command, final Throwable realCause, final Exception dve) {

        if (realCause.getMessage().contains("'name'")) {
            final String name = command.stringValueOfParameterNamed(ShareProductApiConstants.name_paramname);
            throw new PlatformDataIntegrityException("error.msg.shareproduct.duplicate.name", "Share Product with name `" + name
                    + "` already exists", "name", name);
        } 

        throw new PlatformDataIntegrityException("error.msg.shareproduct.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource.");
    }

}
