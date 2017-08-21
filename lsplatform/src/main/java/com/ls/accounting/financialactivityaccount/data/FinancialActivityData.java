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
package com.ls.accounting.financialactivityaccount.data;

import com.ls.accounting.glaccount.domain.GLAccountType;

public class FinancialActivityData {

    private final Integer id;
    private final String name;
    private final GLAccountType mappedGLAccountType;

    public Integer getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public GLAccountType getMappedGLAccountType() {
        return this.mappedGLAccountType;
    }

    public FinancialActivityData(Integer id, String name, GLAccountType mappedGLAccountType) {
        super();
        this.id = id;
        this.name = name;
        this.mappedGLAccountType = mappedGLAccountType;
    }

}