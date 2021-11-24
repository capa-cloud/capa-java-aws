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
package group.rxcloud.capa.spi.aws.config.entity;

import group.rxcloud.capa.component.configstore.ConfigurationItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Reckless Xu
 */
public class Configuration<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    public static final Configuration<Void> EMPTY = new Configuration<>();

    private String clientConfigurationVersion;

    private ConfigurationItem<T> configurationItem;

    public final Object lock = new Object();

    private final CopyOnWriteArraySet<ConfigurationListener<T>> listeners = new CopyOnWriteArraySet<>();

    private AtomicBoolean initialized = new AtomicBoolean(false);

    private AtomicBoolean subscribed = new AtomicBoolean(false);

    public synchronized void addListener(ConfigurationListener<T> listener) {
        if (initialized.get()) {
            trigger(listener, configurationItem);
        }
        listeners.add(listener);
    }

    public boolean triggers(ConfigurationItem<T> data) {
        boolean result = true;
        for (ConfigurationListener<T> listener : listeners) {
            if (!trigger(listener, data)) {
                result = false;
            }
        }
        return result;
    }

    private boolean trigger(ConfigurationListener<T> listener, ConfigurationItem<T> data) {
        try {
            listener.onLoad(data);
            return true;
        } catch (Exception e) {
            LOGGER.error("listener onLoad error,fileName:{},", data.getKey(), e);
            return false;
        }
    }

    public String getClientConfigurationVersion() {
        return clientConfigurationVersion;
    }

    public void setClientConfigurationVersion(String clientConfigurationVersion) {
        this.clientConfigurationVersion = clientConfigurationVersion;
    }

    public ConfigurationItem<T> getConfigurationItem() {
        return configurationItem;
    }

    public void setConfigurationItem(ConfigurationItem<T> configurationItem) {
        this.configurationItem = configurationItem;
    }

    public AtomicBoolean getInitialized() {
        return initialized;
    }

    public AtomicBoolean getSubscribed() {
        return subscribed;
    }
}
