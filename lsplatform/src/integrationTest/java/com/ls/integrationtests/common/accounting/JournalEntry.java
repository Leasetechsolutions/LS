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
package com.ls.integrationtests.common.accounting;

public class JournalEntry {

    public enum TransactionType {
        CREDIT("CREDIT"), DEBIT("DEBIT");

        private TransactionType(final String type) {
            this.type = type;
        }

        private final String type;

        @Override
        public String toString() {
            return this.type;
        }
    }

    private final Float transactionAmount;
    private final TransactionType transactionType;
    private final Integer officeId;

    public JournalEntry(final float transactionAmount, final TransactionType type) {
        this.transactionAmount = transactionAmount;
        this.transactionType = type;
        this.officeId = null;
    }

    public Float getTransactionAmount() {
        return this.transactionAmount;
    }

    public String getTransactionType() {
        return this.transactionType.toString();
    }

}
