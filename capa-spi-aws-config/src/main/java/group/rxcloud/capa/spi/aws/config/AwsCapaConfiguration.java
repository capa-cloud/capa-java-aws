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

import com.google.common.collect.Lists;
import group.rxcloud.capa.component.configstore.ConfigurationItem;
import group.rxcloud.capa.component.configstore.StoreConfig;
import group.rxcloud.capa.component.configstore.SubscribeResp;
import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.capa.spi.aws.config.common.serializer.SerializerProcessor;
import group.rxcloud.capa.spi.aws.config.entity.Configuration;
import group.rxcloud.capa.spi.aws.config.scheduler.AwsCapaConfigurationScheduler;
import group.rxcloud.capa.spi.configstore.CapaConfigStoreSpi;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfig.AppConfigAsyncClient;
import software.amazon.awssdk.services.appconfig.model.GetConfigurationRequest;
import software.amazon.awssdk.services.appconfig.model.GetConfigurationResponse;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static group.rxcloud.capa.spi.aws.config.common.constant.CapaAWSConstants.AWS_APP_CONFIG_NAME;
import static group.rxcloud.capa.spi.aws.config.common.constant.CapaAWSConstants.DEFAULT_ENV;

/**
 *
 */
public class AwsCapaConfiguration extends CapaConfigStoreSpi {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsCapaConfiguration.class);

    private AppConfigAsyncClient appConfigAsyncClient;

    private SerializerProcessor serializerProcessor;

    /**
     * key of versionMap--applicationName,format:appid_ENV,e.g:"12345_FAT"
     * value of versionMap--configurationMap of this application,which contains all the configuration info for this application
     * <p>
     * key of configurationMap--configurationName;
     * value of configurationMap--configuration content and version of this configuration
     * <p>
     * ps:currentHashMap may not be necessary, as update in synchronized method
     */
    private static Map<String, ConcurrentHashMap<String, Configuration<?>>> versionMap;

    /**
     * Instantiates a new Capa configuration.
     *
     * @param objectSerializer Serializer for transient request/response objects.
     */
    public AwsCapaConfiguration(CapaObjectSerializer objectSerializer) {
        super(objectSerializer);
        appConfigAsyncClient = AppConfigAsyncClient.create();
        versionMap = new ConcurrentHashMap<>();
        serializerProcessor = new SerializerProcessor(objectSerializer);
    }

    @Override
    protected void doInit(StoreConfig storeConfig) {
        //no need
    }

    @Override
    protected <T> Mono<List<ConfigurationItem<T>>> doGet(String appId, String group, String label, List<String> keys, Map<String, String> metadata, TypeRef<T> type) {
        List<ConfigurationItem<T>> items = new ArrayList<>();
        if (CollectionUtils.isNullOrEmpty(keys)) {
            return Mono.error(new IllegalArgumentException("keys is null or empty"));
        }

        //todo:need to get the specific env from system properties
        String applicationName = appId + "_FAT";

        String configurationName = keys.get(0);
        String clientConfigurationVersion = getCurVersion(applicationName, configurationName);

        GetConfigurationRequest request = GetConfigurationRequest.builder()
                .application(applicationName)
                .clientId(UUID.randomUUID().toString())
                .configuration(configurationName)
                .clientConfigurationVersion(clientConfigurationVersion)
                .environment(DEFAULT_ENV)
                .build();

        return Mono.fromFuture(() -> appConfigAsyncClient.getConfiguration(request))
                .publishOn(AwsCapaConfigurationScheduler.INSTANCE.configPublisherScheduler)
                .map(resp -> {
                    //if version doesn't change, get from versionMap
                    if (Objects.equals(clientConfigurationVersion, resp.configurationVersion())) {
                        items.add((ConfigurationItem<T>) getCurConfigurationItem(applicationName, configurationName));
                    } else {
                        //if version changes,update versionMap and return
                        items.add(updateConfigurationItem(applicationName, configurationName, type, resp.content(), resp.configurationVersion()));
                    }
                    return items;
                });
    }

    @Override
    protected <T> Flux<SubscribeResp<T>> doSubscribe(String appId, String group, String label, List<String> keys, Map<String, String> metadata, TypeRef<T> type) {
        //todo:need to get the specific env from system properties
        String applicationName = appId + "_FAT";

        String configurationName = keys.get(0);

        return Flux.create(fluxSink -> {
            AwsCapaConfigurationScheduler.INSTANCE.configSubscribePollingScheduler
                    .schedulePeriodically(() -> {
                        String version = getCurVersion(applicationName, configurationName);

                        GetConfigurationRequest request = GetConfigurationRequest.builder()
                                .application(applicationName)
                                .clientId(UUID.randomUUID().toString())
                                .configuration(configurationName)
                                .clientConfigurationVersion(version)
                                .environment(DEFAULT_ENV)
                                .build();

                        GetConfigurationResponse resp = null;
                        try {
                            resp = appConfigAsyncClient.getConfiguration(request).get();
                        } catch (InterruptedException | ExecutionException e) {
                            LOGGER.error("error occurs when getConfiguration,configurationName:{},version:{}", request.configuration(), request.clientConfigurationVersion(), e);
                        }
                        if (resp != null && !Objects.equals(resp.configurationVersion(), version)) {
                            /*
                            the reason why not use publisher scheduler to update configuration item is that switch thread needs time,
                            when the polling frequency is high,the second polling request may happens before the first request has been
                            update successfully by publisher thread. In that case,the subscriber may receive several signals for one actual
                            change event.
                             */
                            ConfigurationItem<T> configurationItem = updateConfigurationItem(applicationName, configurationName, type, resp.content(), resp.configurationVersion());
                            SubscribeResp<T> subscribeResp = convertToSubscribeResp(configurationItem, appId);
                            fluxSink.next(subscribeResp);
                        }
                    }, 0, 1, TimeUnit.SECONDS);
        });
    }

    private <T> SubscribeResp<T> convertToSubscribeResp(ConfigurationItem<T> item, String appId) {
        SubscribeResp<T> resp = new SubscribeResp<>();
        resp.setStoreName(AWS_APP_CONFIG_NAME);
        resp.setAppId(appId);
        resp.setItems(Lists.newArrayList(item));
        return resp;
    }

    @Override
    public String stopSubscribe() {
        AwsCapaConfigurationScheduler.INSTANCE.configSubscribePollingScheduler.dispose();
        return "success";
    }

    @Override
    public void close() {
        //no need
    }

    /**
     * get current version
     * ps:version can be null
     *
     * @param applicationName
     * @param configurationName
     * @return current version
     */
    private String getCurVersion(String applicationName, String configurationName) {
        String version = null;
        ConcurrentHashMap<String, Configuration<?>> configVersionMap = versionMap.get(applicationName);
        if (configVersionMap != null && configVersionMap.containsKey(configurationName)) {
            version = configVersionMap.get(configurationName).getClientConfigurationVersion();
        }
        return version;
    }

    private ConfigurationItem<?> getCurConfigurationItem(String applicationName, String configuration) {
        ConfigurationItem<?> configurationItem = null;
        ConcurrentHashMap<String, Configuration<?>> configMap = versionMap.get(applicationName);
        if (configMap != null && configMap.containsKey(configuration)) {
            configurationItem = configMap.get(configuration).getConfigurationItem();
        }
        return configurationItem;
    }

    private synchronized <T> ConfigurationItem<T> updateConfigurationItem(String applicationName, String configurationName, TypeRef<T> type, SdkBytes contentSdkBytes, String version) {
        ConcurrentHashMap<String, Configuration<?>> configMap = versionMap.get(applicationName);
        if (configMap == null) {
            configMap = new ConcurrentHashMap<>();
            T content = serializerProcessor.deserialize(contentSdkBytes, type, configurationName);

            Configuration<T> configuration = new Configuration<>();
            configuration.setClientConfigurationVersion(version);

            ConfigurationItem<T> configurationItem = new ConfigurationItem<>();
            configurationItem.setKey(configurationName);
            configurationItem.setContent(content);
            configuration.setConfigurationItem(configurationItem);

            configMap.put(configurationName, configuration);
            versionMap.put(applicationName, configMap);
            return configurationItem;
        } else {
            T content = serializerProcessor.deserialize(contentSdkBytes, type, configurationName);
            Configuration<T> configuration = new Configuration<>();
            configuration.setClientConfigurationVersion(version);

            ConfigurationItem<T> configurationItem = new ConfigurationItem<>();
            configurationItem.setKey(configurationName);
            configurationItem.setContent(content);
            configuration.setConfigurationItem(configurationItem);
            configMap.put(configurationName, configuration);
            return configurationItem;
        }
    }
}
