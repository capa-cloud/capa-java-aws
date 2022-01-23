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

import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Log common configs.
 */
public interface LogConfig<T> {

    T get();

    enum BoolConfig implements LogConfig<Boolean> {
        LOG_SWITCH("logSwitch", true);

        String configKey;

        boolean defaultValue;

        BoolConfig(String configKey, boolean defaultValue) {
            this.configKey = configKey;
            this.defaultValue = defaultValue;
        }

        @Override
        public Boolean get() {
            Optional<CapaComponentLogConfiguration> configuration = CapaComponentLogConfiguration.getInstanceOpt();
            if (configuration.isPresent()) {
                String value = configuration.get().get(configKey);
                return value == null ? defaultValue : Boolean.valueOf(value);
            }
            return defaultValue;
        }
    }

    enum IntConfig implements LogConfig<Integer> {
        ALERT_LOG_COUNT("alertLogCount", 100);

        String configKey;

        int defaultValue;

        IntConfig(String configKey, int defaultValue) {
            this.configKey = configKey;
            this.defaultValue = defaultValue;
        }

        @Override
        public Integer get() {
            Optional<CapaComponentLogConfiguration> configuration = CapaComponentLogConfiguration.getInstanceOpt();
            if (configuration.isPresent()) {
                String value = configuration.get().get(configKey);
                return value == null ? defaultValue : Integer.parseInt(value);
            }
            return defaultValue;
        }
    }

    enum TimeConfig implements LogConfig<Long> {

        OUTPUT_LOG_EFFECTIVE_TIME("outputLogEffectiveTime", 30),
        ALERT_LOG_COUNT_TIME("alertLogCountMinutes", 5),
        ALERT_LOG_IGNORE_IGNORE_TIME("alertLogIgnoreMinutes", 60);

        String configKey;

        int defaultValue;

        TimeConfig(String configKey, int defaultValue) {
            this.configKey = configKey;
            this.defaultValue = defaultValue;
        }

        @Override
        public Long get() {
            int target = defaultValue;
            Optional<CapaComponentLogConfiguration> configuration = CapaComponentLogConfiguration.getInstanceOpt();
            if (configuration.isPresent()) {
                String value = configuration.get().get(configKey);
                if (value != null) {
                    target = Integer.parseInt(value);
                }
            }
            return TimeUnit.MINUTES.toMillis(target);
        }
    }

    enum LevelConfig implements LogConfig<CapaLogLevel> {

        OUTPUT_LEVEL("outputLogLevel", CapaLogLevel.ERROR),
        DEFAULT_OUT_PUT_LEVEL("defaultOutputLogLevel", CapaLogLevel.ERROR),
        ALERT_LOG_LEVEL("alertLogLevel", CapaLogLevel.ERROR);

        String configKey;

        CapaLogLevel defaultValue;

        LevelConfig(String configKey, CapaLogLevel defaultValue) {
            this.configKey = configKey;
            this.defaultValue = defaultValue;
        }

        public Optional<CapaLogLevel> getOpt() {
            Optional<CapaComponentLogConfiguration> configuration = CapaComponentLogConfiguration.getInstanceOpt();
            return configuration
                    .flatMap(logConfiguration -> CapaLogLevel.toCapaLogLevel(logConfiguration.get(configKey)));
        }

        @Override
        public CapaLogLevel get() {
            return getOpt().orElse(defaultValue);
        }
    }
}
