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
package com.ls.infrastructure.entityaccess.service;

import java.util.Date;
import java.util.Map;

import javax.persistence.PersistenceException;

import org.apache.commons.lang.exception.ExceptionUtils;
import com.ls.infrastructure.codes.domain.CodeValue;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.infrastructure.core.data.CommandProcessingResultBuilder;
import com.ls.infrastructure.core.exception.PlatformDataIntegrityException;
import com.ls.infrastructure.entityaccess.api.FineractEntityApiResourceConstants;
import com.ls.infrastructure.entityaccess.data.FineractEntityDataValidator;
import com.ls.infrastructure.entityaccess.domain.FineractEntityAccess;
import com.ls.infrastructure.entityaccess.domain.FineractEntityAccessRepository;
import com.ls.infrastructure.entityaccess.domain.FineractEntityRelation;
import com.ls.infrastructure.entityaccess.domain.FineractEntityRelationRepositoryWrapper;
import com.ls.infrastructure.entityaccess.domain.FineractEntityToEntityMapping;
import com.ls.infrastructure.entityaccess.domain.FineractEntityToEntityMappingRepository;
import com.ls.infrastructure.entityaccess.domain.FineractEntityToEntityMappingRepositoryWrapper;
import com.ls.infrastructure.entityaccess.exception.FineractEntityToEntityMappingDateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FineractEntityAccessWriteServiceImpl implements FineractEntityAccessWriteService {

    private final static Logger logger = LoggerFactory.getLogger(FineractEntityAccessWriteServiceImpl.class);
    private final FineractEntityAccessRepository entityAccessRepository;
    private final FineractEntityRelationRepositoryWrapper fineractEntityRelationRepositoryWrapper;
    private final FineractEntityToEntityMappingRepository fineractEntityToEntityMappingRepository;
    private final FineractEntityToEntityMappingRepositoryWrapper fineractEntityToEntityMappingRepositoryWrapper;
    private final FineractEntityDataValidator fromApiJsonDeserializer;

    @Autowired
    public FineractEntityAccessWriteServiceImpl(final FineractEntityAccessRepository entityAccessRepository,
            final FineractEntityRelationRepositoryWrapper fineractEntityRelationRepositoryWrapper,
            final FineractEntityToEntityMappingRepository fineractEntityToEntityMappingRepository,
            final FineractEntityToEntityMappingRepositoryWrapper fineractEntityToEntityMappingRepositoryWrapper,
            FineractEntityDataValidator fromApiJsonDeserializer) {
        this.entityAccessRepository = entityAccessRepository;
        this.fineractEntityToEntityMappingRepository = fineractEntityToEntityMappingRepository;
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
        this.fineractEntityRelationRepositoryWrapper = fineractEntityRelationRepositoryWrapper;
        this.fineractEntityToEntityMappingRepositoryWrapper = fineractEntityToEntityMappingRepositoryWrapper;
    }

    @Override
    public CommandProcessingResult createEntityAccess(@SuppressWarnings("unused") JsonCommand command) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    @Transactional
    public void addNewEntityAccess(final String entityType, final Long entityId, final CodeValue accessType, final String secondEntityType,
            final Long secondEntityId) {
        FineractEntityAccess entityAccess = FineractEntityAccess.createNew(entityType, entityId, accessType, secondEntityType, secondEntityId);
        entityAccessRepository.save(entityAccess);
    }

    @Override
    @Transactional
    public CommandProcessingResult createEntityToEntityMapping(Long relId, JsonCommand command) {

        try {

            this.fromApiJsonDeserializer.validateForCreate(command.json());

            final FineractEntityRelation mapId = this.fineractEntityRelationRepositoryWrapper.findOneWithNotFoundDetection(relId);

            final Long fromId = command.longValueOfParameterNamed(FineractEntityApiResourceConstants.fromEnityType);
            final Long toId = command.longValueOfParameterNamed(FineractEntityApiResourceConstants.toEntityType);
            final Date startDate = command.DateValueOfParameterNamed(FineractEntityApiResourceConstants.startDate);
            final Date endDate = command.DateValueOfParameterNamed(FineractEntityApiResourceConstants.endDate);

            fromApiJsonDeserializer.checkForEntity(relId.toString(), fromId, toId);
            if (startDate != null && endDate != null) {
                if (endDate
                        .before(startDate)) { throw new FineractEntityToEntityMappingDateException(startDate.toString(), endDate.toString()); }
            }

            final FineractEntityToEntityMapping newMap = FineractEntityToEntityMapping.newMap(mapId, fromId, toId, startDate, endDate);

            this.fineractEntityToEntityMappingRepository.save(newMap);

            return new CommandProcessingResultBuilder().withEntityId(newMap.getId()).withCommandId(command.commandId()).build();
        } catch (final DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        }catch (final PersistenceException dve) {
        	Throwable throwable = ExceptionUtils.getRootCause(dve.getCause()) ;
        	handleDataIntegrityIssues(command, throwable, dve);
        	return CommandProcessingResult.empty();
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult updateEntityToEntityMapping(Long mapId, JsonCommand command) {

        try {

            this.fromApiJsonDeserializer.validateForUpdate(command.json());

            final FineractEntityToEntityMapping mapForUpdate = this.fineractEntityToEntityMappingRepositoryWrapper
                    .findOneWithNotFoundDetection(mapId);

            String relId = mapForUpdate.getRelationId().getId().toString();
            final Long fromId = command.longValueOfParameterNamed(FineractEntityApiResourceConstants.fromEnityType);
            final Long toId = command.longValueOfParameterNamed(FineractEntityApiResourceConstants.toEntityType);
            fromApiJsonDeserializer.checkForEntity(relId, fromId, toId);

            final Map<String, Object> changes = mapForUpdate.updateMap(command);

            if (!changes.isEmpty()) {
                this.fineractEntityToEntityMappingRepository.saveAndFlush(mapForUpdate);
            }
            return new CommandProcessingResultBuilder(). //
                    withEntityId(mapForUpdate.getId()).withCommandId(command.commandId()).build();
        } catch (DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        }catch (final PersistenceException dve) {
        	Throwable throwable = ExceptionUtils.getRootCause(dve.getCause()) ;
        	handleDataIntegrityIssues(command, throwable, dve);
        	return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult deleteEntityToEntityMapping(Long mapId) {
        // TODO Auto-generated method stub

        final FineractEntityToEntityMapping deleteMap = this.fineractEntityToEntityMappingRepositoryWrapper.findOneWithNotFoundDetection(mapId);
        this.fineractEntityToEntityMappingRepository.delete(deleteMap);

        return new CommandProcessingResultBuilder(). //
                withEntityId(deleteMap.getId()).build();

    }

    private void handleDataIntegrityIssues(final JsonCommand command, final Throwable realCause, final Exception dve) {

        realCause.printStackTrace();
        if (realCause.getMessage().contains("rel_id_from_id_to_id")) {
            final String fromId = command.stringValueOfParameterNamed(FineractEntityApiResourceConstants.fromEnityType);
            final String toId = command.stringValueOfParameterNamed(FineractEntityApiResourceConstants.toEntityType);
            throw new PlatformDataIntegrityException("error.msg.duplicate.entity.mapping",
                    "EntityMapping from " + fromId + " to " + toId + " already exist");
        }

        logAsErrorUnexpectedDataIntegrityException(dve);
        throw new PlatformDataIntegrityException("error.msg.entity.mapping", "Unknown data integrity issue with resource.");
    }

    private void logAsErrorUnexpectedDataIntegrityException(final Exception dve) {
        logger.error(dve.getMessage(), dve);
    }

    /*
     * @Override public CommandProcessingResult updateEntityAccess(Long
     * entityAccessId, JsonCommand command) { // TODO Auto-generated method stub
     * return null; }
     * 
     * @Override public CommandProcessingResult removeEntityAccess(String
     * entityType, Long entityId, Long accessType, String secondEntityType, Long
     * secondEntityId) { // TODO Auto-generated method stub return null; }
     */

}