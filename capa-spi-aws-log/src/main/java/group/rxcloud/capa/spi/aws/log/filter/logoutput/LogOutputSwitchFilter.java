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

public class LogOutputSwitchFilter implements LogOutputFilter {
    /**
     * Dynamically adjust the log level switch name.
     */
    private static final String LOG_SWITCH_NAME = "logSwitch";

    private final Optional<CapaComponentLogConfiguration> capaComponentLogConfiguration = Optional.ofNullable(CapaComponentLogConfiguration.getInstance());

    @Override
    public boolean logCanOutput(CapaLogLevel level) {
        // Determine whether the log output switch is turned on, if it is turned off, the log will not be output.
        return !capaComponentLogConfiguration.isPresent()
                || !capaComponentLogConfiguration.get().containsKey(LOG_SWITCH_NAME)
                || !String.valueOf(Boolean.FALSE).equalsIgnoreCase(capaComponentLogConfiguration.get().get(LOG_SWITCH_NAME));
    }
}
