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
package group.rxcloud.capa.spi.aws.config;

import group.rxcloud.capa.component.configstore.CapaConfigStore;
import group.rxcloud.capa.component.configstore.ConfigurationItem;
import group.rxcloud.capa.component.configstore.GetRequest;
import group.rxcloud.capa.component.configstore.StoreConfig;
import group.rxcloud.capa.component.configstore.SubscribeReq;
import group.rxcloud.capa.component.configstore.SubscribeResp;
import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.appconfig.AppConfigAsyncClient;

import java.util.List;

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
        appConfigAsyncClient = AppConfigAsyncClient.create();
    }

    @Override
    protected void doInit(StoreConfig storeConfig) {

    }

    @Override
    public <T> Mono<List<ConfigurationItem<T>>> get(GetRequest getRequest, TypeRef<T> typeRef) {
        return null;
    }

    @Override
    public <T> Flux<SubscribeResp<T>> subscribe(SubscribeReq subscribeReq, TypeRef<T> typeRef) {
        return null;
    }

    @Override
    public String stopSubscribe() {
        return null;
    }

    @Override
    public String getDefaultGroup() {
        return null;
    }

    @Override
    public String getDefaultLabel() {
        return null;
    }

    @Override
    public void close() throws Exception {

    }
}
