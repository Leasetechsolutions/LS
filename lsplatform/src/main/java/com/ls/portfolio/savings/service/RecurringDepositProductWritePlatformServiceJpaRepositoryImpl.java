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
package com.ls.portfolio.savings.service;

import static com.ls.portfolio.savings.DepositsApiConstants.RECURRING_DEPOSIT_PRODUCT_RESOURCE_NAME;
import static com.ls.portfolio.savings.SavingsApiConstants.accountingRuleParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.chargesParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.taxGroupIdParamName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.PersistenceException;

import org.apache.commons.lang.exception.ExceptionUtils;
import com.ls.accounting.producttoaccountmapping.service.ProductToGLAccountMappingWritePlatformService;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.ApiParameterError;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.infrastructure.core.data.CommandProcessingResultBuilder;
import com.ls.infrastructure.core.data.DataValidatorBuilder;
import com.ls.infrastructure.core.exception.PlatformApiDataValidationException;
import com.ls.infrastructure.core.exception.PlatformDataIntegrityException;
import com.ls.infrastructure.security.service.PlatformSecurityContext;
import com.ls.portfolio.charge.domain.Charge;
import com.ls.portfolio.interestratechart.service.InterestRateChartAssembler;
import com.ls.portfolio.savings.DepositAccountType;
import com.ls.portfolio.savings.data.DepositProductDataValidator;
import com.ls.portfolio.savings.domain.DepositProductAssembler;
import com.ls.portfolio.savings.domain.RecurringDepositProduct;
import com.ls.portfolio.savings.domain.RecurringDepositProductRepository;
import com.ls.portfolio.savings.exception.RecurringDepositProductNotFoundException;
import com.ls.portfolio.tax.domain.TaxGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringDepositProductWritePlatformServiceJpaRepositoryImpl implements RecurringDepositProductWritePlatformService {

    private final Logger logger;
    private final PlatformSecurityContext context;
    private final RecurringDepositProductRepository recurringDepositProductRepository;
    private final DepositProductDataValidator fromApiJsonDataValidator;
    private final DepositProductAssembler depositProductAssembler;
    private final ProductToGLAccountMappingWritePlatformService accountMappingWritePlatformService;
    private final InterestRateChartAssembler chartAssembler;

    @Autowired
    public RecurringDepositProductWritePlatformServiceJpaRepositoryImpl(final PlatformSecurityContext context,
            final RecurringDepositProductRepository recurringDepositProductRepository,
            final DepositProductDataValidator fromApiJsonDataValidator, final DepositProductAssembler depositProductAssembler,
            final ProductToGLAccountMappingWritePlatformService accountMappingWritePlatformService,
            final InterestRateChartAssembler chartAssembler) {
        this.context = context;
        this.recurringDepositProductRepository = recurringDepositProductRepository;
        this.fromApiJsonDataValidator = fromApiJsonDataValidator;
        this.depositProductAssembler = depositProductAssembler;
        this.logger = LoggerFactory.getLogger(RecurringDepositProductWritePlatformServiceJpaRepositoryImpl.class);
        this.accountMappingWritePlatformService = accountMappingWritePlatformService;
        this.chartAssembler = chartAssembler;
    }

    @Transactional
    @Override
    public CommandProcessingResult create(final JsonCommand command) {

        try {
            this.fromApiJsonDataValidator.validateForRecurringDepositCreate(command.json());

            final RecurringDepositProduct product = this.depositProductAssembler.assembleRecurringDepositProduct(command);

            this.recurringDepositProductRepository.save(product);

            // save accounting mappings
            this.accountMappingWritePlatformService.createSavingProductToGLAccountMapping(product.getId(), command,
                    DepositAccountType.RECURRING_DEPOSIT);

            return new CommandProcessingResultBuilder() //
                    .withEntityId(product.getId()) //
                    .build();
        } catch (final DataAccessException e) {
            handleDataIntegrityIssues(command, e.getMostSpecificCause(), e);
            return CommandProcessingResult.empty();
        }catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause()) ;
        	handleDataIntegrityIssues(command, throwable, dve);
        	return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult update(final Long productId, final JsonCommand command) {

        try {
            this.context.authenticatedUser();
            this.fromApiJsonDataValidator.validateForRecurringDepositUpdate(command.json());

            final RecurringDepositProduct product = this.recurringDepositProductRepository.findOne(productId);
            if (product == null) { throw new RecurringDepositProductNotFoundException(productId); }
            product.setHelpers(this.chartAssembler);

            final Map<String, Object> changes = product.update(command);

            if (changes.containsKey(chargesParamName)) {
                final Set<Charge> savingsProductCharges = this.depositProductAssembler.assembleListOfSavingsProductCharges(command, product
                        .currency().getCode());
                final boolean updated = product.update(savingsProductCharges);
                if (!updated) {
                    changes.remove(chargesParamName);
                }
            }

            if (changes.containsKey(taxGroupIdParamName)) {
                final TaxGroup taxGroup = this.depositProductAssembler.assembleTaxGroup(command);
                product.setTaxGroup(taxGroup);
                if (product.withHoldTax() && product.getTaxGroup() == null) {
                    final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
                    final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                            .resource(RECURRING_DEPOSIT_PRODUCT_RESOURCE_NAME);
                    final Long taxGroupId = null;
                    baseDataValidator.reset().parameter(taxGroupIdParamName).value(taxGroupId).notBlank();
                    throw new PlatformApiDataValidationException(dataValidationErrors);
                }
            }

            // accounting related changes
            final boolean accountingTypeChanged = changes.containsKey(accountingRuleParamName);
            final Map<String, Object> accountingMappingChanges = this.accountMappingWritePlatformService
                    .updateSavingsProductToGLAccountMapping(product.getId(), command, accountingTypeChanged, product.getAccountingType(),
                            DepositAccountType.RECURRING_DEPOSIT);
            changes.putAll(accountingMappingChanges);

            if (!changes.isEmpty()) {
                this.recurringDepositProductRepository.save(product);
            }

            return new CommandProcessingResultBuilder() //
                    .withEntityId(product.getId()) //
                    .with(changes).build();
        } catch (final DataAccessException e) {
            handleDataIntegrityIssues(command, e.getMostSpecificCause(), e);
            return CommandProcessingResult.empty();
        }catch (final PersistenceException dve) {
        	Throwable throwable = ExceptionUtils.getRootCause(dve.getCause()) ;
        	handleDataIntegrityIssues(command, throwable, dve);
        	return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult delete(final Long productId) {

        this.context.authenticatedUser();
        final RecurringDepositProduct product = this.recurringDepositProductRepository.findOne(productId);
        if (product == null) { throw new RecurringDepositProductNotFoundException(productId); }

        this.recurringDepositProductRepository.delete(product);

        return new CommandProcessingResultBuilder() //
                .withEntityId(product.getId()) //
                .build();
    }

    /*
     * Guaranteed to throw an exception no matter what the data integrity issue
     * is.
     */
    private void handleDataIntegrityIssues(final JsonCommand command, final Throwable realCause, final Exception dae) {

        if (realCause.getMessage().contains("sp_unq_name")) {

            final String name = command.stringValueOfParameterNamed("name");
            throw new PlatformDataIntegrityException("error.msg.product.savings.duplicate.name", "Recurring Deposit product with name `"
                    + name + "` already exists", "name", name);
        } else if (realCause.getMessage().contains("sp_unq_short_name")) {

            final String shortName = command.stringValueOfParameterNamed("shortName");
            throw new PlatformDataIntegrityException("error.msg.product.savings.duplicate.short.name",
                    "Recurring Deposit product with short name `" + shortName + "` already exists", "shortName", shortName);
        }

        logAsErrorUnexpectedDataIntegrityException(dae);
        throw new PlatformDataIntegrityException("error.msg.savingsproduct.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource.");
    }

    private void logAsErrorUnexpectedDataIntegrityException(final Exception dae) {
        this.logger.error(dae.getMessage(), dae);
    }
}