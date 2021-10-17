/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package group.rxcloud.capa.spi.aws.configuration;

import group.rxcloud.capa.component.configstore.CapaConfigStore;
import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.capa.spi.aws.config.AwsClientProviderLoader;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import software.amazon.awssdk.services.appconfig.AppConfigAsyncClient;

/**
 * TODO load aws client from spi
 */
public class AwsCapaConfiguration extends CapaConfigStore {

    private AppConfigAsyncClient appConfigAsyncClient;

    /**
     * Instantiates a new Capa configuration.
     *
     * @param objectSerializer Serializer for transient request/response objects.
     */
    public AwsCapaConfiguration(CapaObjectSerializer objectSerializer) {
        super(objectSerializer);

        TypeRef<AppConfigAsyncClient> ref = TypeRef.get(AppConfigAsyncClient.class);
        appConfigAsyncClient = AwsClientProviderLoader.load(ref);
    }

    @Override
    public void close() throws Exception {

    }
}
