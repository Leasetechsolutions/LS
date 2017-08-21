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
package com.ls.portfolio.self.account.handler;

import com.ls.commands.annotation.CommandType;
import com.ls.commands.handler.NewCommandSourceHandler;
import com.ls.infrastructure.core.api.JsonCommand;
import com.ls.infrastructure.core.data.CommandProcessingResult;
import com.ls.portfolio.self.account.service.SelfBeneficiariesTPTWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@CommandType(entity = "SSBENEFICIARYTPT", action = "DELETE")
public class DeleteSelfBeneficiariesTPTCommandHandler implements
		NewCommandSourceHandler {
	private final SelfBeneficiariesTPTWritePlatformService writePlatformService;

	@Autowired
	public DeleteSelfBeneficiariesTPTCommandHandler(
			final SelfBeneficiariesTPTWritePlatformService writePlatformService) {
		this.writePlatformService = writePlatformService;
	}

	@Override
	public CommandProcessingResult processCommand(final JsonCommand command) {
		return this.writePlatformService.delete(command);
	}

}
