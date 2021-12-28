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
import group.rxcloud.capa.addons.foundation.CapaFoundation;
import group.rxcloud.capa.addons.foundation.FoundationType;
import group.rxcloud.capa.component.CapaConfigurationProperties;
import group.rxcloud.capa.component.configstore.ConfigurationItem;
import group.rxcloud.capa.component.configstore.StoreConfig;
import group.rxcloud.capa.component.configstore.SubscribeResp;
import group.rxcloud.capa.infrastructure.exceptions.CapaErrorContext;
import group.rxcloud.capa.infrastructure.exceptions.CapaException;
import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.capa.spi.aws.config.entity.Configuration;
import group.rxcloud.capa.spi.aws.config.scheduler.AwsCapaConfigurationScheduler;
import group.rxcloud.capa.spi.aws.config.serializer.SerializerProcessor;
import group.rxcloud.capa.spi.configstore.CapaConfigStoreSpi;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfig.AppConfigAsyncClient;
import software.amazon.awssdk.services.appconfig.model.BadRequestException;
import software.amazon.awssdk.services.appconfig.model.GetConfigurationRequest;
import software.amazon.awssdk.services.appconfig.model.GetConfigurationResponse;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * @author Reckless Xu
 */
public class AwsCapaConfigStore extends CapaConfigStoreSpi {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsCapaConfigStore.class);

    /**
     * key of versionMap--applicationName,format:appid_ENV,e.g:"12345_FAT"
     * value of versionMap--configurationMap of this application,which contains all the configuration info for this application
     * <p>
     * key of configurationMap--configurationName;
     * value of configurationMap--configuration content and version of this configuration
     * <p>
     * ps:currentHashMap may not be necessary, as update in synchronized method
     */
    private static final Map<String, ConcurrentHashMap<String, Configuration<?>>> versionMap;

    static {
        versionMap = new ConcurrentHashMap<>();
    }

    private final CapaObjectSerializer objectSerializer;

    private SerializerProcessor serializerProcessor;

    private AppConfigAsyncClient appConfigAsyncClient;

    private static final String APPCONFIG_NAME_FORMAT = "%s_%s";

    private static final Long REQUEST_TIMEOUT_IN_SECONDS = AwsCapaConfigurationProperties.AppConfigProperties.Settings.getRequestTimeoutInSeconds();

    /**
     * Instantiates a new Capa configuration.
     *
     * @param objectSerializer Serializer for transient request/response objects.
     */
    public AwsCapaConfigStore(CapaObjectSerializer objectSerializer) {
        super(objectSerializer);
        this.objectSerializer = objectSerializer;
    }

    @Override
    protected void doInit(StoreConfig storeConfig) {
        appConfigAsyncClient = AppConfigAsyncClient.create();
        serializerProcessor = new SerializerProcessor(objectSerializer);
    }

    @Override
    public String stopSubscribe() {
        AwsCapaConfigurationScheduler.INSTANCE.configSubscribePollingScheduler.dispose();
        return "success";
    }

    @Override
    public void close() {
        // no need
    }

    /**
     * direct get current config from server
     *
     * @param appId    appId
     * @param group    group
     * @param label    label
     * @param keys     configurationName list
     * @param metadata metadata
     * @param type     type
     * @param <T>      T
     * @return config value or throw exception if cannot get config in specific time.
     * Exception contains e.g. {@link BadRequestException},{@link TimeoutException} and so on.
     */
    @Override
    protected <T> Mono<List<ConfigurationItem<T>>> doGet(String appId, String group, String label, List<String> keys, Map<String, String> metadata, TypeRef<T> type) {
        if (CollectionUtils.isNullOrEmpty(keys)) {
            LOGGER.warn("[[type=Capa.Config]] keys is null or empty,appId:{}", appId);
            return Mono.error(new CapaException(CapaErrorContext.PARAMETER_ERROR, "keys is null or empty"));
        }

        String applicationName = String.format(APPCONFIG_NAME_FORMAT, appId, CapaFoundation.getEnv(FoundationType.TRIP));
        String configurationName = keys.get(0);

        //init config and create subscribe polling
        initAndSubscribe(applicationName, configurationName, group, label, metadata, type);

        //get current config from local
        ConfigurationItem<T> configurationItem = (ConfigurationItem<T>) getConfiguration(applicationName, configurationName).getConfigurationItem();
        return Mono.just(Lists.newArrayList(configurationItem));
    }

    @Override
    protected <T> Flux<SubscribeResp<T>> doSubscribe(String appId, String group, String label, List<String> keys, Map<String, String> metadata, TypeRef<T> type) {

        String applicationName = String.format(APPCONFIG_NAME_FORMAT, appId, CapaFoundation.getEnv(FoundationType.TRIP));
        String configurationName = keys.get(0);

        initAndSubscribe(applicationName, configurationName, group, label, metadata, type);
        return doSub(applicationName, configurationName, group, label, metadata, type, appId);
    }

    /**
     * @param applicationName   applicationName
     * @param configurationName configurationName
     * @param group             group
     * @param label             label
     * @param metadata          metadata
     * @param type              type
     * @param <T>               T
     * @return return Configuration.EMPTY if has been initialized before by others and not been done this time;
     * or return configuration value which initialized just now.
     */
    private synchronized <T> Configuration<T> initConfig(String applicationName, String configurationName, String group, String label, Map<String, String> metadata, TypeRef<T> type) {
        // double check whether has been initialized
        if (isInitialized(applicationName, configurationName)) {
            LOGGER.info("[[type=Capa.Config.initConfig]] config has been initialized before,applicationName:{},configurationName:{}", applicationName, configurationName);
            return Configuration.EMPTY;
        }
        String version = getCurVersion(applicationName, configurationName);

        GetConfigurationRequest request = GetConfigurationRequest.builder()
                .application(applicationName)
                .clientId(UUID.randomUUID().toString())
                .configuration(configurationName)
                .clientConfigurationVersion(version)
                .environment(AwsCapaConfigurationProperties.AppConfigProperties.Settings.getConfigAwsAppConfigEnv())
                .build();

        LOGGER.debug("[[type=Capa.Config.initConfig]] call getconfiguration in init process,request:{}", request);

        return Mono.fromCallable(() -> {
            GetConfigurationResponse response = appConfigAsyncClient.getConfiguration(request).get(REQUEST_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            LOGGER.debug("[[type=Capa.Config.initConfig]] call getconfiguration in init process,response:{}", response);
            return response;
        })
                .doOnError(e -> LOGGER.error("[[type=Capa.Config.initConfig]] error occurs when getconfiguration in init process, request:{}", request, e))
                .map(resp -> initConfigurationItem(applicationName, configurationName, type, resp.content(), resp.configurationVersion()))
                .block();
    }

    private <T> void initAndSubscribe(String applicationName, String configurationName, String group, String label, Map<String, String> metadata, TypeRef<T> type) {
        if (!isInitialized(applicationName, configurationName)) {
            initConfig(applicationName, configurationName, group, label, metadata, type);
        }
        if (!isSubscribed(applicationName, configurationName)) {
            createSubscribePolling(applicationName, configurationName, type);
        }
    }

    private synchronized <T> void createSubscribePolling(String applicationName, String configurationName, TypeRef<T> type) {
        if (isSubscribed(applicationName, configurationName)) {
            return;
        }
        Flux.create(fluxSink -> {
            AwsCapaConfigurationScheduler.INSTANCE.configSubscribePollingScheduler
                    .schedulePeriodically(() -> {

                        // update subscribed status if needs
                        getConfiguration(applicationName, configurationName).getSubscribed().compareAndSet(false, true);

                        String version = getCurVersion(applicationName, configurationName);

                        GetConfigurationRequest request = GetConfigurationRequest.builder()
                                .application(applicationName)
                                .clientId(UUID.randomUUID().toString())
                                .configuration(configurationName)
                                .clientConfigurationVersion(version)
                                .environment(AwsCapaConfigurationProperties.AppConfigProperties.Settings.getConfigAwsAppConfigEnv())
                                .build();
                        LOGGER.debug("[[type=Capa.Config.subscribePolling]] subscribe polling task start,request:{}", request);

                        GetConfigurationResponse resp = null;
                        try {
                            resp = appConfigAsyncClient.getConfiguration(request).get(REQUEST_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            //catch error,log error and not trigger listeners
                            LOGGER.error("[[type=Capa.Config.subscribePolling]] error occurs when getConfiguration in polling process,configurationName:{},version:{}", request.configuration(), request.clientConfigurationVersion(), e);
                        }

                        LOGGER.debug("[[type=Capa.Config.subscribePolling]] subscribe polling task end,response:{}", resp);

                        if (resp != null && !Objects.equals(resp.configurationVersion(), version)) {
                            fluxSink.next(resp);
                        }
                        // todo: make the polling frequency configurable
                    }, 0, 5, TimeUnit.SECONDS);
        })
                .publishOn(AwsCapaConfigurationScheduler.INSTANCE.configPublisherScheduler)
                .map(origin -> {
                    GetConfigurationResponse resp = (GetConfigurationResponse) origin;
                    Configuration<T> configuration = updateConfigurationItem(applicationName, configurationName, type, resp.content(), resp.configurationVersion());
                    return configuration == null ? Configuration.EMPTY : configuration;
                })
                .filter(resp -> resp != Configuration.EMPTY)
                .subscribe(resp -> {
                    LOGGER.info("[[type=Capa.Config.triggerListener]] receive changes and trigger listeners,response:{}", resp);
                    resp.triggers(resp.getConfigurationItem());
                });
    }

    private <T> Flux<SubscribeResp<T>> doSub(String applicationName, String configurationName, String group, String label, Map<String, String> metadata, TypeRef<T> type, String appId) {
        Configuration<?> configuration = getConfiguration(applicationName, configurationName);
        if (Objects.equals(configuration, Configuration.EMPTY)) {
            return Flux.empty();
        }
        return Flux
                .create(fluxSink -> {
                    configuration.addListener(configurationItem -> {
                        LOGGER.info("[[type=Capa.Config.listenerOnChange]] listener onChanges, configurationItem key:{}", configurationItem.getKey());
                        fluxSink.next(configurationItem);
                    });
                })
                .map(resp -> (ConfigurationItem<T>) resp)
                .map(resp -> convertToSubscribeResp(resp, appId));
    }

    private <T> SubscribeResp<T> convertToSubscribeResp(ConfigurationItem<T> conf, String appId) {
        SubscribeResp<T> subscribeResp = new SubscribeResp<>();
        subscribeResp.setItems(Lists.newArrayList(conf));
        subscribeResp.setAppId(appId);
        subscribeResp.setStoreName(CapaConfigurationProperties.Settings.getStoreNames().get(0));
        return subscribeResp;
    }

    /**
     * get current version
     * ps:version can be null
     *
     * @param applicationName   applicationName
     * @param configurationName configurationName
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

    /**
     * @param applicationName   applicationName
     * @param configurationName configurationName
     * @param type              type of content
     * @param contentSdkBytes   content value with SdkBytes type
     * @param version           new version
     * @param <T>               T
     * @return return the new value or null if not actually update
     */
    private <T> Configuration<T> updateConfigurationItem(String applicationName, String configurationName, TypeRef<T> type, SdkBytes contentSdkBytes, String version) {
        ConcurrentHashMap<String, Configuration<?>> configMap = versionMap.get(applicationName);

        // in fact,configMap.get(configurationName) is always not null, as it has been initialized in initialization process
        Configuration<T> configuration = (Configuration<T>) configMap.get(configurationName);

        synchronized (configuration.lock) {
            // check whether content has been updated by other thread
            if (configMap.containsKey(configurationName) && Objects.equals(configMap.get(configurationName).getClientConfigurationVersion(), version)) {
                return null;
            }
            // do need to update
            T content = serializerProcessor.deserialize(contentSdkBytes, type, configurationName);
            configuration.setClientConfigurationVersion(version);

            ConfigurationItem<T> configurationItem = Optional.ofNullable(configuration.getConfigurationItem()).orElse(new ConfigurationItem<>());
            configurationItem.setContent(content);
            configuration.setConfigurationItem(configurationItem);

            configMap.put(configurationName, configuration);
            LOGGER.info("[[type=Capa.Config.updateConfig]] update config,key configurationName:{},value configuration:{}", configurationName, configuration);
            return configuration;
        }
    }

    private <T> Configuration<T> initConfigurationItem(String applicationName, String configurationName, TypeRef<T> type, SdkBytes contentSdkBytes, String version) {
        ConcurrentHashMap<String, Configuration<?>> configMap = versionMap.get(applicationName);
        boolean initApplication = false;
        if (configMap == null) {
            configMap = new ConcurrentHashMap<>();
            initApplication = true;
        }

        Configuration<T> configuration = new Configuration<>();
        configuration.setClientConfigurationVersion(version);
        configuration.getInitialized().compareAndSet(false, true);

        ConfigurationItem<T> configurationItem = new ConfigurationItem<>();
        configurationItem.setKey(configurationName);
        T content = serializerProcessor.deserialize(contentSdkBytes, type, configurationName);
        configurationItem.setContent(content);
        configuration.setConfigurationItem(configurationItem);

        configMap.put(configurationName, configuration);

        LOGGER.info("[[type=Capa.Config.initConfig]] process initConfigurationItem,put key configurationName:{},value configuration:{}", configurationName, configuration);

        if (initApplication) {
            versionMap.put(applicationName, configMap);
            LOGGER.info("[[type=Capa.Config.initConfig]] process initConfigurationItem,put key applicationName:{}", applicationName);
        }
        return configuration;
    }

    private boolean isInitialized(String applicationName, String configurationName) {
        ConcurrentHashMap<String, Configuration<?>> configMap = versionMap.get(applicationName);
        return configMap != null && configMap.containsKey(configurationName) && configMap.get(configurationName).getInitialized().get();
    }

    private boolean isSubscribed(String applicationName, String configurationName) {
        ConcurrentHashMap<String, Configuration<?>> configMap = versionMap.get(applicationName);
        return configMap != null && configMap.containsKey(configurationName) && configMap.get(configurationName).getInitialized().get() && configMap.get(configurationName).getSubscribed().get();
    }

    private Configuration<?> getConfiguration(String applicationName, String configurationName) {
        ConcurrentHashMap<String, Configuration<?>> configMap = versionMap.get(applicationName);
        if (configMap != null && configMap.containsKey(configurationName)) {
            return configMap.get(configurationName);
        }
        return Configuration.EMPTY;
    }

    private <T> List<ConfigurationItem<T>> convertToConfigurationList(GetConfigurationResponse response, String configurationName, TypeRef<T> type) {
        List<ConfigurationItem<T>> list = new ArrayList<>();
        if (response == null) {
            return list;
        }
        ConfigurationItem<T> configurationItem = new ConfigurationItem<>();
        configurationItem.setKey(configurationName);

        T content = serializerProcessor.deserialize(response.content(), type, configurationName);
        configurationItem.setContent(content);

        list.add(configurationItem);
        return list;
    }
}
