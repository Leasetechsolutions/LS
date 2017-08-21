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
package com.ls.infrastructure.cache.command;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import com.ls.commands.annotation.CommandType;
import com.ls.commands.handler.NewCommandSourceHandler;
import com.ls.infrastructure.cache.CacheApiConstants;
import com.ls.infrastructure.cache.domain.CacheType;
import com.ls.infrastructure.cache.service.CacheWritePlatformService;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.ApiParameterError;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.infrastructure.core.data.CommandProcessingResultBuilder;
import com.ls.infrastructure.core.data.DataValidatorBuilder;
import com.ls.infrastructure.core.exception.InvalidJsonException;
import com.ls.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.reflect.TypeToken;

@Service
@CommandType(entity = "CACHE", action = "UPDATE")
public class UpdateCacheCommandHandler implements NewCommandSourceHandler {

    private final CacheWritePlatformService cacheService;

    @Autowired
    public UpdateCacheCommandHandler(final CacheWritePlatformService cacheService) {
        this.cacheService = cacheService;
    }

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {

        final String json = command.json();

        if (StringUtils.isBlank(json)) { throw new InvalidJsonException(); }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        command.checkForUnsupportedParameters(typeOfMap, json, CacheApiConstants.REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(CacheApiConstants.RESOURCE_NAME.toLowerCase());

        final int cacheTypeEnum = command.integerValueSansLocaleOfParameterNamed(CacheApiConstants.cacheTypeParameter);
        baseDataValidator.reset().parameter(CacheApiConstants.cacheTypeParameter).value(Integer.valueOf(cacheTypeEnum)).notNull()
                .isOneOfTheseValues(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3));

        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }

        final CacheType cacheType = CacheType.fromInt(cacheTypeEnum);

        final Map<String, Object> changes = this.cacheService.switchToCache(cacheType);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).with(changes).build();
    }
}