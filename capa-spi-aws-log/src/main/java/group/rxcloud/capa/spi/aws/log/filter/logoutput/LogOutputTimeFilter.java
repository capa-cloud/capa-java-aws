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
package group.rxcloud.capa.spi.aws.log.filter.logoutput;

import group.rxcloud.capa.spi.aws.log.configuration.CapaComponentLogConfiguration;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.LogOutputFilter;
import group.rxcloud.cloudruntimes.domain.core.configuration.SubConfigurationResp;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class LogOutputTimeFilter implements LogOutputFilter {
    private static final String OUTPUT_LOG_LEVEL_CONFIG_KEY = "outputLogLevel";
    private static final Integer DEFAULT_OUTPUT_LOG_EFFECTIVE_TIME = 30;
    private static final String OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY = "outputLogEffectiveTime";
    private static final CapaLogLevel DEFAULT_OUTPUT_LOG_LEVEL = CapaLogLevel.ERROR;
    private static final String DEFAULT_OUTPUT_LOG_LEVEL_NAME = "defaultOutputLogLevel";
    private final Optional<CapaComponentLogConfiguration> capaComponentLogConfiguration = Optional.ofNullable(CapaComponentLogConfiguration.getInstance());
    private volatile Long currentOutputLogValidTime;
    private volatile CapaLogLevel currentLogLevel = CapaLogLevel.ERROR;
    private volatile Integer currentOutputLogLevelEffectiveMin;


    public LogOutputTimeFilter() {
        Consumer<SubConfigurationResp<Map>> capaComponentLogConfigurationConsumer = resp -> {
            Map<String, String> config = new HashMap<>();
            if (!resp.getItems().isEmpty()) {
                config = resp.getItems().get(0).getContent();
            }
            // update current output log level valid time
            if (config.isEmpty()) {
                this.currentLogLevel = CapaLogLevel.ALL;
            }
            if (!config.isEmpty() && config.containsKey(OUTPUT_LOG_LEVEL_CONFIG_KEY)) {
                CapaLogLevel.toCapaLogLevel(config.get(OUTPUT_LOG_LEVEL_CONFIG_KEY))
                        .ifPresent(outputLogLevel -> {
                            if (!this.currentLogLevel.equals(outputLogLevel)) {
                                this.currentLogLevel = outputLogLevel;
                                this.updateOutputLogValidTime();
                            }
                        });
            }
            // update output log level and the valid time
            if (!config.isEmpty() && config.containsKey(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)) {
                Optional.ofNullable(Integer.parseInt(config.get(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)))
                        .ifPresent(outputLogEffectiveTime -> {
                            if (!this.currentOutputLogLevelEffectiveMin.equals(outputLogEffectiveTime)) {
                                this.currentOutputLogLevelEffectiveMin = outputLogEffectiveTime;
                                this.updateOutputLogValidTime();
                            }
                        });
            }
        };
        capaComponentLogConfiguration.ifPresent(capaComponentLogConfiguration -> {
            capaComponentLogConfiguration.registerConfigCallback(capaComponentLogConfigurationConsumer);
        });
        this.updateOutputLogValidTime();
    }

    private void updateOutputLogValidTime() {
        this.currentOutputLogLevelEffectiveMin = DEFAULT_OUTPUT_LOG_EFFECTIVE_TIME;
        if (CapaComponentLogConfiguration.getInstance() != null
                && CapaComponentLogConfiguration.getInstance().containsKey(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)) {
            this.currentOutputLogLevelEffectiveMin = Integer.parseInt(CapaComponentLogConfiguration.getInstance().get(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY));
        }

        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.MINUTE, this.currentOutputLogLevelEffectiveMin);
        currentOutputLogValidTime = instance.getTimeInMillis();
    }

    @Override
    public boolean logCanOutput(CapaLogLevel outputLogLevel) {
        // Check whether the output log level is higher than or equal to the log output level configured by capa-log, and return true if it is higher than or equal to.
        if (capaComponentLogConfiguration.isPresent()
                && capaComponentLogConfiguration.get().containsKey(DEFAULT_OUTPUT_LOG_LEVEL_NAME)) {
            Optional<CapaLogLevel> capaLogLevel = CapaLogLevel.toCapaLogLevel(capaComponentLogConfiguration.get().get(DEFAULT_OUTPUT_LOG_LEVEL_NAME));
            if (!capaLogLevel.isPresent()) {
                capaLogLevel = Optional.of(DEFAULT_OUTPUT_LOG_LEVEL);
            }
            if (outputLogLevel.getLevel() >= capaLogLevel.get().getLevel()) {
                return true;
            }
        }
        return System.currentTimeMillis() < currentOutputLogValidTime;
    }
}
