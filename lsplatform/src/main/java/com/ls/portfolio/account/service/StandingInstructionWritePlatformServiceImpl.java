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
package com.ls.portfolio.account.service;

import static com.ls.portfolio.account.AccountDetailConstants.fromAccountTypeParamName;
import static com.ls.portfolio.account.AccountDetailConstants.fromClientIdParamName;
import static com.ls.portfolio.account.AccountDetailConstants.toAccountTypeParamName;
import static com.ls.portfolio.account.api.StandingInstructionApiConstants.statusParamName;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.infrastructure.core.data.CommandProcessingResultBuilder;
import com.ls.infrastructure.core.exception.AbstractPlatformServiceUnavailableException;
import com.ls.infrastructure.core.exception.PlatformApiDataValidationException;
import com.ls.infrastructure.core.exception.PlatformDataIntegrityException;
import com.ls.infrastructure.core.service.RoutingDataSource;
import com.ls.infrastructure.jobs.annotation.CronTarget;
import com.ls.infrastructure.jobs.exception.JobExecutionException;
import com.ls.infrastructure.jobs.service.JobName;
import com.ls.portfolio.account.PortfolioAccountType;
import com.ls.portfolio.account.api.StandingInstructionApiConstants;
import com.ls.portfolio.account.data.AccountTransferDTO;
import com.ls.portfolio.account.data.StandingInstructionData;
import com.ls.portfolio.account.data.StandingInstructionDataValidator;
import com.ls.portfolio.account.data.StandingInstructionDuesData;
import com.ls.portfolio.account.domain.AccountTransferDetailRepository;
import com.ls.portfolio.account.domain.AccountTransferDetails;
import com.ls.portfolio.account.domain.AccountTransferRecurrenceType;
import com.ls.portfolio.account.domain.AccountTransferStandingInstruction;
import com.ls.portfolio.account.domain.StandingInstructionAssembler;
import com.ls.portfolio.account.domain.StandingInstructionRepository;
import com.ls.portfolio.account.domain.StandingInstructionStatus;
import com.ls.portfolio.account.domain.StandingInstructionType;
import com.ls.portfolio.account.exception.StandingInstructionNotFoundException;
import com.ls.portfolio.common.domain.PeriodFrequencyType;
import com.ls.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import com.ls.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import com.ls.portfolio.savings.domain.SavingsAccount;
import com.ls.portfolio.savings.exception.InsufficientAccountBalanceException;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StandingInstructionWritePlatformServiceImpl implements StandingInstructionWritePlatformService {

    private final static Logger logger = LoggerFactory.getLogger(StandingInstructionWritePlatformServiceImpl.class);

    private final StandingInstructionDataValidator standingInstructionDataValidator;
    private final StandingInstructionAssembler standingInstructionAssembler;
    private final AccountTransferDetailRepository accountTransferDetailRepository;
    private final StandingInstructionRepository standingInstructionRepository;
    private final StandingInstructionReadPlatformService standingInstructionReadPlatformService;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public StandingInstructionWritePlatformServiceImpl(final StandingInstructionDataValidator standingInstructionDataValidator,
            final StandingInstructionAssembler standingInstructionAssembler,
            final AccountTransferDetailRepository accountTransferDetailRepository,
            final StandingInstructionRepository standingInstructionRepository,
            final StandingInstructionReadPlatformService standingInstructionReadPlatformService,
            final AccountTransfersWritePlatformService accountTransfersWritePlatformService, final RoutingDataSource dataSource) {
        this.standingInstructionDataValidator = standingInstructionDataValidator;
        this.standingInstructionAssembler = standingInstructionAssembler;
        this.accountTransferDetailRepository = accountTransferDetailRepository;
        this.standingInstructionRepository = standingInstructionRepository;
        this.standingInstructionReadPlatformService = standingInstructionReadPlatformService;
        this.accountTransfersWritePlatformService = accountTransfersWritePlatformService;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Transactional
    @Override
    public CommandProcessingResult create(final JsonCommand command) {

        this.standingInstructionDataValidator.validateForCreate(command);

        final Integer fromAccountTypeId = command.integerValueSansLocaleOfParameterNamed(fromAccountTypeParamName);
        final PortfolioAccountType fromAccountType = PortfolioAccountType.fromInt(fromAccountTypeId);

        final Integer toAccountTypeId = command.integerValueSansLocaleOfParameterNamed(toAccountTypeParamName);
        final PortfolioAccountType toAccountType = PortfolioAccountType.fromInt(toAccountTypeId);

        final Long fromClientId = command.longValueOfParameterNamed(fromClientIdParamName);

        Long standingInstructionId = null;
        try {
            if (isSavingsToSavingsAccountTransfer(fromAccountType, toAccountType)) {
                final AccountTransferDetails standingInstruction = this.standingInstructionAssembler
                        .assembleSavingsToSavingsTransfer(command);
                this.accountTransferDetailRepository.save(standingInstruction);
                standingInstructionId = standingInstruction.accountTransferStandingInstruction().getId();
            } else if (isSavingsToLoanAccountTransfer(fromAccountType, toAccountType)) {
                final AccountTransferDetails standingInstruction = this.standingInstructionAssembler.assembleSavingsToLoanTransfer(command);
                this.accountTransferDetailRepository.save(standingInstruction);
                standingInstructionId = standingInstruction.accountTransferStandingInstruction().getId();
            } else if (isLoanToSavingsAccountTransfer(fromAccountType, toAccountType)) {

                final AccountTransferDetails standingInstruction = this.standingInstructionAssembler.assembleLoanToSavingsTransfer(command);
                this.accountTransferDetailRepository.save(standingInstruction);
                standingInstructionId = standingInstruction.accountTransferStandingInstruction().getId();

            }
        } catch (final DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve);
            return CommandProcessingResult.empty();
        }
        final CommandProcessingResultBuilder builder = new CommandProcessingResultBuilder().withEntityId(standingInstructionId)
                .withClientId(fromClientId);
        return builder.build();
    }

    private void handleDataIntegrityIssues(final JsonCommand command, final DataIntegrityViolationException dve) {

        final Throwable realCause = dve.getMostSpecificCause();
        if (realCause.getMessage().contains("name")) {
            final String name = command.stringValueOfParameterNamed(StandingInstructionApiConstants.nameParamName);
            throw new PlatformDataIntegrityException("error.msg.standinginstruction.duplicate.name", "Standinginstruction with name `"
                    + name + "` already exists", "name", name);
        }
        logger.error(dve.getMessage(), dve);
        throw new PlatformDataIntegrityException("error.msg.client.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource.");
    }

    private boolean isLoanToSavingsAccountTransfer(final PortfolioAccountType fromAccountType, final PortfolioAccountType toAccountType) {
        return fromAccountType.isLoanAccount() && toAccountType.isSavingsAccount();
    }

    private boolean isSavingsToLoanAccountTransfer(final PortfolioAccountType fromAccountType, final PortfolioAccountType toAccountType) {
        return fromAccountType.isSavingsAccount() && toAccountType.isLoanAccount();
    }

    private boolean isSavingsToSavingsAccountTransfer(final PortfolioAccountType fromAccountType, final PortfolioAccountType toAccountType) {
        return fromAccountType.isSavingsAccount() && toAccountType.isSavingsAccount();
    }

    @Override
    public CommandProcessingResult update(final Long id, final JsonCommand command) {
        this.standingInstructionDataValidator.validateForUpdate(command);
        AccountTransferStandingInstruction standingInstructionsForUpdate = this.standingInstructionRepository.findOne(id);
        if (standingInstructionsForUpdate == null) { throw new StandingInstructionNotFoundException(id); }
        final Map<String, Object> actualChanges = standingInstructionsForUpdate.update(command);
        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(id) //
                .with(actualChanges) //
                .build();
    }

    @Override
    public CommandProcessingResult delete(final Long id) {
        AccountTransferStandingInstruction standingInstructionsForUpdate = this.standingInstructionRepository.findOne(id);
        // update the "deleted" and "name" properties of the standing instruction
        standingInstructionsForUpdate.delete();
        
        final Map<String, Object> actualChanges = new HashMap<>();
        actualChanges.put(statusParamName, StandingInstructionStatus.DELETED.getValue());
        return new CommandProcessingResultBuilder() //
                .withEntityId(id) //
                .with(actualChanges) //
                .build();
    }

    @Override
    @CronTarget(jobName = JobName.EXECUTE_STANDING_INSTRUCTIONS)
    public void executeStandingInstructions() throws JobExecutionException {
        Collection<StandingInstructionData> instructionDatas = this.standingInstructionReadPlatformService
                .retrieveAll(StandingInstructionStatus.ACTIVE.getValue());
        final StringBuilder sb = new StringBuilder();
        for (StandingInstructionData data : instructionDatas) {
            boolean isDueForTransfer = false;
            AccountTransferRecurrenceType recurrenceType = data.recurrenceType();
            StandingInstructionType instructionType = data.instructionType();
            LocalDate transactionDate = new LocalDate();
            if (recurrenceType.isPeriodicRecurrence()) {
                final ScheduledDateGenerator scheduledDateGenerator = new DefaultScheduledDateGenerator();
                PeriodFrequencyType frequencyType = data.recurrenceFrequency();
                LocalDate startDate = data.validFrom();
                if (frequencyType.isMonthly()) {
                    startDate = startDate.withDayOfMonth(data.recurrenceOnDay());
                    if (startDate.isBefore(data.validFrom())) {
                        startDate = startDate.plusMonths(1);
                    }
                } else if (frequencyType.isYearly()) {
                    startDate = startDate.withDayOfMonth(data.recurrenceOnDay()).withMonthOfYear(data.recurrenceOnMonth());
                    if (startDate.isBefore(data.validFrom())) {
                        startDate = startDate.plusYears(1);
                    }
                }
                isDueForTransfer = scheduledDateGenerator.isDateFallsInSchedule(frequencyType, data.recurrenceInterval(), startDate,
                        transactionDate);

            }
            BigDecimal transactionAmount = data.amount();
            if (data.toAccountType().isLoanAccount()
                    && (recurrenceType.isDuesRecurrence() || (isDueForTransfer && instructionType.isDuesAmoutTransfer()))) {
                StandingInstructionDuesData standingInstructionDuesData = this.standingInstructionReadPlatformService
                        .retriveLoanDuesData(data.toAccount().accountId());
                if (data.instructionType().isDuesAmoutTransfer()) {
                    transactionAmount = standingInstructionDuesData.totalDueAmount();
                }
                if (recurrenceType.isDuesRecurrence()) {
                    isDueForTransfer = new LocalDate().equals(standingInstructionDuesData.dueDate());
                }
            }

            if (isDueForTransfer && transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) > 0) {
                final SavingsAccount fromSavingsAccount = null;
                final boolean isRegularTransaction = true;
                final boolean isExceptionForBalanceCheck = false;
                AccountTransferDTO accountTransferDTO = new AccountTransferDTO(transactionDate, transactionAmount, data.fromAccountType(),
                        data.toAccountType(), data.fromAccount().accountId(), data.toAccount().accountId(), data.name()
                                + " Standing instruction trasfer ", null, null, null, null, data.toTransferType(), null, null, data
                                .transferType().getValue(), null, null, null, null, null, fromSavingsAccount,
                        isRegularTransaction, isExceptionForBalanceCheck);
                final boolean transferCompleted = transferAmount(sb, accountTransferDTO, data.getId());

                if(transferCompleted){
                    final String updateQuery = "UPDATE m_account_transfer_standing_instructions SET last_run_date = ? where id = ?";
                    this.jdbcTemplate.update(updateQuery, transactionDate.toDate(), data.getId());
                }

            }
        }
        if (sb.length() > 0) { throw new JobExecutionException(sb.toString()); }

    }

    /**
     * @param sb
     * @param accountTransferDTO
     */
    private boolean transferAmount(final StringBuilder sb, final AccountTransferDTO accountTransferDTO, final Long instructionId) {
        boolean transferCompleted = true;
        StringBuffer errorLog = new StringBuffer();
        StringBuffer updateQuery = new StringBuffer(
                "INSERT INTO `m_account_transfer_standing_instructions_history` (`standing_instruction_id`, `status`, `amount`,`execution_time`, `error_log`) VALUES (");
        try {
            this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
        } catch (final PlatformApiDataValidationException e) {
            sb.append("Validation exception while trasfering funds for standing Instruction id").append(instructionId).append(" from ")
                    .append(accountTransferDTO.getFromAccountId()).append(" to ").append(accountTransferDTO.getToAccountId())
                    .append("--------");
            errorLog.append("Validation exception while trasfering funds " + e.getDefaultUserMessage());
        } catch (final InsufficientAccountBalanceException e) {
            sb.append("InsufficientAccountBalance Exception while trasfering funds for standing Instruction id").append(instructionId)
                    .append(" from ").append(accountTransferDTO.getFromAccountId()).append(" to ")
                    .append(accountTransferDTO.getToAccountId()).append("--------");
            errorLog.append("InsufficientAccountBalance Exception ");
        } catch (final AbstractPlatformServiceUnavailableException e) {
            sb.append("Platform exception while trasfering funds for standing Instruction id").append(instructionId).append(" from ")
                    .append(accountTransferDTO.getFromAccountId()).append(" to ").append(accountTransferDTO.getToAccountId())
                    .append("--------");
            errorLog.append("Platform exception while trasfering funds " + e.getDefaultUserMessage());
        } catch (Exception e) {
            sb.append("Exception while trasfering funds for standing Instruction id").append(instructionId).append(" from ")
                    .append(accountTransferDTO.getFromAccountId()).append(" to ").append(accountTransferDTO.getToAccountId())
                    .append("--------");
            errorLog.append("Exception while trasfering funds " + e.getMessage());

        }
        updateQuery.append(instructionId).append(",");
        if (errorLog.length() > 0) {
            transferCompleted = false;
            updateQuery.append("'failed'").append(",");
        } else {
            updateQuery.append("'success'").append(",");
        }
        updateQuery.append(accountTransferDTO.getTransactionAmount().doubleValue());
        updateQuery.append(", now(),");
        updateQuery.append("'").append(errorLog.toString()).append("')");
        this.jdbcTemplate.update(updateQuery.toString());
        return transferCompleted;
    }
}