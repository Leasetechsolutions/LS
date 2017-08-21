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

import static com.ls.portfolio.savings.SavingsApiConstants.allowOverdraftParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.withHoldTaxParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.chargesParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.currencyCodeParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.descriptionParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.digitsAfterDecimalParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.enforceMinRequiredBalanceParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.idParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.inMultiplesOfParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.interestCalculationDaysInYearTypeParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.interestCalculationTypeParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.interestCompoundingPeriodTypeParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.interestPostingPeriodTypeParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.lockinPeriodFrequencyParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.lockinPeriodFrequencyTypeParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.minBalanceForInterestCalculationParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.minOverdraftForInterestCalculationParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.minRequiredBalanceParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.minRequiredOpeningBalanceParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.nameParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.nominalAnnualInterestRateOverdraftParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.nominalAnnualInterestRateParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.overdraftLimitParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.shortNameParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.taxGroupIdParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.withdrawalFeeForTransfersParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.isDormancyTrackingActiveParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.daysToInactiveParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.daysToDormancyParamName;
import static com.ls.portfolio.savings.SavingsApiConstants.daysToEscheatParamName;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import com.ls.accounting.common.AccountingRuleType;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.organisation.monetary.domain.MonetaryCurrency;
import com.ls.portfolio.charge.domain.Charge;
import com.ls.portfolio.charge.domain.ChargeRepositoryWrapper;
import com.ls.portfolio.charge.exception.ChargeCannotBeAppliedToException;
import com.ls.portfolio.loanproduct.exception.InvalidCurrencyException;
import com.ls.portfolio.savings.SavingsCompoundingInterestPeriodType;
import com.ls.portfolio.savings.SavingsInterestCalculationDaysInYearType;
import com.ls.portfolio.savings.SavingsInterestCalculationType;
import com.ls.portfolio.savings.SavingsPeriodFrequencyType;
import com.ls.portfolio.savings.SavingsPostingInterestPeriodType;
import com.ls.portfolio.tax.domain.TaxGroup;
import com.ls.portfolio.tax.domain.TaxGroupRepositoryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@Component
public class SavingsProductAssembler {

    private final ChargeRepositoryWrapper chargeRepository;
    private final TaxGroupRepositoryWrapper taxGroupRepository;

    @Autowired
    public SavingsProductAssembler(final ChargeRepositoryWrapper chargeRepository, final TaxGroupRepositoryWrapper taxGroupRepository) {
        this.chargeRepository = chargeRepository;
        this.taxGroupRepository = taxGroupRepository;
    }

    public SavingsProduct assemble(final JsonCommand command) {

        final String name = command.stringValueOfParameterNamed(nameParamName);
        final String shortName = command.stringValueOfParameterNamed(shortNameParamName);
        final String description = command.stringValueOfParameterNamed(descriptionParamName);

        final String currencyCode = command.stringValueOfParameterNamed(currencyCodeParamName);
        final Integer digitsAfterDecimal = command.integerValueOfParameterNamed(digitsAfterDecimalParamName);
        final Integer inMultiplesOf = command.integerValueOfParameterNamed(inMultiplesOfParamName);
        final MonetaryCurrency currency = new MonetaryCurrency(currencyCode, digitsAfterDecimal, inMultiplesOf);

        final BigDecimal interestRate = command.bigDecimalValueOfParameterNamed(nominalAnnualInterestRateParamName);

        SavingsCompoundingInterestPeriodType interestCompoundingPeriodType = null;
        final Integer interestPeriodTypeValue = command.integerValueOfParameterNamed(interestCompoundingPeriodTypeParamName);
        if (interestPeriodTypeValue != null) {
            interestCompoundingPeriodType = SavingsCompoundingInterestPeriodType.fromInt(interestPeriodTypeValue);
        }

        SavingsPostingInterestPeriodType interestPostingPeriodType = null;
        final Integer interestPostingPeriodTypeValue = command.integerValueOfParameterNamed(interestPostingPeriodTypeParamName);
        if (interestPostingPeriodTypeValue != null) {
            interestPostingPeriodType = SavingsPostingInterestPeriodType.fromInt(interestPostingPeriodTypeValue);
        }

        SavingsInterestCalculationType interestCalculationType = null;
        final Integer interestCalculationTypeValue = command.integerValueOfParameterNamed(interestCalculationTypeParamName);
        if (interestCalculationTypeValue != null) {
            interestCalculationType = SavingsInterestCalculationType.fromInt(interestCalculationTypeValue);
        }

        SavingsInterestCalculationDaysInYearType interestCalculationDaysInYearType = null;
        final Integer interestCalculationDaysInYearTypeValue = command
                .integerValueOfParameterNamed(interestCalculationDaysInYearTypeParamName);
        if (interestCalculationDaysInYearTypeValue != null) {
            interestCalculationDaysInYearType = SavingsInterestCalculationDaysInYearType.fromInt(interestCalculationDaysInYearTypeValue);
        }

        final BigDecimal minRequiredOpeningBalance = command
                .bigDecimalValueOfParameterNamedDefaultToNullIfZero(minRequiredOpeningBalanceParamName);

        final Integer lockinPeriodFrequency = command.integerValueOfParameterNamedDefaultToNullIfZero(lockinPeriodFrequencyParamName);
        SavingsPeriodFrequencyType lockinPeriodFrequencyType = null;
        final Integer lockinPeriodFrequencyTypeValue = command.integerValueOfParameterNamed(lockinPeriodFrequencyTypeParamName);
        if (lockinPeriodFrequencyTypeValue != null) {
            lockinPeriodFrequencyType = SavingsPeriodFrequencyType.fromInt(lockinPeriodFrequencyTypeValue);
        }

        boolean iswithdrawalFeeApplicableForTransfer = false;
        if (command.parameterExists(withdrawalFeeForTransfersParamName)) {
            iswithdrawalFeeApplicableForTransfer = command.booleanPrimitiveValueOfParameterNamed(withdrawalFeeForTransfersParamName);
        }

        final AccountingRuleType accountingRuleType = AccountingRuleType.fromInt(command.integerValueOfParameterNamed("accountingRule"));

        // Savings product charges
        final Set<Charge> charges = assembleListOfSavingsProductCharges(command, currencyCode);

        boolean allowOverdraft = false;
        if (command.parameterExists(allowOverdraftParamName)) {
            allowOverdraft = command.booleanPrimitiveValueOfParameterNamed(allowOverdraftParamName);
        }

        BigDecimal overdraftLimit = BigDecimal.ZERO;
        if (command.parameterExists(overdraftLimitParamName)) {
            overdraftLimit = command.bigDecimalValueOfParameterNamed(overdraftLimitParamName);
        }

        BigDecimal nominalAnnualInterestRateOverdraft = BigDecimal.ZERO;
        if (command.parameterExists(nominalAnnualInterestRateOverdraftParamName)) {
            nominalAnnualInterestRateOverdraft = command.bigDecimalValueOfParameterNamed(nominalAnnualInterestRateOverdraftParamName);
        }

        BigDecimal minOverdraftForInterestCalculation = BigDecimal.ZERO;
        if (command.parameterExists(minOverdraftForInterestCalculationParamName)) {
            minOverdraftForInterestCalculation = command.bigDecimalValueOfParameterNamed(minOverdraftForInterestCalculationParamName);
        }

        boolean enforceMinRequiredBalance = false;
        if (command.parameterExists(enforceMinRequiredBalanceParamName)) {
            enforceMinRequiredBalance = command.booleanPrimitiveValueOfParameterNamed(enforceMinRequiredBalanceParamName);
        }

        BigDecimal minRequiredBalance = BigDecimal.ZERO;
        if (command.parameterExists(minRequiredBalanceParamName)) {
            minRequiredBalance = command.bigDecimalValueOfParameterNamed(minRequiredBalanceParamName);
        }
        final BigDecimal minBalanceForInterestCalculation = command
                .bigDecimalValueOfParameterNamedDefaultToNullIfZero(minBalanceForInterestCalculationParamName);

        boolean withHoldTax = command.booleanPrimitiveValueOfParameterNamed(withHoldTaxParamName);
        final TaxGroup taxGroup = assembleTaxGroup(command);
        
        final Boolean isDormancyTrackingActive = command.booleanObjectValueOfParameterNamed(isDormancyTrackingActiveParamName);
        final Long daysToInactive = command.longValueOfParameterNamed(daysToInactiveParamName);
        final Long daysToDormancy = command.longValueOfParameterNamed(daysToDormancyParamName);
        final Long daysToEscheat = command.longValueOfParameterNamed(daysToEscheatParamName);

        return SavingsProduct.createNew(name, shortName, description, currency, interestRate, interestCompoundingPeriodType,
                interestPostingPeriodType, interestCalculationType, interestCalculationDaysInYearType, minRequiredOpeningBalance,
                lockinPeriodFrequency, lockinPeriodFrequencyType, iswithdrawalFeeApplicableForTransfer, accountingRuleType, charges,
                allowOverdraft, overdraftLimit, enforceMinRequiredBalance, minRequiredBalance, minBalanceForInterestCalculation,
                nominalAnnualInterestRateOverdraft, minOverdraftForInterestCalculation, withHoldTax, taxGroup,
                isDormancyTrackingActive, daysToInactive, daysToDormancy, daysToEscheat);
    }

    public Set<Charge> assembleListOfSavingsProductCharges(final JsonCommand command, final String savingsProductCurrencyCode) {

        final Set<Charge> charges = new HashSet<>();

        if (command.parameterExists(chargesParamName)) {
            final JsonArray chargesArray = command.arrayOfParameterNamed(chargesParamName);
            if (chargesArray != null) {
                for (int i = 0; i < chargesArray.size(); i++) {

                    final JsonObject jsonObject = chargesArray.get(i).getAsJsonObject();
                    if (jsonObject.has(idParamName)) {
                        final Long id = jsonObject.get(idParamName).getAsLong();

                        final Charge charge = this.chargeRepository.findOneWithNotFoundDetection(id);

                        if (!charge.isSavingsCharge()) {
                            final String errorMessage = "Charge with identifier " + charge.getId()
                                    + " cannot be applied to Savings product.";
                            throw new ChargeCannotBeAppliedToException("savings.product", errorMessage, charge.getId());
                        }

                        if (!savingsProductCurrencyCode.equals(charge.getCurrencyCode())) {
                            final String errorMessage = "Charge and Savings Product must have the same currency.";
                            throw new InvalidCurrencyException("charge", "attach.to.savings.product", errorMessage);
                        }
                        charges.add(charge);
                    }
                }
            }
        }

        return charges;
    }

    public TaxGroup assembleTaxGroup(final JsonCommand command) {
        final Long taxGroupId = command.longValueOfParameterNamed(taxGroupIdParamName);
        TaxGroup taxGroup = null;
        if (taxGroupId != null) {
            taxGroup = this.taxGroupRepository.findOneWithNotFoundDetection(taxGroupId);
        }
        return taxGroup;
    }
}