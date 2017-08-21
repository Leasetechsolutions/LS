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

import static com.ls.portfolio.savings.DepositsApiConstants.closedOnDateParamName;

import java.util.Collection;

import com.ls.infrastructure.configuration.domain.ConfigurationDomainService;
import com.ls.infrastructure.core.api.JsonQuery;
import com.ls.infrastructure.core.data.EnumOptionData;
import com.ls.infrastructure.core.serialization.FromJsonHelper;
import com.ls.portfolio.paymenttype.data.PaymentTypeData;
import com.ls.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import com.ls.portfolio.savings.DepositAccountOnClosureType;
import com.ls.portfolio.savings.DepositAccountType;
import com.ls.portfolio.savings.data.DepositAccountData;
import com.ls.portfolio.savings.data.DepositAccountTransactionDataValidator;
import com.ls.portfolio.savings.data.FixedDepositAccountData;
import com.ls.portfolio.savings.data.RecurringDepositAccountData;
import com.ls.portfolio.savings.data.SavingsAccountData;
import com.ls.portfolio.savings.domain.DepositAccountAssembler;
import com.ls.portfolio.savings.domain.FixedDepositAccount;
import com.ls.portfolio.savings.domain.RecurringDepositAccount;
import com.ls.portfolio.savings.domain.SavingsAccount;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.JsonElement;

@Service
public class DepositAccountPreMatureCalculationPlatformServiceImpl implements DepositAccountPreMatureCalculationPlatformService {

    private final FromJsonHelper fromJsonHelper;
    private final DepositAccountTransactionDataValidator depositAccountTransactionDataValidator;
    private final DepositAccountAssembler depositAccountAssembler;
    private final SavingsAccountReadPlatformService savingsAccountReadPlatformService;
    private final ConfigurationDomainService configurationDomainService;
    private final PaymentTypeReadPlatformService paymentTypeReadPlatformService;

    @Autowired
    public DepositAccountPreMatureCalculationPlatformServiceImpl(final FromJsonHelper fromJsonHelper,
            final DepositAccountTransactionDataValidator depositAccountTransactionDataValidator,
            final DepositAccountAssembler depositAccountAssembler,
            final SavingsAccountReadPlatformService savingsAccountReadPlatformService,
            final ConfigurationDomainService configurationDomainService, PaymentTypeReadPlatformService paymentTypeReadPlatformService) {
        this.fromJsonHelper = fromJsonHelper;
        this.depositAccountTransactionDataValidator = depositAccountTransactionDataValidator;
        this.depositAccountAssembler = depositAccountAssembler;
        this.savingsAccountReadPlatformService = savingsAccountReadPlatformService;
        this.configurationDomainService = configurationDomainService;
        this.paymentTypeReadPlatformService = paymentTypeReadPlatformService;

    }

    @Transactional
    @Override
    public DepositAccountData calculatePreMatureAmount(final Long accountId, final JsonQuery query,
            final DepositAccountType depositAccountType) {

        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();

        this.depositAccountTransactionDataValidator.validatePreMatureAmountCalculation(query.json(), depositAccountType);
        final SavingsAccount account = this.depositAccountAssembler.assembleFrom(accountId, depositAccountType);

        DepositAccountData accountData = null;
        Collection<EnumOptionData> onAccountClosureOptions = SavingsEnumerations
                .depositAccountOnClosureType(new DepositAccountOnClosureType[] { DepositAccountOnClosureType.WITHDRAW_DEPOSIT,
                        DepositAccountOnClosureType.TRANSFER_TO_SAVINGS });
        final Collection<PaymentTypeData> paymentTypeOptions = this.paymentTypeReadPlatformService.retrieveAllPaymentTypes();
        final Collection<SavingsAccountData> savingsAccountDatas = this.savingsAccountReadPlatformService.retrieveActiveForLookup(
                account.clientId(), DepositAccountType.SAVINGS_DEPOSIT);
        final JsonElement element = this.fromJsonHelper.parse(query.json());
        final LocalDate preMaturityDate = this.fromJsonHelper.extractLocalDateNamed(closedOnDateParamName, element);
        // calculate interest before one day of closure date
        final LocalDate interestCalculatedToDate = preMaturityDate.minusDays(1);
        final boolean isPreMatureClosure = true;

        if (depositAccountType.isFixedDeposit()) {
            final FixedDepositAccount fd = (FixedDepositAccount) account;
            accountData = FixedDepositAccountData.preClosureDetails(account.getId(), fd.calculatePreMatureAmount(interestCalculatedToDate,
                    isPreMatureClosure, isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth), onAccountClosureOptions,
                    paymentTypeOptions, savingsAccountDatas);
        } else if (depositAccountType.isRecurringDeposit()) {
            final RecurringDepositAccount rd = (RecurringDepositAccount) account;
            accountData = RecurringDepositAccountData.preClosureDetails(account.getId(), rd.calculatePreMatureAmount(
                    interestCalculatedToDate, isPreMatureClosure, isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth),
                    onAccountClosureOptions, paymentTypeOptions, savingsAccountDatas);
        }

        return accountData;
    }
}