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
package com.ls.infrastructure.dataqueries.data;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.ls.portfolio.loanproduct.data.LoanProductData;
import com.ls.portfolio.savings.data.SavingsProductData;

/**
 * Immutable data object for role data.
 */
public class EntityDataTableChecksTemplateData implements Serializable {

	private final List<String> entities;
	private final List<DatatableCheckStatusData> statusClient;
	private final List<DatatableCheckStatusData> statusGroup;
	private final List<DatatableCheckStatusData> statusSavings;
	private final List<DatatableCheckStatusData> statusLoans;
	private final List<DatatableChecksData> datatables;
	private final Collection<LoanProductData> loanProductDatas;
	private final Collection<SavingsProductData> savingsProductDatas;

	public EntityDataTableChecksTemplateData(final List<String> entities, List<DatatableCheckStatusData> statusClient,
			List<DatatableCheckStatusData> statusGroup, List<DatatableCheckStatusData> statusSavings,
			List<DatatableCheckStatusData> statusLoans, List<DatatableChecksData> datatables,
			Collection<LoanProductData> loanProductDatas, Collection<SavingsProductData> savingsProductDatas) {

		this.entities = entities;
		this.statusClient = statusClient;
		this.statusGroup = statusGroup;
		this.statusSavings = statusSavings;
		this.statusLoans = statusLoans;
		this.datatables = datatables;
		this.loanProductDatas = loanProductDatas;
		this.savingsProductDatas = savingsProductDatas;
	}
}