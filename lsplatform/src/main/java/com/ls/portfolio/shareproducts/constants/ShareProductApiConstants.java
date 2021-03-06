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
package com.ls.portfolio.shareproducts.constants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.ls.accounting.common.AccountingConstants;

public interface ShareProductApiConstants {

    // Command Strings
    public final String PREIEW_DIVIDENDS_COMMAND_STRING = "previewdividends";
    public final String POST_DIVIDENdS_COMMAND_STRING = "postdividends";
    public final String SHARE_PRODUCT_RESOURCE_NAME = "shareproduct";

    String locale_paramname = "locale";
    String dateFormatParamName = "dateFormat";
    String id_paramname = "id";
    String name_paramname = "name";
    String shortname_paramname = "shortName";
    String description_paramname = "description";
    String externalid_paramname = "externalId";
    String totalshares_paramname = "totalShares";
    String currency_paramname = "currencyCode";
    String digitsafterdecimal_paramname = "digitsAfterDecimal";
    String inmultiplesof_paramname = "inMultiplesOf";
    String totalsharesissued_paramname = "sharesIssued";
    String unitprice_paramname = "unitPrice";
    String sharecapital_paramname = "shareCapital";
    String minimumshares_paramname = "minimumShares";
    String nominaltshares_paramname = "nominalShares";
    String maximumshares_paramname = "maximumShares";
    String marketprice_paramname = "marketPricePeriods";
    String charges_paramname = "chargesSelected";
    String allowdividendcalculationforinactiveclients_paramname = "allowDividendCalculationForInactiveClients";
    String lockperiod_paramname = "lockinPeriodFrequency";
    String lockinperiodfrequencytype_paramname = "lockinPeriodFrequencyType";

    String minimumactiveperiodfordividends_paramname = "minimumActivePeriodForDividends";
    String minimumactiveperiodfrequencytype_paramname = "minimumactiveperiodFrequencyType";

    String startdate_paramname = "fromDate";
    String sharevalue_paramname = "shareValue";
    String dividendPeriodStartDateParamName = "dividendPeriodStartDate";
    String dividendPeriodEndDateParamName = "dividendPeriodEndDate";
    String dividendAmountParamName = "dividendAmount";

    String accountingRuleParamName = "accountingRule";

    Set<String> supportedParametersForCreate = new HashSet<>(Arrays.asList(locale_paramname, name_paramname, shortname_paramname,
            shortname_paramname, description_paramname, externalid_paramname, totalshares_paramname, currency_paramname,
            digitsafterdecimal_paramname, digitsafterdecimal_paramname, inmultiplesof_paramname, totalsharesissued_paramname,
            unitprice_paramname, minimumshares_paramname, nominaltshares_paramname, maximumshares_paramname, marketprice_paramname,
            charges_paramname, allowdividendcalculationforinactiveclients_paramname, lockperiod_paramname,
            lockinperiodfrequencytype_paramname, minimumactiveperiodfordividends_paramname, minimumactiveperiodfrequencytype_paramname,
            sharecapital_paramname, accountingRuleParamName,
            AccountingConstants.SHARES_PRODUCT_ACCOUNTING_PARAMS.INCOME_FROM_FEES.getValue(),
            AccountingConstants.SHARES_PRODUCT_ACCOUNTING_PARAMS.SHARES_EQUITY.getValue(),
            AccountingConstants.SHARES_PRODUCT_ACCOUNTING_PARAMS.SHARES_REFERENCE.getValue(),
            AccountingConstants.SHARES_PRODUCT_ACCOUNTING_PARAMS.SHARES_SUSPENSE.getValue()));

    Set<String> supportedParametersForDivident = new HashSet<>(Arrays.asList(locale_paramname, dateFormatParamName,
            dividendPeriodStartDateParamName, dividendPeriodEndDateParamName, dividendAmountParamName));

}
