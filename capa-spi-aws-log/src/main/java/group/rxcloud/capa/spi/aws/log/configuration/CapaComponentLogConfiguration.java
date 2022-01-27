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
package group.rxcloud.capa.spi.aws.log.configuration;

import group.rxcloud.capa.component.CapaLogProperties;
import group.rxcloud.capa.infrastructure.hook.ConfigurationHooks;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.cloudruntimes.domain.core.configuration.SubConfigurationResp;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class CapaComponentLogConfiguration {

    private static final String CAPA_COMPONENT_LOG_CONFIGURATION_FILE_NAME = "capa-component-log-configuration.properties";

    private static final AtomicBoolean INIT = new AtomicBoolean(false);

    private static final Object lock = new Object();

    private static Optional<CapaComponentLogConfiguration> instance = Optional.empty();

    private final Logger log = LoggerFactory.getLogger(CapaComponentLogConfiguration.class);

    private final AtomicReferenceArray<Map> capaComponentLogConfigurationProperties;

    private final List<ConfigChangedCallback> callbackList;

    private volatile Map<String, String> capaComponentLogConfiguration;

    private CapaComponentLogConfiguration() {
        capaComponentLogConfigurationProperties = new AtomicReferenceArray<>(2);
        capaComponentLogConfiguration = new HashMap<>();
        callbackList = new ArrayList<>();
        callbackList.add(new EffectiveTimeChecker());
        Mixer.configurationHooksNullable().ifPresent(hooks -> {
            // subscribe capa-compoment-log-configuration.properties
            List<String> appIds = new ArrayList<>(2);
            appIds.add(hooks.defaultConfigurationAppId());
            appIds.add(CapaLogProperties.Settings.getCenterConfigAppId());
            for (int i = 0; i < appIds.size(); i++) {
                try {
                    subscribeCapaComponentLogConfigurationByAppId(hooks, appIds.get(i), i);
                } catch (Throwable throwable) {
                    log.warn("Fail to subscribe config for app id " + appIds.get(i) + ", index " + i, throwable);
                    capaComponentLogConfigurationProperties.set(i, null);
                }
            }
        });
    }


    public static Optional<CapaComponentLogConfiguration> getInstanceOpt() {
        if (INIT.get()) {
            return instance;
        }

        synchronized (lock) {
            if (INIT.compareAndSet(false, true)) {
                instance = Optional.of(new CapaComponentLogConfiguration());
            }
        }
        return instance;
    }

    public boolean containsKey(String key) {
        return capaComponentLogConfiguration.containsKey(key);
    }

    public String get(String key) {
        return capaComponentLogConfiguration.get(key);
    }

    private void subscribeCapaComponentLogConfigurationByAppId(ConfigurationHooks configurationHooks, String appId,
                                                               int index) {
        String storeName = configurationHooks.registryStoreNames().get(0);

        Flux<SubConfigurationResp<Map>> configFlux = configurationHooks.subscribeConfiguration(
                storeName,
                appId,
                Collections.singletonList(CAPA_COMPONENT_LOG_CONFIGURATION_FILE_NAME),
                null,
                "",
                "",
                TypeRef.get(Map.class));

        configFlux.subscribe(resp -> {
            synchronized (lock) {
                if (!resp.getItems().isEmpty()) {
                    capaComponentLogConfigurationProperties.set(index, resp.getItems().get(0).getContent());
                } else {
                    capaComponentLogConfigurationProperties.set(index, null);
                }

                Map<String, String> newConfig = new HashMap<>();
                for (int i = 0; i < capaComponentLogConfigurationProperties.length(); i++) {
                    Map item = capaComponentLogConfigurationProperties.get(i);
                    if (item != null) {
                        item.forEach((k, v) -> newConfig.putIfAbsent(String.valueOf(k), String.valueOf(v)));
                    }
                }

                Map<String, String> oldConfig = capaComponentLogConfiguration;
                capaComponentLogConfiguration = newConfig;

                for (ConfigChangedCallback callback : callbackList) {
                    callback.onChange(oldConfig, newConfig);
                }
            }
        });
    }

    @FunctionalInterface
    interface ConfigChangedCallback {
        void onChange(Map<String, String> oldConfig, Map<String, String> newConfig);
    }
}
