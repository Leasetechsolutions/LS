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
package com.ls.infrastructure.documentmanagement.contentrepository;

import com.ls.infrastructure.configuration.data.S3CredentialsData;
import com.ls.infrastructure.configuration.domain.ConfigurationDomainService;
import com.ls.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import com.ls.infrastructure.documentmanagement.domain.StorageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ContentRepositoryFactory {

    private final ApplicationContext applicationContext;
    private final ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService;

    @Autowired
    public ContentRepositoryFactory(final ApplicationContext applicationContext,
            final ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService) {
        this.applicationContext = applicationContext;
        this.externalServicesReadPlatformService = externalServicesReadPlatformService;
    }

    public ContentRepository getRepository() {
        final ConfigurationDomainService configurationDomainServiceJpa = this.applicationContext.getBean("configurationDomainServiceJpa",
                ConfigurationDomainService.class);
        if (configurationDomainServiceJpa.isAmazonS3Enabled()) { return createS3DocumentStore(); }
        return new FileSystemContentRepository();
    }

    public ContentRepository getRepository(final StorageType documentStoreType) {
        if (documentStoreType == StorageType.FILE_SYSTEM) { return new FileSystemContentRepository(); }
        return createS3DocumentStore();
    }

    private ContentRepository createS3DocumentStore() {
        final S3CredentialsData s3CredentialsData = this.externalServicesReadPlatformService.getS3Credentials();
        return new S3ContentRepository(s3CredentialsData.getBucketName(), s3CredentialsData.getSecretKey(),
                s3CredentialsData.getAccessKey());
    }
}
