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
package com.ls.portfolio.savings.domain;

import static com.ls.portfolio.savings.DepositsApiConstants.RECURRING_DEPOSIT_PRODUCT_RESOURCE_NAME;
import static com.ls.portfolio.savings.DepositsApiConstants.maxDepositTermParamName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

import com.ls.accounting.common.AccountingRuleType;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.ApiParameterError;
import com.ls.infrastructure.core.data.DataValidatorBuilder;
import com.ls.infrastructure.core.exception.PlatformApiDataValidationException;
import com.ls.organisation.monetary.domain.MonetaryCurrency;
import com.ls.portfolio.charge.domain.Charge;
import com.ls.portfolio.interestratechart.domain.InterestRateChart;
import com.ls.portfolio.savings.DepositsApiConstants;
import com.ls.portfolio.savings.SavingsCompoundingInterestPeriodType;
import com.ls.portfolio.savings.SavingsInterestCalculationDaysInYearType;
import com.ls.portfolio.savings.SavingsInterestCalculationType;
import com.ls.portfolio.savings.SavingsPeriodFrequencyType;
import com.ls.portfolio.savings.SavingsPostingInterestPeriodType;
import com.ls.portfolio.tax.domain.TaxGroup;

@Entity
@DiscriminatorValue("300")
public class RecurringDepositProduct extends FixedDepositProduct {

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL)
    private DepositProductRecurringDetail recurringDetail;

    protected RecurringDepositProduct() {
        super();
    }

    public static RecurringDepositProduct createNew(final String name, final String shortName, final String description,
            final MonetaryCurrency currency, final BigDecimal interestRate,
            final SavingsCompoundingInterestPeriodType interestCompoundingPeriodType,
            final SavingsPostingInterestPeriodType interestPostingPeriodType, final SavingsInterestCalculationType interestCalculationType,
            final SavingsInterestCalculationDaysInYearType interestCalculationDaysInYearType, final Integer lockinPeriodFrequency,
            final SavingsPeriodFrequencyType lockinPeriodFrequencyType, final AccountingRuleType accountingRuleType,
            final Set<Charge> charges, final DepositProductTermAndPreClosure productTermAndPreClosure,
            final DepositProductRecurringDetail recurringDetail, final Set<InterestRateChart> charts,
            BigDecimal minBalanceForInterestCalculation, final TaxGroup taxGroup, final boolean withHoldTax) {

        final BigDecimal minRequiredOpeningBalance = null;
        final boolean withdrawalFeeApplicableForTransfer = false;
        final boolean allowOverdraft = false;
        final BigDecimal overdraftLimit = null;

        return new RecurringDepositProduct(name, shortName, description, currency, interestRate, interestCompoundingPeriodType,
                interestPostingPeriodType, interestCalculationType, interestCalculationDaysInYearType, minRequiredOpeningBalance,
                lockinPeriodFrequency, lockinPeriodFrequencyType, withdrawalFeeApplicableForTransfer, accountingRuleType, charges,
                productTermAndPreClosure, recurringDetail, charts, allowOverdraft, overdraftLimit, minBalanceForInterestCalculation,
                withHoldTax, taxGroup);
    }

    protected RecurringDepositProduct(final String name, final String shortName, final String description, final MonetaryCurrency currency,
            final BigDecimal interestRate, final SavingsCompoundingInterestPeriodType interestCompoundingPeriodType,
            final SavingsPostingInterestPeriodType interestPostingPeriodType, final SavingsInterestCalculationType interestCalculationType,
            final SavingsInterestCalculationDaysInYearType interestCalculationDaysInYearType, final BigDecimal minRequiredOpeningBalance,
            final Integer lockinPeriodFrequency, final SavingsPeriodFrequencyType lockinPeriodFrequencyType,
            final boolean withdrawalFeeApplicableForTransfer, final AccountingRuleType accountingRuleType, final Set<Charge> charges,
            final DepositProductTermAndPreClosure productTermAndPreClosure, final DepositProductRecurringDetail recurringDetail,
            final Set<InterestRateChart> charts, final boolean allowOverdraft, final BigDecimal overdraftLimit,
            final BigDecimal minBalanceForInterestCalculation, final boolean withHoldTax, final TaxGroup taxGroup) {

        super(name, shortName, description, currency, interestRate, interestCompoundingPeriodType, interestPostingPeriodType,
                interestCalculationType, interestCalculationDaysInYearType, minRequiredOpeningBalance, lockinPeriodFrequency,
                lockinPeriodFrequencyType, withdrawalFeeApplicableForTransfer, accountingRuleType, charges, productTermAndPreClosure,
                charts, allowOverdraft, overdraftLimit, minBalanceForInterestCalculation, withHoldTax, taxGroup);

        this.recurringDetail = recurringDetail;
    }

    @Override
    public Map<String, Object> update(final JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>(10);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(RECURRING_DEPOSIT_PRODUCT_RESOURCE_NAME);

        actualChanges.putAll(this.update(command, baseDataValidator));

        validateDomainRules(baseDataValidator);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);

        return actualChanges;
    }

    @Override
    protected Map<String, Object> update(final JsonCommand command, final DataValidatorBuilder baseDataValidator) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>(10);

        actualChanges.putAll(super.update(command, baseDataValidator));

        if (this.recurringDetail != null) {
            actualChanges.putAll(this.recurringDetail.update(command));
        }

        return actualChanges;
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
    }

    public DepositProductRecurringDetail depositRecurringDetail() {
        return this.recurringDetail;
    }

    @Override
    public void validateDomainRules() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(RECURRING_DEPOSIT_PRODUCT_RESOURCE_NAME);

        validateDomainRules(baseDataValidator);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void validateDomainRules(final DataValidatorBuilder baseDataValidator) {
        final DepositTermDetail termDetails = this.depositProductTermAndPreClosure().depositTermDetail();
        final boolean isMinTermGreaterThanMax = termDetails.isMinDepositTermGreaterThanMaxDepositTerm();
        if (isMinTermGreaterThanMax) {
            final Integer maxTerm = termDetails.maxDepositTerm();
            baseDataValidator.reset().parameter(maxDepositTermParamName).value(maxTerm)
                    .failWithCodeNoParameterAddedToErrorCode("max.term.lessthan.min.term");
        }

        if (this.charts != null) {
            validateCharts(baseDataValidator);
        } else if (this.nominalAnnualInterestRate == null || this.nominalAnnualInterestRate.compareTo(BigDecimal.ZERO) == 0) {
            baseDataValidator.reset().parameter(DepositsApiConstants.nominalAnnualInterestRateParamName).value(nominalAnnualInterestRate)
                    .failWithCodeNoParameterAddedToErrorCode("interest.chart.or.nominal.interest.rate.required");
        }

        this.validateInterestPostingAndCompoundingPeriodTypes(baseDataValidator);
    }
}