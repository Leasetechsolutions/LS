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
package com.ls.integrationtests;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;
import com.ls.integrationtests.common.CommonConstants;
import com.ls.integrationtests.common.Utils;
import com.ls.integrationtests.common.WorkingDaysHelper;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class WorkingDaysTest {

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private ResponseSpecification generalResponseSpec;

    @Before
    public void setUp() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.generalResponseSpec = new ResponseSpecBuilder().build();

    }

    @Test
    public void updateWorkingDays() {
        HashMap response = (HashMap) WorkingDaysHelper.updateWorkingDays(requestSpec, responseSpec);
        Assert.assertNotNull(response.get("resourceId"));
    }

    @Test
    public void updateWorkingDaysWithWrongRecurrencePattern() {
        final List<HashMap> error = (List) WorkingDaysHelper.updateWorkingDaysWithWrongRecurrence(requestSpec, generalResponseSpec,
                CommonConstants.RESPONSE_ERROR);
        assertEquals("Verify wrong recurrence pattern error", "error.msg.recurring.rule.parsing.error",
                error.get(0).get("userMessageGlobalisationCode"));
    }

}
