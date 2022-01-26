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

import java.util.Map;

/**
 * Checker of output level effective time.
 */
public class EffectiveTimeChecker implements CapaComponentLogConfiguration.ConfigChangedCallback {

    private static volatile long levelChangedStart = -1;

    private static boolean isChanged(Map<String, String> oldConfig, Map<String, String> newConfig) {
        return !oldConfig.getOrDefault(LogConfig.LevelConfig.OUTPUT_LEVEL.configKey, "")
                         .equals(newConfig.getOrDefault(LogConfig.LevelConfig.OUTPUT_LEVEL.configKey, ""));
    }

    public static boolean isOutputLevelEffective() {
        if (levelChangedStart > 0) {
            long end = levelChangedStart + LogConfig.TimeConfig.OUTPUT_LOG_EFFECTIVE_TIME.get();
            return end > System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public void onChange(Map<String, String> oldConfig, Map<String, String> newConfig) {
        boolean isLevelChanged = isChanged(oldConfig, newConfig);
        if (isLevelChanged) {
            levelChangedStart = System.currentTimeMillis();
        }
    }
}
