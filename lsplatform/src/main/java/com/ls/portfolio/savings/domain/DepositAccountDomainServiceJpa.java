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

import static com.ls.portfolio.savings.DepositsApiConstants.onAccountClosureIdParamName;
import static com.ls.portfolio.savings.DepositsApiConstants.toSavingsAccountIdParamName;
import static com.ls.portfolio.savings.DepositsApiConstants.transferDescriptionParamName;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.ls.accounting.journalentry.service.JournalEntryWritePlatformService;
import com.ls.infrastructure.accountnumberformat.domain.AccountNumberFormat;
import com.ls.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import com.ls.infrastructure.accountnumberformat.domain.EntityAccountType;
import com.ls.infrastructure.configuration.domain.ConfigurationDomainService;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import com.ls.infrastructure.core.service.DateUtils;
import com.ls.organisation.monetary.domain.ApplicationCurrency;
import com.ls.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import com.ls.organisation.monetary.domain.MonetaryCurrency;
import com.ls.portfolio.account.PortfolioAccountType;
import com.ls.portfolio.account.data.AccountTransferDTO;
import com.ls.portfolio.account.domain.AccountTransferType;
import com.ls.portfolio.account.service.AccountTransfersWritePlatformService;
import com.ls.portfolio.calendar.domain.Calendar;
import com.ls.portfolio.calendar.domain.CalendarEntityType;
import com.ls.portfolio.calendar.domain.CalendarFrequencyType;
import com.ls.portfolio.calendar.domain.CalendarInstance;
import com.ls.portfolio.calendar.domain.CalendarInstanceRepository;
import com.ls.portfolio.calendar.domain.CalendarType;
import com.ls.portfolio.calendar.service.CalendarUtils;
import com.ls.portfolio.client.domain.AccountNumberGenerator;
import com.ls.portfolio.common.domain.PeriodFrequencyType;
import com.ls.portfolio.paymentdetail.domain.PaymentDetail;
import com.ls.portfolio.savings.DepositAccountOnClosureType;
import com.ls.portfolio.savings.DepositAccountType;
import com.ls.portfolio.savings.DepositsApiConstants;
import com.ls.portfolio.savings.SavingsApiConstants;
import com.ls.portfolio.savings.SavingsTransactionBooleanValues;
import com.ls.useradministration.domain.AppUser;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositAccountDomainServiceJpa implements DepositAccountDomainService {

    private final SavingsAccountRepositoryWrapper savingsAccountRepository;
    private final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepositoryWrapper;
    private final JournalEntryWritePlatformService journalEntryWritePlatformService;
    private final AccountNumberGenerator accountNumberGenerator;
    private final DepositAccountAssembler depositAccountAssembler;
    private final SavingsAccountDomainService savingsAccountDomainService;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private final ConfigurationDomainService configurationDomainService;
    private final AccountNumberFormatRepositoryWrapper accountNumberFormatRepository;
    private final CalendarInstanceRepository calendarInstanceRepository;

    @Autowired
    public DepositAccountDomainServiceJpa(final SavingsAccountRepositoryWrapper savingsAccountRepository,
            final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepositoryWrapper,
            final JournalEntryWritePlatformService journalEntryWritePlatformService, final AccountNumberGenerator accountNumberGenerator,
            final DepositAccountAssembler depositAccountAssembler, final SavingsAccountDomainService savingsAccountDomainService,
            final AccountTransfersWritePlatformService accountTransfersWritePlatformService,
            final ConfigurationDomainService configurationDomainService,
            final AccountNumberFormatRepositoryWrapper accountNumberFormatRepository,
            final CalendarInstanceRepository calendarInstanceRepository) {
        this.savingsAccountRepository = savingsAccountRepository;
        this.applicationCurrencyRepositoryWrapper = applicationCurrencyRepositoryWrapper;
        this.journalEntryWritePlatformService = journalEntryWritePlatformService;
        this.accountNumberGenerator = accountNumberGenerator;
        this.depositAccountAssembler = depositAccountAssembler;
        this.savingsAccountDomainService = savingsAccountDomainService;
        this.accountTransfersWritePlatformService = accountTransfersWritePlatformService;
        this.configurationDomainService = configurationDomainService;
        this.accountNumberFormatRepository = accountNumberFormatRepository;
        this.calendarInstanceRepository = calendarInstanceRepository;
    }

    @Transactional
    @Override
    public SavingsAccountTransaction handleWithdrawal(final SavingsAccount account, final DateTimeFormatter fmt,
            final LocalDate transactionDate, final BigDecimal transactionAmount, final PaymentDetail paymentDetail,
            final boolean applyWithdrawFee, final boolean isRegularTransaction) {
        boolean isAccountTransfer = false;
        boolean isInterestTransfer = false;
        boolean isWithdrawBalance = false;

        SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(isAccountTransfer,
                isRegularTransaction, applyWithdrawFee, isInterestTransfer, isWithdrawBalance);
        return this.savingsAccountDomainService.handleWithdrawal(account, fmt, transactionDate, transactionAmount, paymentDetail,
                transactionBooleanValues);
    }

    @Transactional
    @Override
    public SavingsAccountTransaction handleFDDeposit(final FixedDepositAccount account, final DateTimeFormatter fmt,
            final LocalDate transactionDate, final BigDecimal transactionAmount, final PaymentDetail paymentDetail) {
        boolean isAccountTransfer = false;
        boolean isRegularTransaction = false;
        return this.savingsAccountDomainService.handleDeposit(account, fmt, transactionDate, transactionAmount, paymentDetail,
                isAccountTransfer, isRegularTransaction);
    }

    @Transactional
    @Override
    public SavingsAccountTransaction handleRDDeposit(final RecurringDepositAccount account, final DateTimeFormatter fmt,
            final LocalDate transactionDate, final BigDecimal transactionAmount, final PaymentDetail paymentDetail,
            final boolean isRegularTransaction) {

        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();

        boolean isAccountTransfer = false;
        final boolean isPreMatureClosure = false;
        final MathContext mc = MathContext.DECIMAL64;
        account.updateDepositAmount(transactionAmount);
        final SavingsAccountTransaction deposit = this.savingsAccountDomainService.handleDeposit(account, fmt, transactionDate,
                transactionAmount, paymentDetail, isAccountTransfer, isRegularTransaction);

        account.handleScheduleInstallments(deposit);
        account.updateMaturityDateAndAmount(mc, isPreMatureClosure, isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth);
        account.updateOverduePayments(DateUtils.getLocalDateOfTenant());
        return deposit;
    }

    @Transactional
    @Override
    public Long handleFDAccountClosure(final FixedDepositAccount account, final PaymentDetail paymentDetail, final AppUser user,
            final JsonCommand command, final LocalDate tenantsTodayDate, final Map<String, Object> changes) {

        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();

        boolean isRegularTransaction = false;
        boolean isAccountTransfer = false;
        final boolean isPreMatureClosure = false;
        final Set<Long> existingTransactionIds = new HashSet<>();
        final Set<Long> existingReversedTransactionIds = new HashSet<>();
        /***
         * Update account transactionIds for post journal entries.
         */
        updateExistingTransactionsDetails(account, existingTransactionIds, existingReversedTransactionIds);
        /*
         * <<<<<<< HEAD final SavingsAccountTransactionDTO transactionDTO = new
         * SavingsAccountTransactionDTO(fmt, transactionDate, transactionAmount,
         * paymentDetail, new Date()); final SavingsAccountTransaction deposit =
         * account.deposit(transactionDTO); boolean isInterestTransfer = false;
         * final MathContext mc = MathContext.DECIMAL64; if
         * (account.isBeforeLastPostingPeriod(transactionDate)) { final
         * LocalDate today = DateUtils.getLocalDateOfTenant();
         * account.postInterest(mc, today, isInterestTransfer); } else { final
         * LocalDate today = DateUtils.getLocalDateOfTenant();
         * account.calculateInterestUsing(mc, today, isInterestTransfer);
         * =======
         */
        final MathContext mc = MathContext.DECIMAL64;
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormat.forPattern(command.dateFormat()).withLocale(locale);
        final LocalDate closedDate = command.localDateValueOfParameterNamed(SavingsApiConstants.closedOnDateParamName);
        Long savingsTransactionId = null;
        account.postMaturityInterest(isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth);
        final Integer onAccountClosureId = command.integerValueOfParameterNamed(onAccountClosureIdParamName);
        final DepositAccountOnClosureType onClosureType = DepositAccountOnClosureType.fromInt(onAccountClosureId);
        if (onClosureType.isReinvest()) {
            FixedDepositAccount reinvestedDeposit = account.reInvest(account.getAccountBalance());
            this.depositAccountAssembler.assignSavingAccountHelpers(reinvestedDeposit);
            reinvestedDeposit.updateMaturityDateAndAmountBeforeAccountActivation(mc, isPreMatureClosure,
                    isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth);
            this.savingsAccountRepository.save(reinvestedDeposit);
            autoGenerateAccountNumber(reinvestedDeposit);
            final SavingsAccountTransaction withdrawal = this.handleWithdrawal(account, fmt, closedDate, account.getAccountBalance(),
                    paymentDetail, false, isRegularTransaction);
            savingsTransactionId = withdrawal.getId();
        } else if (onClosureType.isTransferToSavings()) {
            final Long toSavingsId = command.longValueOfParameterNamed(toSavingsAccountIdParamName);
            final String transferDescription = command.stringValueOfParameterNamed(transferDescriptionParamName);
            final SavingsAccount toSavingsAccount = this.depositAccountAssembler.assembleFrom(toSavingsId,
                    DepositAccountType.SAVINGS_DEPOSIT);
            final boolean isExceptionForBalanceCheck = false;
            final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(closedDate, account.getAccountBalance(),
                    PortfolioAccountType.SAVINGS, PortfolioAccountType.SAVINGS, null, null, transferDescription, locale, fmt, null, null,
                    null, null, null, AccountTransferType.ACCOUNT_TRANSFER.getValue(), null, null, null, null, toSavingsAccount, account,
                    isAccountTransfer, isExceptionForBalanceCheck);
            this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
            updateAlreadyPostedTransactions(existingTransactionIds, account);  
        } else {
            final SavingsAccountTransaction withdrawal = this.handleWithdrawal(account, fmt, closedDate, account.getAccountBalance(),
                    paymentDetail, false, isRegularTransaction);
            savingsTransactionId = withdrawal.getId();
        }

        account.close(user, command, tenantsTodayDate, changes);
        this.savingsAccountRepository.save(account);

        postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, isAccountTransfer);

        return savingsTransactionId;
    }

    @Transactional
    @Override
    public Long handleRDAccountClosure(final RecurringDepositAccount account, final PaymentDetail paymentDetail, final AppUser user,
            final JsonCommand command, final LocalDate tenantsTodayDate, final Map<String, Object> changes) {

        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();

        boolean isRegularTransaction = false;
        boolean isAccountTransfer = false;
        final boolean isPreMatureClosure = false;
        final Set<Long> existingTransactionIds = new HashSet<>();
        final Set<Long> existingReversedTransactionIds = new HashSet<>();
        /***
         * Update account transactionIds for post journal entries.
         */
        updateExistingTransactionsDetails(account, existingTransactionIds, existingReversedTransactionIds);

        final MathContext mc = MathContext.DECIMAL64;
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormat.forPattern(command.dateFormat()).withLocale(locale);
        final LocalDate closedDate = command.localDateValueOfParameterNamed(SavingsApiConstants.closedOnDateParamName);
        Long savingsTransactionId = null;
        account.postMaturityInterest(isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth, closedDate);
        final BigDecimal transactionAmount = account.getAccountBalance();
        final Integer onAccountClosureId = command.integerValueOfParameterNamed(onAccountClosureIdParamName);
        final DepositAccountOnClosureType onClosureType = DepositAccountOnClosureType.fromInt(onAccountClosureId);
        if (onClosureType.isReinvest()) {
            RecurringDepositAccount reinvestedDeposit = account.reInvest(transactionAmount);
            depositAccountAssembler.assignSavingAccountHelpers(reinvestedDeposit);
            this.savingsAccountRepository.save(reinvestedDeposit);
            final CalendarInstance calendarInstance = getCalendarInstance(account, reinvestedDeposit);
            this.calendarInstanceRepository.save(calendarInstance);
            final Calendar calendar = calendarInstance.getCalendar();
            final PeriodFrequencyType frequencyType = CalendarFrequencyType.from(CalendarUtils.getFrequency(calendar.getRecurrence()));
            Integer frequency = CalendarUtils.getInterval(calendar.getRecurrence());
            frequency = frequency == -1 ? 1 : frequency;
            reinvestedDeposit.generateSchedule(frequencyType, frequency, calendar);
            reinvestedDeposit.processAccountUponActivation(fmt, user);
            reinvestedDeposit.updateMaturityDateAndAmount(mc, isPreMatureClosure, isSavingsInterestPostingAtCurrentPeriodEnd,
                    financialYearBeginningMonth);
            this.savingsAccountRepository.save(reinvestedDeposit);
            autoGenerateAccountNumber(reinvestedDeposit);

            final SavingsAccountTransaction withdrawal = this.handleWithdrawal(account, fmt, closedDate, account.getAccountBalance(),
                    paymentDetail, false, isRegularTransaction);
            savingsTransactionId = withdrawal.getId();

        } else if (onClosureType.isTransferToSavings()) {
            final Long toSavingsId = command.longValueOfParameterNamed(toSavingsAccountIdParamName);
            final String transferDescription = command.stringValueOfParameterNamed(transferDescriptionParamName);
            final SavingsAccount toSavingsAccount = this.depositAccountAssembler.assembleFrom(toSavingsId,
                    DepositAccountType.SAVINGS_DEPOSIT);
            final boolean isExceptionForBalanceCheck = false;
            final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(closedDate, transactionAmount,
                    PortfolioAccountType.SAVINGS, PortfolioAccountType.SAVINGS, null, null, transferDescription, locale, fmt, null, null,
                    null, null, null, AccountTransferType.ACCOUNT_TRANSFER.getValue(), null, null, null, null, toSavingsAccount, account,
                    isRegularTransaction, isExceptionForBalanceCheck);
            this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
            updateAlreadyPostedTransactions(existingTransactionIds, account);  
        } else {
            final SavingsAccountTransaction withdrawal = this.handleWithdrawal(account, fmt, closedDate, account.getAccountBalance(),
                    paymentDetail, false, isRegularTransaction);
            savingsTransactionId = withdrawal.getId();
        }

        account.close(user, command, tenantsTodayDate, changes);

        this.savingsAccountRepository.save(account);

        postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, isAccountTransfer);

        return savingsTransactionId;
    }

    private CalendarInstance getCalendarInstance(final RecurringDepositAccount account, RecurringDepositAccount reinvestedDeposit) {
        CalendarInstance calendarInstance = null;
        CalendarInstance parentCalendarInstance = this.calendarInstanceRepository.findByEntityIdAndEntityTypeIdAndCalendarTypeId(
                account.getId(), CalendarEntityType.SAVINGS.getValue(), CalendarType.COLLECTION.getValue());
        if (account.isCalendarInherited()) {
            calendarInstance = CalendarInstance.from(parentCalendarInstance.getCalendar(), account.getId(),
                    CalendarEntityType.SAVINGS.getValue());
        } else {
            LocalDate calendarStartDate = reinvestedDeposit.depositStartDate();
            Calendar parentCalendar = parentCalendarInstance.getCalendar();
            final String recurrence = parentCalendar.getRecurrence();
            final String title = "recurring_savings_" + reinvestedDeposit.getId();
            final Calendar calendar = Calendar.createRepeatingCalendar(title, calendarStartDate, CalendarType.COLLECTION.getValue(),
                    recurrence);
            calendarInstance = CalendarInstance.from(calendar, reinvestedDeposit.getId(), CalendarEntityType.SAVINGS.getValue());
        }
        if (calendarInstance == null) {
            final String defaultUserMessage = "No valid recurring details available for recurring depost account creation.";
            throw new GeneralPlatformDomainRuleException(
                    "error.msg.recurring.deposit.account.cannot.create.no.valid.recurring.details.available", defaultUserMessage,
                    account.clientId());
        }
        return calendarInstance;
    }

    private void autoGenerateAccountNumber(final SavingsAccount account) {
        if (account.isAccountNumberRequiresAutoGeneration()) {
            final AccountNumberFormat accountNumberFormat = this.accountNumberFormatRepository.findByAccountType(EntityAccountType.SAVINGS);
            account.updateAccountNo(this.accountNumberGenerator.generate(account, accountNumberFormat));
            this.savingsAccountRepository.save(account);
        }
    }

    @Transactional
    @Override
    public Long handleFDAccountPreMatureClosure(final FixedDepositAccount account, final PaymentDetail paymentDetail, final AppUser user,
            final JsonCommand command, final LocalDate tenantsTodayDate, final Map<String, Object> changes) {

        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();

        boolean isAccountTransfer = false;
        boolean isRegularTransaction = false;
        final boolean isPreMatureClosure = true;
        final Set<Long> existingTransactionIds = new HashSet<>();
        final Set<Long> existingReversedTransactionIds = new HashSet<>();
        /***
         * Update account transactionIds for post journal entries.
         */
        updateExistingTransactionsDetails(account, existingTransactionIds, existingReversedTransactionIds);

        final LocalDate closedDate = command.localDateValueOfParameterNamed(SavingsApiConstants.closedOnDateParamName);
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormat.forPattern(command.dateFormat()).withLocale(locale);
        Long savingsTransactionId = null;
       
        
        // post interest
        account.postPreMaturityInterest(closedDate, isPreMatureClosure, isSavingsInterestPostingAtCurrentPeriodEnd,
                financialYearBeginningMonth);

        final Integer closureTypeValue = command.integerValueOfParameterNamed(DepositsApiConstants.onAccountClosureIdParamName);
        DepositAccountOnClosureType closureType = DepositAccountOnClosureType.fromInt(closureTypeValue);

        if (closureType.isTransferToSavings()) {
            final boolean isExceptionForBalanceCheck = false;
            final Long toSavingsId = command.longValueOfParameterNamed(toSavingsAccountIdParamName);
            final String transferDescription = command.stringValueOfParameterNamed(transferDescriptionParamName);
            final SavingsAccount toSavingsAccount = this.depositAccountAssembler.assembleFrom(toSavingsId,
                    DepositAccountType.SAVINGS_DEPOSIT);
            final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(closedDate, account.getAccountBalance(),
                    PortfolioAccountType.SAVINGS, PortfolioAccountType.SAVINGS, null, null, transferDescription, locale, fmt, null, null,
                    null, null, null, AccountTransferType.ACCOUNT_TRANSFER.getValue(), null, null, null, null, toSavingsAccount, account,
                    isRegularTransaction, isExceptionForBalanceCheck);
            this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
            updateAlreadyPostedTransactions(existingTransactionIds, account);          
        } else {
            final SavingsAccountTransaction withdrawal = this.handleWithdrawal(account, fmt, closedDate, account.getAccountBalance(),
                    paymentDetail, false, isRegularTransaction);
            savingsTransactionId = withdrawal.getId();
        }

      
        account.prematureClosure(user, command, tenantsTodayDate, changes);

        this.savingsAccountRepository.save(account);

        postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, isAccountTransfer);
        return savingsTransactionId;
    }

    @Transactional
    @Override
    public Long handleRDAccountPreMatureClosure(final RecurringDepositAccount account, final PaymentDetail paymentDetail,
            final AppUser user, final JsonCommand command, final LocalDate tenantsTodayDate, final Map<String, Object> changes) {

        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();

        boolean isAccountTransfer = false;
        final boolean isPreMatureClosure = true;
        boolean isRegularTransaction = false;
        final Set<Long> existingTransactionIds = new HashSet<>();
        final Set<Long> existingReversedTransactionIds = new HashSet<>();
        /***
         * Update account transactionIds for post journal entries.
         */
        updateExistingTransactionsDetails(account, existingTransactionIds, existingReversedTransactionIds);

        final LocalDate closedDate = command.localDateValueOfParameterNamed(SavingsApiConstants.closedOnDateParamName);
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormat.forPattern(command.dateFormat()).withLocale(locale);
        Long savingsTransactionId = null;
        // post interest
        account.postPreMaturityInterest(closedDate, isPreMatureClosure, isSavingsInterestPostingAtCurrentPeriodEnd,
                financialYearBeginningMonth);

        final Integer closureTypeValue = command.integerValueOfParameterNamed(DepositsApiConstants.onAccountClosureIdParamName);
        DepositAccountOnClosureType closureType = DepositAccountOnClosureType.fromInt(closureTypeValue);

        if (closureType.isTransferToSavings()) {
            final boolean isExceptionForBalanceCheck = false;
            final Long toSavingsId = command.longValueOfParameterNamed(toSavingsAccountIdParamName);
            final String transferDescription = command.stringValueOfParameterNamed(transferDescriptionParamName);
            final SavingsAccount toSavingsAccount = this.depositAccountAssembler.assembleFrom(toSavingsId,
                    DepositAccountType.SAVINGS_DEPOSIT);
            final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(closedDate, account.getAccountBalance(),
                    PortfolioAccountType.SAVINGS, PortfolioAccountType.SAVINGS, null, null, transferDescription, locale, fmt, null, null,
                    null, null, null, AccountTransferType.ACCOUNT_TRANSFER.getValue(), null, null, null, null, toSavingsAccount, account,
                    isRegularTransaction, isExceptionForBalanceCheck);
            this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
            updateAlreadyPostedTransactions(existingTransactionIds, account);  
        } else {
            final SavingsAccountTransaction withdrawal = this.handleWithdrawal(account, fmt, closedDate, account.getAccountBalance(),
                    paymentDetail, false, isRegularTransaction);
            savingsTransactionId = withdrawal.getId();
        }

        account.prematureClosure(user, command, tenantsTodayDate, changes);
        this.savingsAccountRepository.save(account);
        postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, isAccountTransfer);
        return savingsTransactionId;
    }

    private void updateExistingTransactionsDetails(SavingsAccount account, Set<Long> existingTransactionIds,
            Set<Long> existingReversedTransactionIds) {
        existingTransactionIds.addAll(account.findExistingTransactionIds());
        existingReversedTransactionIds.addAll(account.findExistingReversedTransactionIds());
    }

    private void postJournalEntries(final SavingsAccount savingsAccount, final Set<Long> existingTransactionIds,
            final Set<Long> existingReversedTransactionIds, boolean isAccountTransfer) {

        final MonetaryCurrency currency = savingsAccount.getCurrency();
        final ApplicationCurrency applicationCurrency = this.applicationCurrencyRepositoryWrapper.findOneWithNotFoundDetection(currency);

        final Map<String, Object> accountingBridgeData = savingsAccount.deriveAccountingBridgeData(applicationCurrency.toData(),
                existingTransactionIds, existingReversedTransactionIds, isAccountTransfer);
        this.journalEntryWritePlatformService.createJournalEntriesForSavings(accountingBridgeData);
    }

    private void updateAlreadyPostedTransactions(final Set<Long> existingTransactionIds, final SavingsAccount savingsAccount) {
        List<SavingsAccountTransaction> transactions = savingsAccount.getTransactions() ;
        int size = transactions.size();
        for (int i = size - 1;; i--) {
            SavingsAccountTransaction transaction = transactions.get(i);
            if (transaction.isWithdrawal() || transaction.isWithdrawalFee()) {
                existingTransactionIds.add(transaction.getId());
            } else {
                break;
            }
        }
    }
}