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

    @Override
    protected <T> Mono<List<ConfigurationItem<T>>> doGet(String appId, String group, String label, List<String> keys, Map<String, String> metadata, TypeRef<T> type) {
        List<ConfigurationItem<T>> items = new ArrayList<>();
        if (CollectionUtils.isNullOrEmpty(keys)) {
            return Mono.error(new IllegalArgumentException("keys is null or empty"));
        }
        // todo:need to get the specific env from system properties
        String applicationName = appId + "_FAT";
        String configurationName = keys.get(0);
        /*
            check whether the config file has been initialized before
            the function of if here is to first check init status to rough filter
         */
        if (!isInitialized(applicationName, configurationName)) {
            /*
            return Configuration.EMPTY if has been initialized before by others,
            or return configuration which initialized just now.
             */
            Configuration<T> initConfiguration = initConfig(applicationName, configurationName, group, label, metadata, type);
            if (!Objects.equals(initConfiguration, Configuration.EMPTY)) {
                //init just now and the config value is the latest value,return immediately
                return Mono.just(Lists.newArrayList(initConfiguration.getConfigurationItem()));
            }
        }

        //has been initialized before, and need to get the latest value
        String clientConfigurationVersion = getCurVersion(applicationName, configurationName);

        GetConfigurationRequest request = GetConfigurationRequest.builder()
                .application(applicationName)
                .clientId(UUID.randomUUID().toString())
                .configuration(configurationName)
                .clientConfigurationVersion(clientConfigurationVersion)
                .environment(AwsCapaConfigurationProperties.AppConfigProperties.Settings.getConfigAwsAppConfigEnv())
                .build();

        return Mono.fromFuture(() -> appConfigAsyncClient.getConfiguration(request))
                .publishOn(AwsCapaConfigurationScheduler.INSTANCE.configPublisherScheduler)
                .map(resp -> {
                    // if version doesn't change, get from versionMap
                    if (Objects.equals(clientConfigurationVersion, resp.configurationVersion())) {
                        items.add((ConfigurationItem<T>) getCurConfigurationItem(applicationName, configurationName));
                    } else {
                        // if version changes,update versionMap and return
                        Configuration<T> tConfiguration = updateConfigurationItem(applicationName, configurationName, type, resp.content(), resp.configurationVersion());
                        if (tConfiguration != null) {
                            items.add(tConfiguration.getConfigurationItem());
                        }
                    }
                    return items;
                });
    }

    @Override
    protected <T> Flux<SubscribeResp<T>> doSubscribe(String appId, String group, String label, List<String> keys, Map<String, String> metadata, TypeRef<T> type) {
        // todo: need to get the specific env from system properties
        String applicationName = appId + "_FAT";
        String configurationName = keys.get(0);

        initSubscribe(applicationName, configurationName, group, label, metadata, type);
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
            return Configuration.EMPTY;
        }
        return Mono.create(monoSink -> {
                    AwsCapaConfigurationScheduler.INSTANCE.configInitScheduler
                            .schedule(() -> {
                                String version = getCurVersion(applicationName, configurationName);

                                GetConfigurationRequest request = GetConfigurationRequest.builder()
                                        .application(applicationName)
                                        .clientId(UUID.randomUUID().toString())
                                        .configuration(configurationName)
                                        .clientConfigurationVersion(version)
                                        .environment(AwsCapaConfigurationProperties.AppConfigProperties.Settings.getConfigAwsAppConfigEnv())
                                        .build();

                                GetConfigurationResponse resp = null;
                                try {
                                    resp = appConfigAsyncClient.getConfiguration(request).get();
                                } catch (InterruptedException | ExecutionException e) {
                                    LOGGER.error("error occurs when getConfiguration,configurationName:{},version:{}", request.configuration(), request.clientConfigurationVersion(), e);
                                }
                                if (resp != null && !Objects.equals(resp.configurationVersion(), version)) {
                                    Configuration<T> tConfiguration = initConfigurationItem(applicationName, configurationName, type, resp.content(), resp.configurationVersion());
                                    monoSink.success(tConfiguration);
                                }
                            });
                })
                .map(resp -> (Configuration<T>) resp)
                .block();
    }

    private <T> void initSubscribe(String applicationName, String configurationName, String group, String label, Map<String, String> metadata, TypeRef<T> type) {
        if (!isInitialized(applicationName, configurationName)) {
            initConfig(applicationName, configurationName, group, label, metadata, type);
        }
        if (!isSubscribed(applicationName, configurationName)) {
            createSubscribe(applicationName, configurationName, type);
        }
    }

    private synchronized <T> void createSubscribe(String applicationName, String configurationName, TypeRef<T> type) {
        if (isSubscribed(applicationName, configurationName)) {
            return;
        }
        Flux.create(fluxSink -> {
                    AwsCapaConfigurationScheduler.INSTANCE.configSubscribePollingScheduler
                            .schedulePeriodically(() -> {
                                String version = getCurVersion(applicationName, configurationName);

                                GetConfigurationRequest request = GetConfigurationRequest.builder()
                                        .application(applicationName)
                                        .clientId(UUID.randomUUID().toString())
                                        .configuration(configurationName)
                                        .clientConfigurationVersion(version)
                                        .environment(AwsCapaConfigurationProperties.AppConfigProperties.Settings.getConfigAwsAppConfigEnv())
                                        .build();

                                GetConfigurationResponse resp = null;
                                try {
                                    resp = appConfigAsyncClient.getConfiguration(request).get();
                                } catch (InterruptedException | ExecutionException e) {
                                    LOGGER.error("error occurs when getConfiguration,configurationName:{},version:{}", request.configuration(), request.clientConfigurationVersion(), e);
                                }
                                // update subscribed status if needs
                                getConfiguration(applicationName, configurationName).getSubscribed().compareAndSet(false, true);

                                if (resp != null && !Objects.equals(resp.configurationVersion(), version)) {
                                    fluxSink.next(resp);
                                }
                                // todo: make the polling frequency configurable
                            }, 0, 1, TimeUnit.SECONDS);
                })
                .publishOn(AwsCapaConfigurationScheduler.INSTANCE.configPublisherScheduler)
                .map(origin -> {
                    GetConfigurationResponse resp = (GetConfigurationResponse) origin;
                    Configuration configuration = updateConfigurationItem(applicationName, configurationName, type, resp.content(), resp.configurationVersion());
                    return configuration == null ? Configuration.EMPTY : configuration;
                })
                .filter(resp -> resp != Configuration.EMPTY)
                .subscribe(resp -> {
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
                        fluxSink.next(configurationItem);
                    });
                })
                .map(resp -> (ConfigurationItem<T>) resp)
                .map(resp -> convert(resp, appId));
    }

    private <T> SubscribeResp<T> convert(ConfigurationItem<T> conf, String appId) {
        SubscribeResp<T> subscribeResp = new SubscribeResp<>();
        subscribeResp.setItems(Lists.newArrayList(conf));
        subscribeResp.setAppId(appId);
        subscribeResp.setStoreName(AwsCapaConfigurationProperties.AppConfigProperties.Settings.getAwsAppConfigName());
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

    private ConfigurationItem<?> getCurConfigurationItem(String applicationName, String configuration) {
        ConfigurationItem<?> configurationItem = null;
        ConcurrentHashMap<String, Configuration<?>> configMap = versionMap.get(applicationName);
        if (configMap != null && configMap.containsKey(configuration)) {
            configurationItem = configMap.get(configuration).getConfigurationItem();
        }
        return configurationItem;
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

        if (initApplication) {
            versionMap.put(applicationName, configMap);
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
}
