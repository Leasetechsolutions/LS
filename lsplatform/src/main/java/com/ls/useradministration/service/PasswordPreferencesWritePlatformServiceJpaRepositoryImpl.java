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
package com.ls.useradministration.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.infrastructure.core.data.CommandProcessingResultBuilder;
import com.ls.infrastructure.core.exception.PlatformDataIntegrityException;
import com.ls.useradministration.api.PasswordPreferencesApiConstants;
import com.ls.useradministration.data.PasswordPreferencesDataValidator;
import com.ls.useradministration.domain.PasswordValidationPolicy;
import com.ls.useradministration.domain.PasswordValidationPolicyRepository;
import com.ls.useradministration.exception.PasswordValidationPolicyNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordPreferencesWritePlatformServiceJpaRepositoryImpl implements PasswordPreferencesWritePlatformService {

    private final static Logger logger = LoggerFactory.getLogger(PasswordPreferencesWritePlatformServiceJpaRepositoryImpl.class);
    private final PasswordValidationPolicyRepository validationRepository;
    private final PasswordPreferencesDataValidator dataValidator;

    @Autowired
    public PasswordPreferencesWritePlatformServiceJpaRepositoryImpl(final PasswordValidationPolicyRepository validationPolicyRepository,
            final PasswordPreferencesDataValidator dataValidator) {
        this.validationRepository = validationPolicyRepository;
        this.dataValidator = dataValidator;

    }

    @Transactional
    @Override
    public CommandProcessingResult updatePreferences(final JsonCommand command) {

        this.dataValidator.validateForUpdate(command.json());
        Long validationPolicyId = command.longValueOfParameterNamed(PasswordPreferencesApiConstants.VALIDATION_POLICY_ID);
        try {
            final List<PasswordValidationPolicy> validationPolicies = this.validationRepository.findAll();

            Map<String, Object> changes = new HashMap<>(1);

            boolean found = false;

            for (PasswordValidationPolicy policy : validationPolicies) {
                if (policy.getId().equals(validationPolicyId)) {
                    found = true;
                    if (!policy.isActive()) {
                        changes = policy.activate();
                    }
                } else if (policy.isActive() && !policy.getId().equals(validationPolicyId)) {
                    policy.deActivate();
                }
            }

            if (!found) { throw new PasswordValidationPolicyNotFoundException(validationPolicyId); }

            if (!changes.isEmpty()) {
                this.validationRepository.save(validationPolicies);
                this.validationRepository.flush();
            }

            return new CommandProcessingResultBuilder() //
                    .withCommandId(command.commandId()) //
                    .with(changes) //
                    .build();
        } catch (final DataIntegrityViolationException dve) {
            logger.error(dve.getMessage(), dve);
            throw new PlatformDataIntegrityException("error.msg.password.validation.policy.unknown.data.integrity.issue",
                    "Unknown data integrity issue with resource.");
        }
    }
}