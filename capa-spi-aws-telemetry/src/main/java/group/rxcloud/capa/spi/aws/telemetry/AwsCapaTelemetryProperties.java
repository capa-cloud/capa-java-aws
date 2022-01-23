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
package group.rxcloud.capa.spi.aws.telemetry;


import group.rxcloud.capa.infrastructure.CapaProperties;

import java.util.Properties;

public interface AwsCapaTelemetryProperties {

    abstract class Settings {

        private static String awsTraceId = "capa-trace-id";
        private static String defaultMetricNamespce;
        private static String[] customizedNamespacePrefix;


        private static final String TELEMETRY_AWS_TRACE_ID_KEY = "TELEMETRY_AWS_TRACE_ID_KEY";
        private static final String CUSTOMIZED_METRIC_NAMESPCE_KEY = "CUSTOMIZED_METRIC_NAMESPACE_PREFIX";
        private static final String DEFAULT_METRIC_NAMESPCE = "DEFAULT_METRIC_NAMESPACE";

        static {
            Properties awsProperties = CapaProperties.COMPONENT_PROPERTIES_SUPPLIER.apply("telemetry-aws");

            awsTraceId = awsProperties.getProperty(TELEMETRY_AWS_TRACE_ID_KEY, awsTraceId);
            customizedNamespacePrefix = awsProperties.getProperty(CUSTOMIZED_METRIC_NAMESPCE_KEY, "")
            .split(",");
            defaultMetricNamespce = awsProperties.getProperty(DEFAULT_METRIC_NAMESPCE, "");
        }

        public static String getTelemetryAwsTraceIdKey() {
            return awsTraceId;
        }

        public static String[] getCustomizedNamespacePrefix() {
            return customizedNamespacePrefix;
        }

        public static String getDefaultMetricNamespce() {
            return defaultMetricNamespce;
        }

        private Settings() {
        }
    }
}
