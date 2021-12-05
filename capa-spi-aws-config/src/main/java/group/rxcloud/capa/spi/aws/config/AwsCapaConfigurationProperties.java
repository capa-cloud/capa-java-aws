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
package group.rxcloud.capa.spi.aws.config;


import group.rxcloud.capa.infrastructure.CapaProperties;

import java.util.Properties;

/**
 * @author Reckless Xu
 */
public interface AwsCapaConfigurationProperties {

    interface AppConfigProperties {

        abstract class Settings {

            private static String awsAppConfigEnv = "ENV";

            private static final String CONFIG_AWS_APP_CONFIG_ENV = "CONFIG_AWS_APP_CONFIG_ENV";

            private static Long requestTimeoutInSeconds = 15L;

            private static final String REQUEST_TIMEOUT_IN_SECONDS = "REQUEST_TIMEOUT_IN_SECONDS";

            static {
                Properties awsProperties = CapaProperties.COMPONENT_PROPERTIES_SUPPLIER.apply("configuration-aws");

                awsAppConfigEnv = awsProperties.getProperty(CONFIG_AWS_APP_CONFIG_ENV, awsAppConfigEnv);

                requestTimeoutInSeconds = Long.parseLong(awsProperties.getProperty(REQUEST_TIMEOUT_IN_SECONDS, String.valueOf(requestTimeoutInSeconds)));
            }

            public static String getConfigAwsAppConfigEnv() {
                return awsAppConfigEnv;
            }

            public static Long getRequestTimeoutInSeconds() {
                return requestTimeoutInSeconds;
            }

            private Settings() {
            }
        }
    }
}
