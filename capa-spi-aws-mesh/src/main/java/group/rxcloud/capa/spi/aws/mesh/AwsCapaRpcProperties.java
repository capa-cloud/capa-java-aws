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
package group.rxcloud.capa.spi.aws.mesh;

import group.rxcloud.capa.addons.foundation.CapaFoundation;
import group.rxcloud.capa.addons.foundation.FoundationType;
import group.rxcloud.capa.infrastructure.CapaProperties;
import group.rxcloud.capa.infrastructure.exceptions.CapaErrorContext;
import group.rxcloud.capa.infrastructure.exceptions.CapaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * The AWS Capa properties.
 */
public interface AwsCapaRpcProperties {

    Logger logger = LoggerFactory.getLogger(AwsCapaRpcProperties.class);

    interface AppMeshProperties {

        abstract class Settings {

            /**
             * The aws app mesh http url template
             * {serviceId}.svc.cluster.local is virtual service name (https://docs.aws.amazon.com/zh_cn/zh_cn/app-mesh/latest/userguide/virtual_services.html)
             */
            private static String rpcAwsAppMeshTemplate = "http://{serviceId}.{namespace}.svc.cluster.local:{servicePort}/{operation}";
            private static Integer rpcAwsAppMeshPort = 8080;
            private static String rpcAwsAppMeshNamespace = "";

            private static final String RPC_AWS_APP_MESH_TEMPLATE = "RPC_AWS_APP_MESH_TEMPLATE";
            private static final String RPC_AWS_APP_MESH_PORT = "RPC_AWS_APP_MESH_PORT";
            private static final String RPC_AWS_APP_MESH_NAMESPACE = "RPC_AWS_APP_MESH_NAMESPACE";

            static {
                Properties properties = CapaProperties.COMPONENT_PROPERTIES_SUPPLIER.apply("rpc-aws");

                rpcAwsAppMeshTemplate = properties.getProperty(RPC_AWS_APP_MESH_TEMPLATE, rpcAwsAppMeshTemplate);

                String awsRpcAppMeshPort = properties.getProperty(RPC_AWS_APP_MESH_PORT, String.valueOf(rpcAwsAppMeshPort));
                try {
                    rpcAwsAppMeshPort = Integer.parseInt(awsRpcAppMeshPort);
                } catch (Exception e) {
                    throw new CapaException(CapaErrorContext.PARAMETER_ERROR, "Rpc Port: " + awsRpcAppMeshPort);
                }

                // FIXME: 2021/12/15 use trip logic currently
                rpcAwsAppMeshNamespace = CapaFoundation.getNamespace(FoundationType.TRIP);

                logger.info("[Capa.Rpc.Client.config] [AwsCapaRpcProperties.AppMeshProperties] rpcAwsAppMeshTemplate[{}] rpcAwsAppMeshPort[{}] rpcAwsAppMeshNamespace[{}]",
                        rpcAwsAppMeshTemplate, rpcAwsAppMeshPort, rpcAwsAppMeshNamespace);
            }

            public static Integer getRpcAwsAppMeshPort() {
                return rpcAwsAppMeshPort;
            }

            public static String getRpcAwsAppMeshNamespace() {
                return rpcAwsAppMeshNamespace;
            }

            public static String getRpcAwsAppMeshTemplate() {
                return rpcAwsAppMeshTemplate;
            }

            private Settings() {
            }
        }
    }

    interface SerializerProperties {

        abstract class Settings {

            private static String rpcAwsAppMeshSerializer = "default";

            private static final String RPC_AWS_APP_MESH_SERIALIZER = "RPC_AWS_APP_MESH_SERIALIZER";

            static {
                Properties properties = CapaProperties.COMPONENT_PROPERTIES_SUPPLIER.apply("rpc-aws");

                rpcAwsAppMeshSerializer = properties.getProperty(RPC_AWS_APP_MESH_SERIALIZER, rpcAwsAppMeshSerializer);

                logger.info("[Capa.Rpc.Client.config] [AwsCapaRpcProperties.SerializerProperties] rpcAwsAppMeshSerializer[{}]",
                        rpcAwsAppMeshSerializer);
            }

            public static String getRpcAwsAppMeshSerializer() {
                return rpcAwsAppMeshSerializer;
            }

            public static void setRpcAwsAppMeshSerializer(String rpcAwsAppMeshSerializer) {
                Settings.rpcAwsAppMeshSerializer = rpcAwsAppMeshSerializer;
            }

            private Settings() {
            }
        }
    }
}
