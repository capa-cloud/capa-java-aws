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
package group.rxcloud.capa.spi.aws.mesh.http.config;

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

    /**
     * The rpc invoke mode
     */
    private final ServiceRpcInvokeMode rpcInvokeMode;

    /**
     * Optional options of Aws to Aws
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
         * AWS to AWS
         */
        AWS_TO_AWS;
    }

    /**
     * Properties required when calling the Aws service
     */
    public interface ToAwsServiceOptions {

        /**
         * ServiceId
         *
         * @return the service id
         */
        String getServiceId();

        /**
         * ServicePort
         *
         * @return the service port
         */
        int getServicePort();

        /**
         * Namespace
         *
         * @return the namespace
         */
        String getNamespace();
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
        String getServiceEnv();
    }

    /**
     * The service deployed on Aws calls the service of Aws
     */
    public static class AwsToAwsServiceOptions implements AwsServiceOptions, ToAwsServiceOptions {

        private final String serviceId;
        private final int servicePort;
        private final String namespace;
        private final String serviceEnv;

        /**
         * @param serviceId   the service id
         * @param servicePort the service port
         * @param serviceEnv  the service env
         */
        public AwsToAwsServiceOptions(String serviceId, int servicePort, String namespace,
                                      String serviceEnv) {
            this.serviceId = serviceId;
            this.servicePort = servicePort;
            this.namespace = namespace;
            this.serviceEnv = serviceEnv;
        }

        @Override
        public String getServiceId() {
            return serviceId;
        }

        @Override
        public int getServicePort() {
            return servicePort;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public String getServiceEnv() {
            return serviceEnv;
        }
    }

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
     * Gets Aws to Aws service options.
     *
     * @return the aws to aws service options
     */
    public AwsToAwsServiceOptions getAwsToAwsServiceOptions() {
        return awsToAwsServiceOptions;
    }

    /**
     * Sets Aws to Aws service options.
     *
     * @param awsToAwsServiceOptions the Aws to Aws service options
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
