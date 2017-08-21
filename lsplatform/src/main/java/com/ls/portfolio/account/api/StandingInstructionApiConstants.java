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
package com.ls.portfolio.account.api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ls.portfolio.account.AccountDetailConstants;

public class StandingInstructionApiConstants {

    public static final String STANDING_INSTRUCTION_RESOURCE_NAME = "standinginstruction";

    public static final String nameParamName = "name";
    public static final String priorityParamName = "priority";
    public static final String instructionTypeParamName = "instructionType";
    public static final String statusParamName = "status";
    public static final String amountParamName = "amount";
    public static final String validFromParamName = "validFrom";
    public static final String validTillParamName = "validTill";
    public static final String recurrenceTypeParamName = "recurrenceType";
    public static final String recurrenceFrequencyParamName = "recurrenceFrequency";
    public static final String recurrenceIntervalParamName = "recurrenceInterval";
    public static final String recurrenceOnMonthDayParamName = "recurrenceOnMonthDay";
    public static final String monthDayFormatParamName = "monthDayFormat";

    public static final Set<String> CREATE_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(
            AccountDetailConstants.localeParamName, AccountDetailConstants.dateFormatParamName,
            AccountDetailConstants.fromOfficeIdParamName, AccountDetailConstants.fromClientIdParamName,
            AccountDetailConstants.fromAccountTypeParamName, AccountDetailConstants.fromAccountIdParamName,
            AccountDetailConstants.toOfficeIdParamName, AccountDetailConstants.toClientIdParamName,
            AccountDetailConstants.toAccountTypeParamName, AccountDetailConstants.toAccountIdParamName,
            AccountDetailConstants.transferTypeParamName, priorityParamName, instructionTypeParamName, statusParamName, amountParamName,
            validFromParamName, validTillParamName, recurrenceTypeParamName, recurrenceFrequencyParamName, recurrenceIntervalParamName,
            recurrenceOnMonthDayParamName, nameParamName, monthDayFormatParamName));

    public static final Set<String> UPDATE_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(
            AccountDetailConstants.localeParamName, AccountDetailConstants.dateFormatParamName, priorityParamName,
            instructionTypeParamName, statusParamName, amountParamName, validFromParamName, validTillParamName, recurrenceTypeParamName,
            recurrenceFrequencyParamName, recurrenceIntervalParamName, recurrenceOnMonthDayParamName, monthDayFormatParamName));

    public static final Set<String> RESPONSE_DATA_PARAMETERS = new HashSet<>(Arrays.asList(AccountDetailConstants.idParamName,
            nameParamName, priorityParamName, instructionTypeParamName, statusParamName, AccountDetailConstants.transferTypeParamName,
            validFromParamName, validTillParamName));

}