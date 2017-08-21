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
package com.ls.accounting.glaccount.service;

import java.util.List;
import java.util.Map;

import com.ls.accounting.common.AccountingConstants;
import com.ls.accounting.glaccount.api.GLAccountJsonInputParams;
import com.ls.accounting.glaccount.command.GLAccountCommand;
import com.ls.accounting.glaccount.domain.GLAccount;
import com.ls.accounting.glaccount.domain.GLAccountRepository;
import com.ls.accounting.glaccount.domain.GLAccountType;
import com.ls.accounting.glaccount.exception.GLAccountDuplicateException;
import com.ls.accounting.glaccount.exception.GLAccountInvalidDeleteException;
import com.ls.accounting.glaccount.exception.GLAccountInvalidParentException;
import com.ls.accounting.glaccount.exception.GLAccountInvalidUpdateException;
import com.ls.accounting.glaccount.exception.GLAccountNotFoundException;
import com.ls.accounting.glaccount.exception.InvalidParentGLAccountHeadException;
import com.ls.accounting.glaccount.exception.GLAccountInvalidDeleteException.GL_ACCOUNT_INVALID_DELETE_REASON;
import com.ls.accounting.glaccount.exception.GLAccountInvalidUpdateException.GL_ACCOUNT_INVALID_UPDATE_REASON;
import com.ls.accounting.glaccount.serialization.GLAccountCommandFromApiJsonDeserializer;
import com.ls.accounting.journalentry.domain.JournalEntry;
import com.ls.accounting.journalentry.domain.JournalEntryRepository;
import com.ls.infrastructure.codes.domain.CodeValue;
import com.ls.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.infrastructure.core.data.CommandProcessingResultBuilder;
import com.ls.infrastructure.core.exception.PlatformDataIntegrityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GLAccountWritePlatformServiceJpaRepositoryImpl implements GLAccountWritePlatformService {

    private final static Logger logger = LoggerFactory.getLogger(GLAccountWritePlatformServiceJpaRepositoryImpl.class);

    private final GLAccountRepository glAccountRepository;
    private final JournalEntryRepository glJournalEntryRepository;
    private final GLAccountCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final CodeValueRepositoryWrapper codeValueRepositoryWrapper;

    @Autowired
    public GLAccountWritePlatformServiceJpaRepositoryImpl(final GLAccountRepository glAccountRepository,
            final JournalEntryRepository glJournalEntryRepository, final GLAccountCommandFromApiJsonDeserializer fromApiJsonDeserializer,
            final CodeValueRepositoryWrapper codeValueRepositoryWrapper) {
        this.glAccountRepository = glAccountRepository;
        this.glJournalEntryRepository = glJournalEntryRepository;
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
        this.codeValueRepositoryWrapper = codeValueRepositoryWrapper;
    }

    @Transactional
    @Override
    public CommandProcessingResult createGLAccount(final JsonCommand command) {
        try {
            final GLAccountCommand accountCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            accountCommand.validateForCreate();

            // check parent is valid
            final Long parentId = command.longValueOfParameterNamed(GLAccountJsonInputParams.PARENT_ID.getValue());
            GLAccount parentGLAccount = null;
            if (parentId != null) {
                parentGLAccount = validateParentGLAccount(parentId);
            }

            CodeValue glAccountTagType = null;
            final Long tagId = command.longValueOfParameterNamed(GLAccountJsonInputParams.TAGID.getValue());
            final Long type = command.longValueOfParameterNamed(GLAccountJsonInputParams.TYPE.getValue());
            final GLAccountType accountType = GLAccountType.fromInt(type.intValue());

            if (tagId != null) {
                glAccountTagType = retrieveTagId(tagId, accountType);
            }

            final GLAccount glAccount = GLAccount.fromJson(parentGLAccount, command, glAccountTagType);

            this.glAccountRepository.saveAndFlush(glAccount);

            glAccount.generateHierarchy();

            this.glAccountRepository.save(glAccount);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(glAccount.getId()).build();
        } catch (final DataIntegrityViolationException dve) {
            handleGLAccountDataIntegrityIssues(command, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult updateGLAccount(final Long glAccountId, final JsonCommand command) {
        try {
            final GLAccountCommand accountCommand = this.fromApiJsonDeserializer.commandFromApiJson(command.json());
            accountCommand.validateForUpdate();

            final Long parentId = command.longValueOfParameterNamed(GLAccountJsonInputParams.PARENT_ID.getValue());
            if (glAccountId.equals(parentId)) { throw new InvalidParentGLAccountHeadException(glAccountId, parentId); }
            // is the glAccount valid
            final GLAccount glAccount = this.glAccountRepository.findOne(glAccountId);
            if (glAccount == null) { throw new GLAccountNotFoundException(glAccountId); }

            final Map<String, Object> changesOnly = glAccount.update(command);

            // is the new parent valid
            if (changesOnly.containsKey(GLAccountJsonInputParams.PARENT_ID.getValue())) {
                final GLAccount parentAccount = validateParentGLAccount(parentId);
                glAccount.updateParentAccount(parentAccount);
            }

            if (changesOnly.containsKey(GLAccountJsonInputParams.TAGID.getValue())) {
                final Long tagIdLongValue = command.longValueOfParameterNamed(GLAccountJsonInputParams.TAGID.getValue());
                final GLAccountType accountType = GLAccountType.fromInt(glAccount.getType());
                CodeValue tagID = null;
                if (tagIdLongValue != null) {
                    tagID = retrieveTagId(tagIdLongValue, accountType);
                }
                glAccount.updateTagId(tagID);
            }

            /**
             * a detail account cannot be changed to a header account if
             * transactions are already logged against it
             **/
            if (changesOnly.containsKey(GLAccountJsonInputParams.USAGE.getValue())) {
                if (glAccount.isHeaderAccount()) {
                    final List<JournalEntry> journalEntriesForAccount = this.glJournalEntryRepository
                            .findFirstJournalEntryForAccount(glAccountId);
                    if (journalEntriesForAccount.size() > 0) { throw new GLAccountInvalidUpdateException(
                            GL_ACCOUNT_INVALID_UPDATE_REASON.TRANSANCTIONS_LOGGED, glAccountId); }
                }
            }

            if (!changesOnly.isEmpty()) {
                this.glAccountRepository.saveAndFlush(glAccount);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(glAccount.getId())
                    .with(changesOnly).build();
        } catch (final DataIntegrityViolationException dve) {
            handleGLAccountDataIntegrityIssues(command, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult deleteGLAccount(final Long glAccountId) {
        final GLAccount glAccount = this.glAccountRepository.findOne(glAccountId);

        if (glAccount == null) { throw new GLAccountNotFoundException(glAccountId); }

        // validate this isn't a header account that has children
        if (glAccount.isHeaderAccount() && glAccount.getChildren().size() > 0) { throw new GLAccountInvalidDeleteException(
                GL_ACCOUNT_INVALID_DELETE_REASON.HAS_CHILDREN, glAccountId); }

        // does this account have transactions logged against it
        final List<JournalEntry> journalEntriesForAccount = this.glJournalEntryRepository.findFirstJournalEntryForAccount(glAccountId);
        if (journalEntriesForAccount.size() > 0) { throw new GLAccountInvalidDeleteException(
                GL_ACCOUNT_INVALID_DELETE_REASON.TRANSANCTIONS_LOGGED, glAccountId); }
        this.glAccountRepository.delete(glAccount);

        return new CommandProcessingResultBuilder().withEntityId(glAccountId).build();
    }

    /**
     * @param command
     * @return
     */
    private GLAccount validateParentGLAccount(final Long parentAccountId) {
        GLAccount parentGLAccount = null;
        if (parentAccountId != null) {
            parentGLAccount = this.glAccountRepository.findOne(parentAccountId);
            if (parentGLAccount == null) { throw new GLAccountNotFoundException(parentAccountId); }
            // ensure parent is not a detail account
            if (parentGLAccount.isDetailAccount()) { throw new GLAccountInvalidParentException(parentAccountId); }
        }
        return parentGLAccount;
    }

    /**
     * @param command
     * @param dve
     */
    private void handleGLAccountDataIntegrityIssues(final JsonCommand command, final DataIntegrityViolationException dve) {
        final Throwable realCause = dve.getMostSpecificCause();
        if (realCause.getMessage().contains("acc_gl_code")) {
            final String glCode = command.stringValueOfParameterNamed(GLAccountJsonInputParams.GL_CODE.getValue());
            throw new GLAccountDuplicateException(glCode);
        }

        logger.error(dve.getMessage(), dve);
        throw new PlatformDataIntegrityException("error.msg.glAccount.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource GL Account: " + realCause.getMessage());
    }

    private CodeValue retrieveTagId(final Long tagId, final GLAccountType accountType) {
        CodeValue glAccountTagType = null;
        if (accountType.isAssetType()) {
            glAccountTagType = this.codeValueRepositoryWrapper.findOneByCodeNameAndIdWithNotFoundDetection(
                    AccountingConstants.ASSESTS_TAG_OPTION_CODE_NAME, tagId);
        } else if (accountType.isLiabilityType()) {
            glAccountTagType = this.codeValueRepositoryWrapper.findOneByCodeNameAndIdWithNotFoundDetection(
                    AccountingConstants.LIABILITIES_TAG_OPTION_CODE_NAME, tagId);
        } else if (accountType.isEquityType()) {
            glAccountTagType = this.codeValueRepositoryWrapper.findOneByCodeNameAndIdWithNotFoundDetection(
                    AccountingConstants.EQUITY_TAG_OPTION_CODE_NAME, tagId);
        } else if (accountType.isIncomeType()) {
            glAccountTagType = this.codeValueRepositoryWrapper.findOneByCodeNameAndIdWithNotFoundDetection(
                    AccountingConstants.INCOME_TAG_OPTION_CODE_NAME, tagId);
        } else if (accountType.isExpenseType()) {
            glAccountTagType = this.codeValueRepositoryWrapper.findOneByCodeNameAndIdWithNotFoundDetection(
                    AccountingConstants.EXPENSES_TAG_OPTION_CODE_NAME, tagId);
        }
        return glAccountTagType;
    }

}
