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
package group.rxcloud.capa.spi.aws.infrastructure;

import group.rxcloud.capa.addons.foundation.CapaFoundation;
import group.rxcloud.capa.addons.foundation.FoundationType;
import group.rxcloud.capa.infrastructure.CapaEnvironment;
import group.rxcloud.capa.infrastructure.CapaProperties;

import java.util.Properties;

/**
 * The Aws capa environment.
 */
public class AwsCapaEnvironment implements CapaEnvironment {

    @Override
    public String getDeployCloud() {
        return "AWS";
    }

    @Override
    public String getDeployRegion() {
        String regionKey = Settings.getRegionKey();
        return System.getProperty(regionKey);
    }

    @Override
    public String getDeployEnv() {
        // FIXME: 2021/12/15 use trip logic currently
        return CapaFoundation.getEnv(FoundationType.TRIP);
    }

    abstract static class Settings {

        private static String regionKey = "REGION";
        private static String envKey = "ENV";

        private static final String INFRASTRUCTURE_CLOUD_REGION_KEY = "INFRASTRUCTURE_CLOUD_REGION_KEY";
        private static final String INFRASTRUCTURE_CLOUD_ENV_KEY = "INFRASTRUCTURE_CLOUD_ENV_KEY";

        static {
            Properties properties = CapaProperties.INFRASTRUCTURE_PROPERTIES_SUPPLIER.apply("cloud-aws");

            regionKey = properties.getProperty(INFRASTRUCTURE_CLOUD_REGION_KEY, regionKey);

            envKey = properties.getProperty(INFRASTRUCTURE_CLOUD_ENV_KEY, envKey);
        }

        public static String getRegionKey() {
            return regionKey;
        }

        public static String getEnvKey() {
            return envKey;
        }

        private Settings() {
        }
    }
}
