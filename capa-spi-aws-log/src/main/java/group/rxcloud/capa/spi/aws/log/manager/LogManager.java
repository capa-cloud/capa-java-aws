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
     * To judge whether the output can be output, several conditions need to be considered.
     * 1. Whether the global log output switch is on, if it is off, all logs are not output.
     * 2. The log output switch is on, and judge capa-component-log-configuration.properties (configuration file ConfigFileA)
     * The relationship between the file configuration output log level and the configuration log output level of capa-log-configuration.properties (ConfigFileB)
     * 2.1 The log output level of ConfigFileA is higher than or equal to that of ConfigFileB, and the log is output directly.
     * 2.2 The log output level of ConfigFileB is lower than the log output level of ConfigFileB,
     * and the Japanese-style output expiration time processing is performed.
     * 2.2.1 Calculate the log level expiration time according to the configuration log output time of ConfigFileB (there is no configuration to set a default expiration time, unit: minutes )
     * 2.2.2 If the current time is less than or equal to the expiration time, it can be output.
     * 2.2.3 If the current time is greater than the expiration time, it cannot be output.
     * 2.2.4 When the expiration time and log output level of ConfigFileB change, reset the log output level and expiration time.
     * 2.2.5 When the log output level of ConfigFileA changes, reset the log output expiration time.
     * 3 The expiration time field is local.
     * The output level is reset when the output level of the configuration file changes compared to the configuration file.
     * The default log output level is error. Since the info log needs to be enabled, the log output level is set to info.
     * 1.1. When the code obtains a change in the configured log level, the expiration time will be reset, and the error and the following ones less than the expiration time will be reset.
     * And the logs of info and above can be output, the logs below info are not output, and the logs below error greater than the expiration time are not output
     * 1.2 When the user configures an output level of info,
     * and then after a period of time, he wants to output the info level log ,
     * then you need to re-modify the configuration file,
     * first set the error level to error or warn,
     * and then change it back to info to take effect
     *
     * @param capaLogLevel
     * @return
     */
    public static boolean logsCanOutput(CapaLogLevel capaLogLevel) {
        // Determine whether the log output switch is turned on, if it is turned off, the log will not be output.
        if (LogConfigurationManager.containsKey(LOG_SWITCH_NAME)
                && String.valueOf(Boolean.FALSE).equalsIgnoreCase(LogConfigurationManager.get(LOG_SWITCH_NAME))) {
            return false;
        }
        // Whether the log level is higher than or equal to the log output level configured by the application.
        if (LogConfigurationManager.containsKey(OUTPUT_LOG_LEVEL_NAME)) {
            Optional<CapaLogLevel> outputLogLevel = CapaLogLevel.toCapaLogLevel(LogConfigurationManager.get(OUTPUT_LOG_LEVEL_NAME));
            if (outputLogLevel.isPresent()) {
                return logLevelCanOutput(capaLogLevel, outputLogLevel.get());
            }
        }
        return true;
    }

    private static boolean logLevelCanOutput(CapaLogLevel capaLogLevel, CapaLogLevel outputLogLevel) {
        // 1. Check whether the output log level is higher than or equal to the number of log output levels configured by the application.
        // If it is lower than the configuration, return false directly.
        if (capaLogLevel.getLevel() < outputLogLevel.getLevel()) {
            return false;
        }
        // 2. Check whether the output log level is higher than or equal to the log output level configured by capa-log, and return true if it is higher than or equal to.
        if (capaLogLevel.getLevel() >= LogConfigurationManager.getCustomOutputLogLevel().getLevel()) {
            return true;
        }
        // 3. capaLogLevel>= the log output level configured by the application and capaLogLevel < the log output level configured by capa-log, judged according to the expiration time
        return System.currentTimeMillis() < LogConfigurationManager.getLogOutputValidTime();
    }
}
