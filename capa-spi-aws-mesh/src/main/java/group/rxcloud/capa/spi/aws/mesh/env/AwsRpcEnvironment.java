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
package group.rxcloud.capa.spi.aws.mesh.env;

import group.rxcloud.capa.infrastructure.exceptions.CapaErrorContext;
import group.rxcloud.capa.infrastructure.exceptions.CapaException;
import software.amazon.awssdk.utils.StringUtils;

import java.util.Objects;

import static group.rxcloud.capa.spi.aws.mesh.constants.AwsRpcConstants.AppMeshProperties.RPC_AWS_APP_MESH_DEFAULT_PORT;
import static group.rxcloud.capa.spi.aws.mesh.constants.AwsRpcConstants.AppMeshProperties.RPC_AWS_APP_MESH_PORT;
import static group.rxcloud.capa.spi.aws.mesh.constants.AwsRpcConstants.AppMeshProperties.RPC_AWS_APP_MESH_DEFAULT_NAMESPACE;
import static group.rxcloud.capa.spi.aws.mesh.constants.AwsRpcConstants.AppMeshProperties.RPC_AWS_APP_MESH_NAMESPACE;
import static group.rxcloud.capa.spi.aws.mesh.constants.AwsRpcConstants.SerializerProperties.RPC_AWS_APP_MESH_DEFAULT_SERIALIZER;
import static group.rxcloud.capa.spi.aws.mesh.constants.AwsRpcConstants.SerializerProperties.RPC_AWS_APP_MESH_SERIALIZER;


/**
 * Rpc System Environment Properties In Aws.
 */
public abstract class AwsRpcEnvironment {

    /**
     * The port of app mesh
     */
    private static int servicePort;

    /**
     * The serializer
     */
    private static String serializer;

    /**
     * The namespace of app mesh
     */
    private static String namespace;

    static {
        // setup server port
        initPort();

        // setup serializer
        initSerializer();

        // setup namespace
        initNamespace();
    }

    private static void initPort() {
        String awsRpcAppMeshPort = System.getProperty(RPC_AWS_APP_MESH_PORT);
        if (StringUtils.isBlank(awsRpcAppMeshPort)) {
            awsRpcAppMeshPort = RPC_AWS_APP_MESH_DEFAULT_PORT;
        }
        try {
            servicePort = Integer.parseInt(awsRpcAppMeshPort);
        } catch (Exception e) {
            throw new CapaException(CapaErrorContext.PARAMETER_ERROR, "Rpc Port: " + awsRpcAppMeshPort);
        }
    }

    private static void initSerializer() {
        String awsRpcAppMeshSerializer = System.getProperty(RPC_AWS_APP_MESH_SERIALIZER);
        if (StringUtils.isBlank(awsRpcAppMeshSerializer)) {
            awsRpcAppMeshSerializer = RPC_AWS_APP_MESH_DEFAULT_SERIALIZER;
        }
        serializer = awsRpcAppMeshSerializer;
    }

    private static void initNamespace() {
        String awsRpcAppMeshNamespace = System.getProperty(RPC_AWS_APP_MESH_NAMESPACE);
        if (StringUtils.isBlank(awsRpcAppMeshNamespace)) {
            awsRpcAppMeshNamespace = RPC_AWS_APP_MESH_DEFAULT_NAMESPACE;
        }
        namespace = awsRpcAppMeshNamespace;
    }

    public static int getServicePort() {
        return Objects.requireNonNull(servicePort, "Capa Rpc App Mesh Port");
    }

    public static String getSerializer() {
        return Objects.requireNonNull(serializer, "Capa Serializer");
    }

    public static String getNamespace() {
        return Objects.requireNonNull(namespace, "Capa Namespace");
    }
}
