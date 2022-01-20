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

import java.util.Optional;

public class LogOutputLevelFilter implements LogOutputFilter {
    private static final String OUTPUT_LOG_LEVEL_NAME = "outputLogLevel";
    private final Optional<CapaComponentLogConfiguration> capaComponentLogConfiguration = Optional.ofNullable(CapaComponentLogConfiguration.getInstance());

    public LogOutputLevelFilter() {
    }

    @Override
    public boolean logCanOutput(CapaLogLevel outputLogLevel) {
        // 1. Check whether the output log level is higher than or equal to the number of log output levels configured by the application.
        // If it is lower than the configuration, return false directly.
        // Whether the log level is higher than or equal to the log output level configured by the application.
        if (capaComponentLogConfiguration.isPresent()
                && capaComponentLogConfiguration.get().containsKey(OUTPUT_LOG_LEVEL_NAME)) {
            Optional<CapaLogLevel> capaLogLevel = CapaLogLevel.toCapaLogLevel(capaComponentLogConfiguration.get().get(OUTPUT_LOG_LEVEL_NAME));
            if (capaLogLevel.isPresent()
                    && outputLogLevel.getLevel() < capaLogLevel.get().getLevel()) {
                return false;
            }
        }

        return true;
    }
}
