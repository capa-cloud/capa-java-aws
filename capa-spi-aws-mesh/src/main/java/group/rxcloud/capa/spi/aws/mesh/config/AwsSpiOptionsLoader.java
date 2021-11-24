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
package group.rxcloud.capa.spi.aws.mesh.config;

import group.rxcloud.capa.infrastructure.env.CapaEnvironment;
import group.rxcloud.capa.spi.aws.mesh.AwsCapaRpcProperties;
import group.rxcloud.capa.spi.config.CapaSpiOptionsLoader;

import java.util.Objects;

public class AwsSpiOptionsLoader implements CapaSpiOptionsLoader<AwsRpcServiceOptions> {

    @Override
    public AwsRpcServiceOptions loadRpcServiceOptions(String appId) {
        Objects.requireNonNull(appId, "appId");

        // generate AwsRpcServiceOptions
        AwsRpcServiceOptions rpcServiceOptions = new AwsRpcServiceOptions(appId, AwsRpcServiceOptions.ServiceRpcInvokeMode.AWS_TO_AWS);

        // get variable
        CapaEnvironment.DeployVpcEnvironment deployVpcEnvironment = CapaEnvironment.getDeployVpcEnvironment();
        final int servicePort = AwsCapaRpcProperties.AppMeshProperties.Settings.getRpcAwsAppMeshPort();
        final String namespace = AwsCapaRpcProperties.AppMeshProperties.Settings.getRpcAwsAppMeshNamespace();

        // generate awsToAwsServiceOptions
        // appid is serviceId
        AwsRpcServiceOptions.AwsToAwsServiceOptions awsToAwsServiceOptions =
                new AwsRpcServiceOptions.AwsToAwsServiceOptions(appId, servicePort, namespace, deployVpcEnvironment);
        rpcServiceOptions.setAwsToAwsServiceOptions(awsToAwsServiceOptions);

        return rpcServiceOptions;
    }
}
