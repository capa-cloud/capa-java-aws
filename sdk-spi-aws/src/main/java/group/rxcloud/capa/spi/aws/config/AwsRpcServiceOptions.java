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

import group.rxcloud.capa.infrastructure.env.CapaEnvironment;
import group.rxcloud.capa.spi.config.RpcServiceOptions;

import java.util.Objects;

/**
 * RPC service options. Define for AppId.
 */
public class AwsRpcServiceOptions implements RpcServiceOptions {

    /**
     * Unique rpc service ID
     */
    private final String appId;
    private final ServiceRpcInvokeMode rpcInvokeMode;

    /*
     * Optional options
     */
    private AwsToAwsServiceOptions awsToAwsServiceOptions;

    /**
     * Instantiates a new Capa rpc service options.
     *
     * @param appId         the app id
     * @param rpcInvokeMode the rpc invoke mode
     */
    public AwsRpcServiceOptions(String appId, ServiceRpcInvokeMode rpcInvokeMode) {
        this.appId = appId;
        this.rpcInvokeMode = rpcInvokeMode;
    }

    /**
     * RPC service invoke mode
     */
    public enum ServiceRpcInvokeMode {
        /**
         * AWS → AWS
         */
        AWS_TO_AWS,
        ;
    }

    // -- Properties Defined

    /**
     * Properties required when calling the Aws service
     */
    public interface ToAwsServiceOptions {

        /**
         * SOA ServiceCode
         *
         * @return the service code
         */
        String getServiceCode();

        /**
         * SOA ServiceName
         *
         * @return the service name
         */
        String getServiceName();
    }

    /**
     * Properties available when deployed on Aws
     */
    public interface AwsServiceOptions {

        /**
         * Aws deployment environment
         *
         * @return the service env
         */
        CapaEnvironment.DeployVpcEnvironment getServiceEnv();
    }

    // Specific Properties Impl

    /**
     * The service deployed on Aws calls the service of Aws
     */
    public static class AwsToAwsServiceOptions implements AwsServiceOptions, ToAwsServiceOptions {

        private final String serviceCode;
        private final String serviceName;
        private final CapaEnvironment.DeployVpcEnvironment serviceEnv;

        /**
         * Instantiates a new Aws to aws service options.
         *
         * @param serviceCode the service code
         * @param serviceName the service name
         * @param serviceEnv  the service env
         */
        public AwsToAwsServiceOptions(String serviceCode, String serviceName, CapaEnvironment.DeployVpcEnvironment serviceEnv) {
            this.serviceCode = serviceCode;
            this.serviceName = serviceName;
            this.serviceEnv = serviceEnv;
        }

        @Override
        public String getServiceCode() {
            return serviceCode;
        }

        @Override
        public String getServiceName() {
            return serviceName;
        }

        @Override
        public CapaEnvironment.DeployVpcEnvironment getServiceEnv() {
            return serviceEnv;
        }
    }

    // -- Getter and Setter

    /**
     * Gets app id.
     *
     * @return the app id
     */
    public String getAppId() {
        return appId;
    }

    /**
     * Gets rpc invoke mode.
     *
     * @return the rpc invoke mode
     */
    public ServiceRpcInvokeMode getRpcInvokeMode() {
        return rpcInvokeMode;
    }

    /**
     * Gets aws to aws service options.
     *
     * @return the aws to aws service options
     */
    public AwsToAwsServiceOptions getAwsToAwsServiceOptions() {
        return awsToAwsServiceOptions;
    }

    /**
     * Sets aws to aws service options.
     *
     * @param awsToAwsServiceOptions the aws to aws service options
     */
    public void setAwsToAwsServiceOptions(AwsToAwsServiceOptions awsToAwsServiceOptions) {
        this.awsToAwsServiceOptions = awsToAwsServiceOptions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AwsRpcServiceOptions)) {
            return false;
        }
        AwsRpcServiceOptions that = (AwsRpcServiceOptions) o;
        return Objects.equals(appId, that.appId) && rpcInvokeMode == that.rpcInvokeMode && Objects.equals(awsToAwsServiceOptions, that.awsToAwsServiceOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, rpcInvokeMode, awsToAwsServiceOptions);
    }

    @Override
    public String toString() {
        return "AwsRpcServiceOptions{" +
                "appId='" + appId + '\'' +
                ", rpcInvokeMode=" + rpcInvokeMode +
                ", awsToAwsServiceOptions=" + awsToAwsServiceOptions +
                '}';
    }
}
