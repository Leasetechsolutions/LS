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
package com.ls.portfolio.interestratechart.service;

import static com.ls.portfolio.interestratechart.InterestIncentiveApiConstants.INCENTIVE_RESOURCE_NAME;
import static com.ls.portfolio.interestratechart.InterestIncentiveApiConstants.amountParamName;
import static com.ls.portfolio.interestratechart.InterestIncentiveApiConstants.attributeNameParamName;
import static com.ls.portfolio.interestratechart.InterestIncentiveApiConstants.attributeValueParamName;
import static com.ls.portfolio.interestratechart.InterestIncentiveApiConstants.conditionTypeParamName;
import static com.ls.portfolio.interestratechart.InterestIncentiveApiConstants.entityTypeParamName;
import static com.ls.portfolio.interestratechart.InterestIncentiveApiConstants.incentiveTypeparamName;
import static com.ls.portfolio.interestratechart.InterestRateChartSlabApiConstants.incentivesParamName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.ls.infrastructure.core.data.ApiParameterError;
import com.ls.infrastructure.core.data.DataValidatorBuilder;
import com.ls.infrastructure.core.exception.PlatformApiDataValidationException;
import com.ls.infrastructure.core.serialization.FromJsonHelper;
import com.ls.portfolio.interestratechart.domain.InterestIncentives;
import com.ls.portfolio.interestratechart.domain.InterestIncentivesFields;
import com.ls.portfolio.interestratechart.domain.InterestRateChartSlab;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Service
public class InterestIncentiveAssembler {

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public InterestIncentiveAssembler(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public Collection<InterestIncentives> assembleIncentivesFrom(final JsonElement element, InterestRateChartSlab interestRateChartSlab,
            final Locale locale) {
        final Collection<InterestIncentives> interestIncentivesSet = new HashSet<>();

        if (element.isJsonObject()) {
            final JsonObject topLevelJsonElement = element.getAsJsonObject();
            if (topLevelJsonElement.has(incentivesParamName) && topLevelJsonElement.get(incentivesParamName).isJsonArray()) {
                final JsonArray array = topLevelJsonElement.get(incentivesParamName).getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    final JsonObject incentiveElement = array.get(i).getAsJsonObject();
                    final InterestIncentives incentives = this.assembleFrom(incentiveElement, interestRateChartSlab, locale);
                    interestIncentivesSet.add(incentives);
                }
            }
        }

        return interestIncentivesSet;
    }

    private InterestIncentives assembleFrom(final JsonElement element, final InterestRateChartSlab interestRateChartSlab,
            final Locale locale) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(INCENTIVE_RESOURCE_NAME);
        InterestIncentivesFields incentivesFields = createInterestIncentiveFields(element, baseDataValidator, locale);
        throwExceptionIfValidationWarningsExist(dataValidationErrors);
        return new InterestIncentives(interestRateChartSlab, incentivesFields);
    }

    private InterestIncentivesFields createInterestIncentiveFields(final JsonElement element, final DataValidatorBuilder baseDataValidator,
            final Locale locale) {
        Integer entityType = this.fromApiJsonHelper.extractIntegerNamed(entityTypeParamName, element, locale);
        Integer conditionType = this.fromApiJsonHelper.extractIntegerNamed(conditionTypeParamName, element, locale);
        Integer attributeName = this.fromApiJsonHelper.extractIntegerNamed(attributeNameParamName, element, locale);
        String attributeValue = this.fromApiJsonHelper.extractStringNamed(attributeValueParamName, element);
        Integer incentiveType = this.fromApiJsonHelper.extractIntegerNamed(incentiveTypeparamName, element, locale);
        BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(amountParamName, element, locale);
        return InterestIncentivesFields.createNew(entityType, attributeName, conditionType, attributeValue, incentiveType, amount,
                baseDataValidator);
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
    }
}
