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

import group.rxcloud.capa.spi.aws.log.appender.CapaLogEvent;
import group.rxcloud.capa.spi.aws.log.configuration.EffectiveTimeChecker;
import group.rxcloud.capa.spi.aws.log.configuration.LogConfig;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.LogOutputFilter;

import java.util.Optional;

public class LogOutputLevelFilter implements LogOutputFilter {

    @Override
    public boolean logCanOutput(CapaLogEvent event) {
        // Check whether the output log level is higher than or equal to the log output level configured by capa-log, and return true if it is higher than or equal to.
        Optional<CapaLogLevel> currentLevelOpt = event.getCapaLogLevel();
        if (!currentLevelOpt.isPresent()) {
            return true;
        }

        CapaLogLevel currentLevel = currentLevelOpt.get();
        Optional<CapaLogLevel> outputLevel = LogConfig.LevelConfig.OUTPUT_LEVEL.getOpt();
        // if the output level is effective, skip the default level check.
        if (outputLevel.isPresent() && EffectiveTimeChecker.isOutputLevelEffective()) {
            return currentLevel.getLevel() >= outputLevel.get().getLevel();
        }

        CapaLogLevel defaultOutputLevel = LogConfig.LevelConfig.DEFAULT_OUT_PUT_LEVEL.get();
        return currentLevel.getLevel() >= defaultOutputLevel.getLevel();
    }
}
