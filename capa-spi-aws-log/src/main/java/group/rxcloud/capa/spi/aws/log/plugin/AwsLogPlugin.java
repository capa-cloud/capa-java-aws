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
package group.rxcloud.capa.spi.aws.log.plugin;

import group.rxcloud.capa.addons.cat.CatLogPlugin;
import group.rxcloud.capa.addons.cat.DefaultCatLogPlugin;
import group.rxcloud.capa.spi.aws.log.configuration.CapaComponentLogConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Aws log plugin.
 */
public class AwsLogPlugin implements CatLogPlugin {

    private static final String KEY_SCENARIO_ENABLE = "scenarioEnable";

    private static final Logger log = LoggerFactory.getLogger("CapaAwsTagLogger");

    @Override
    public void logTags(String scenario, Map<String, String> tags) {
        if (filterScenario(scenario)) {
            log.info(DefaultCatLogPlugin.buildLogData(scenario, tags));
        }
    }

    private static boolean filterScenario(String scenario) {
        Optional<CapaComponentLogConfiguration> configuration = CapaComponentLogConfiguration.getInstanceOpt();
        if (!configuration.isPresent()) {
            return true;
        }
        if (!configuration.get().containsKey(KEY_SCENARIO_ENABLE)) {
            return true;
        }

        String scenarios = configuration.get().get(KEY_SCENARIO_ENABLE);
        if (scenarios != null) {
            String[] splits = scenarios.split(",");
            return Arrays.stream(splits)
                    .anyMatch(s -> s.trim().equalsIgnoreCase(scenario));
        }
        return false;
    }
}
