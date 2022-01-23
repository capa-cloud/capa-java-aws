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
package group.rxcloud.capa.spi.aws.log.manager;

import group.rxcloud.capa.spi.aws.log.configuration.LogConfiguration;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;

import java.util.Optional;

/**
 * LogManager, to manage log output levels.
 */
public class LogManager {

    /**
     * Dynamically adjust the log level switch name.
     */
    private static final String LOG_SWITCH_NAME = "logSwitch";
    /**
     * Output log level name.
     */
    private static final String OUTPUT_LOG_LEVEL_NAME = "outputLogLevel";

    /**
     * If the configured log level false, then it returns false and no log is output.
     * <p>
     * If configured logSwitch is not empty and true or logSwitch is empty, then process the output log level judgment logic.
     * <p>
     * If the configured output log level is judged to be empty, then can output the log and return true.
     * <p>
     * If the configured output log level is not empty, but it is not a standard log level, you can output and return true.
     * <p>
     * If the configured output log level is not empty and the log level is standard,
     * then need to compare whether the actual output log level is higher than or equal to the configured log level.
     * If it is higher than or equal to the configured log level, it can be output and return true, otherwise it cannot be output,returns false.
     *
     * @param capaLogLevel
     * @return
     */
    public static Boolean logsCanOutput(CapaLogLevel capaLogLevel) {
        if (LogConfiguration.containsKey(LOG_SWITCH_NAME)
                && String.valueOf(Boolean.FALSE).equalsIgnoreCase(LogConfiguration.get(LOG_SWITCH_NAME))) {
            return Boolean.FALSE;
        }
        if (LogConfiguration.containsKey(OUTPUT_LOG_LEVEL_NAME)) {
            Optional<CapaLogLevel> outputLogLevel = CapaLogLevel.toCapaLogLevel(LogConfiguration.get(OUTPUT_LOG_LEVEL_NAME));
            if (outputLogLevel.isPresent()) {
                return capaLogLevel.getLevel() >= outputLogLevel.get().getLevel();
            }
        }
        return Boolean.TRUE;
    }
}
