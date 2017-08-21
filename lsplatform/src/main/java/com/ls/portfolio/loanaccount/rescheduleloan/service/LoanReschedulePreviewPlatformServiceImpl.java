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
package com.ls.portfolio.loanaccount.rescheduleloan.service;

import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.ls.organisation.monetary.domain.MoneyHelper;
import com.ls.portfolio.loanaccount.data.LoanTermVariationsData;
import com.ls.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import com.ls.portfolio.loanaccount.domain.Loan;
import com.ls.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import com.ls.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import com.ls.portfolio.loanaccount.domain.LoanRescheduleRequestToTermVariationMapping;
import com.ls.portfolio.loanaccount.domain.LoanSummaryWrapper;
import com.ls.portfolio.loanaccount.domain.LoanTermVariations;
import com.ls.portfolio.loanaccount.domain.transactionprocessor.LoanRepaymentScheduleTransactionProcessor;
import com.ls.portfolio.loanaccount.loanschedule.data.LoanScheduleDTO;
import com.ls.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import com.ls.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import com.ls.portfolio.loanaccount.loanschedule.domain.LoanScheduleGenerator;
import com.ls.portfolio.loanaccount.loanschedule.domain.LoanScheduleGeneratorFactory;
import com.ls.portfolio.loanaccount.loanschedule.domain.LoanScheduleModel;
import com.ls.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequest;
import com.ls.portfolio.loanaccount.rescheduleloan.domain.LoanRescheduleRequestRepository;
import com.ls.portfolio.loanaccount.rescheduleloan.exception.LoanRescheduleRequestNotFoundException;
import com.ls.portfolio.loanaccount.service.LoanUtilService;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoanReschedulePreviewPlatformServiceImpl implements LoanReschedulePreviewPlatformService {

    private final LoanRescheduleRequestRepository loanRescheduleRequestRepository;
    private final LoanUtilService loanUtilService;
    private final LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory;
    private final LoanScheduleGeneratorFactory loanScheduleFactory;
    private final LoanSummaryWrapper loanSummaryWrapper;
    private final DefaultScheduledDateGenerator scheduledDateGenerator = new DefaultScheduledDateGenerator();

    @Autowired
    public LoanReschedulePreviewPlatformServiceImpl(final LoanRescheduleRequestRepository loanRescheduleRequestRepository,
            final LoanUtilService loanUtilService,
            final LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            final LoanScheduleGeneratorFactory loanScheduleFactory, final LoanSummaryWrapper loanSummaryWrapper) {
        this.loanRescheduleRequestRepository = loanRescheduleRequestRepository;
        this.loanUtilService = loanUtilService;
        this.loanRepaymentScheduleTransactionProcessorFactory = loanRepaymentScheduleTransactionProcessorFactory;
        this.loanScheduleFactory = loanScheduleFactory;
        this.loanSummaryWrapper = loanSummaryWrapper;
    }

    @Override
    public LoanScheduleModel previewLoanReschedule(Long requestId) {
        final LoanRescheduleRequest loanRescheduleRequest = this.loanRescheduleRequestRepository.findOne(requestId);

        if (loanRescheduleRequest == null) { throw new LoanRescheduleRequestNotFoundException(requestId); }

        Loan loan = loanRescheduleRequest.getLoan();

        ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan,
                loanRescheduleRequest.getRescheduleFromDate());
        LocalDate rescheduleFromDate = null;
        List<LoanTermVariationsData> removeLoanTermVariationsData = new ArrayList<>();
        final LoanApplicationTerms loanApplicationTerms = loan.constructLoanApplicationTerms(scheduleGeneratorDTO);
        LoanTermVariations dueDateVariationInCurrentRequest = loanRescheduleRequest.getDueDateTermVariationIfExists();
        if(dueDateVariationInCurrentRequest != null){
            for (LoanTermVariationsData loanTermVariation : loanApplicationTerms.getLoanTermVariations().getDueDateVariation()) {
                if (loanTermVariation.getDateValue().equals(dueDateVariationInCurrentRequest.fetchTermApplicaDate())) {
                    rescheduleFromDate = loanTermVariation.getTermApplicableFrom();
                    removeLoanTermVariationsData.add(loanTermVariation);
                }
            }
        }
        loanApplicationTerms.getLoanTermVariations().getDueDateVariation().removeAll(removeLoanTermVariationsData);
        if (rescheduleFromDate == null) {
            rescheduleFromDate = loanRescheduleRequest.getRescheduleFromDate();
        }
        List<LoanTermVariationsData> loanTermVariationsData = new ArrayList<>();
        LocalDate adjustedApplicableDate = null;
        Set<LoanRescheduleRequestToTermVariationMapping> loanRescheduleRequestToTermVariationMappings = loanRescheduleRequest.getLoanRescheduleRequestToTermVariationMappings();
        if (!loanRescheduleRequestToTermVariationMappings.isEmpty()) {
            for (LoanRescheduleRequestToTermVariationMapping loanRescheduleRequestToTermVariationMapping : loanRescheduleRequestToTermVariationMappings) {
                if (loanRescheduleRequestToTermVariationMapping.getLoanTermVariations().getTermType().isDueDateVariation()
                        && rescheduleFromDate != null) {
                    adjustedApplicableDate = loanRescheduleRequestToTermVariationMapping.getLoanTermVariations().fetchDateValue();
                    loanRescheduleRequestToTermVariationMapping.getLoanTermVariations().setTermApplicableFrom(
                            rescheduleFromDate.toDate());
                }
                loanTermVariationsData.add(loanRescheduleRequestToTermVariationMapping.getLoanTermVariations().toData());
            }
        }
        
        for (LoanTermVariationsData loanTermVariation : loanApplicationTerms.getLoanTermVariations().getDueDateVariation()) {
            if (rescheduleFromDate.isBefore(loanTermVariation.getTermApplicableFrom())) {
                LocalDate applicableDate = this.scheduledDateGenerator.generateNextRepaymentDate(rescheduleFromDate, loanApplicationTerms,
                        false, loanApplicationTerms.getHolidayDetailDTO());
                if (loanTermVariation.getTermApplicableFrom().equals(applicableDate)) {
                    LocalDate adjustedDate = this.scheduledDateGenerator.generateNextRepaymentDate(adjustedApplicableDate,
                            loanApplicationTerms, false, loanApplicationTerms.getHolidayDetailDTO());
                    loanTermVariation.setApplicableFromDate(adjustedDate);
                    loanTermVariationsData.add(loanTermVariation);
                }
            }
        }
        
        loanApplicationTerms.getLoanTermVariations().updateLoanTermVariationsData(loanTermVariationsData);
        final RoundingMode roundingMode = MoneyHelper.getRoundingMode();
        final MathContext mathContext = new MathContext(8, roundingMode);
        final LoanRepaymentScheduleTransactionProcessor loanRepaymentScheduleTransactionProcessor = this.loanRepaymentScheduleTransactionProcessorFactory
                .determineProcessor(loan.transactionProcessingStrategy());
        final LoanScheduleGenerator loanScheduleGenerator = this.loanScheduleFactory.create(loanApplicationTerms.getInterestMethod());
        final LoanLifecycleStateMachine loanLifecycleStateMachine = null;
        loan.setHelpers(loanLifecycleStateMachine, this.loanSummaryWrapper, this.loanRepaymentScheduleTransactionProcessorFactory);
        final LoanScheduleDTO loanSchedule = loanScheduleGenerator.rescheduleNextInstallments(mathContext, loanApplicationTerms,
                loan, loanApplicationTerms.getHolidayDetailDTO(),
                loanRepaymentScheduleTransactionProcessor, rescheduleFromDate);
        final LoanScheduleModel loanScheduleModel = loanSchedule.getLoanScheduleModel();
        LoanScheduleModel loanScheduleModels = LoanScheduleModel.withLoanScheduleModelPeriods(loanScheduleModel.getPeriods(),
                loanScheduleModel);
        
        return loanScheduleModels;
    }

}
