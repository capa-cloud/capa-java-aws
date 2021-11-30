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
package group.rxcloud.capa.spi.aws.mesh.http;

import group.rxcloud.capa.component.http.HttpResponse;
import group.rxcloud.capa.infrastructure.exceptions.CapaErrorContext;
import group.rxcloud.capa.infrastructure.exceptions.CapaException;
import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.capa.spi.aws.mesh.AwsCapaRpcProperties;
import group.rxcloud.capa.spi.aws.mesh.http.config.AwsRpcServiceOptions;
import group.rxcloud.capa.spi.aws.mesh.http.serializer.AwsCapaSerializerProvider;
import group.rxcloud.capa.spi.http.CapaSerializeHttpSpi;
import group.rxcloud.capa.spi.http.config.RpcServiceOptions;
import group.rxcloud.cloudruntimes.domain.core.invocation.HttpExtension;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;


public class AwsCapaHttp extends CapaSerializeHttpSpi {

    private static final Logger logger = LoggerFactory.getLogger(AwsCapaHttp.class);

    /**
     * Instantiates a new Capa serialize http spi.
     *
     * @param httpClient       the http client
     * @param objectSerializer the object serializer
     */
    public AwsCapaHttp(OkHttpClient httpClient, CapaObjectSerializer objectSerializer) {
        super(httpClient, AwsCapaSerializerProvider.getSerializerOrDefault(objectSerializer));
    }

    @Override
    protected <T> CompletableFuture<HttpResponse<T>> invokeSpiApi(
            String appId,
            String method,
            Object requestData,
            String httpMethod,
            Map<String, String> headers,
            Map<String, List<String>> urlParameters,
            TypeRef<T> type,
            RpcServiceOptions rpcServiceOptions) {
        Objects.requireNonNull(rpcServiceOptions, "rpcServiceOptions");
        AwsToAwsHttpServiceMeshInvoker awsToAwsHttpServiceMeshInvoker = new AwsToAwsHttpServiceMeshInvoker();
        return awsToAwsHttpServiceMeshInvoker.doInvokeSpiApi(
                appId,
                method,
                requestData,
                httpMethod,
                headers, urlParameters,
                type,
                (AwsRpcServiceOptions) rpcServiceOptions);
    }

    private interface AwsHttpInvoker {

        /**
         * Do invoke spi api.
         *
         * @param <T>               the response type parameter
         * @param appId             the app id
         * @param method            the method
         * @param requestData       the request data
         * @param headers           the headers
         * @param type              the response type
         * @param rpcServiceOptions the rpc service options
         * @return the async completable future
         */
        <T> CompletableFuture<HttpResponse<T>> doInvokeSpiApi(
                String appId,
                String method,
                Object requestData,
                String httpMethod,
                Map<String, String> headers,
                Map<String, List<String>> urlParameters,
                TypeRef<T> type,
                AwsRpcServiceOptions rpcServiceOptions);
    }

    /**
     * AWS to AWS service mesh rpc invoker
     */
    private class AwsToAwsHttpServiceMeshInvoker implements AwsHttpInvoker {

        /**
         * Fix to POST http method
         */
        private static final String POST = "POST";

        @Override
        public <T> CompletableFuture<HttpResponse<T>> doInvokeSpiApi(
                String appId,
                String method,
                Object requestData,
                String httpMethod,
                Map<String, String> headers,
                Map<String, List<String>> urlParameters,
                TypeRef<T> type,
                AwsRpcServiceOptions rpcServiceOptions) {
            AwsRpcServiceOptions.AwsToAwsServiceOptions awsToAwsServiceOptions = rpcServiceOptions.getAwsToAwsServiceOptions();
            final String serviceId = awsToAwsServiceOptions.getServiceId();
            if (StringUtils.isBlank(serviceId)) {
                throw new CapaException(CapaErrorContext.PARAMETER_ERROR,
                        "Aws appMesh no serviceId error.");
            }

            final int servicePort = awsToAwsServiceOptions.getServicePort();
            final String namespace = awsToAwsServiceOptions.getNamespace();

            if (httpMethod == null) {
                httpMethod = POST;
            }

            return doAsyncInvoke(
                    method,
                    requestData,
                    httpMethod,
                    headers,
                    urlParameters,
                    type,
                    serviceId,
                    namespace,
                    servicePort);
        }

        private <T> CompletableFuture<HttpResponse<T>> doAsyncInvoke(
                String method,
                Object requestData,
                String httpMethod,
                Map<String, String> headers,
                Map<String, List<String>> urlParameters,
                TypeRef<T> type,
                String serviceId,
                String namespace,
                int servicePort) {
            // generate app mesh http url
            final String appMeshHttpUrl = AwsCapaRpcProperties.AppMeshProperties.Settings.getRpcAwsAppMeshTemplate()
                    .replace("{serviceId}", serviceId)
                    .replace("{namespace}", namespace)
                    .replace("{servicePort}", String.valueOf(servicePort))
                    .replace("{operation}", method);

            if (HttpExtension.GET.getMethod().toString().equals(httpMethod)) {
                // TODO: 2021/11/30 append urlParameters to url
            }

            // async invoke
            CompletableFuture<HttpResponse<T>> asyncInvoke0 = invokeHttp(
                    appMeshHttpUrl,
                    requestData,
                    httpMethod,
                    headers,
                    type);
            asyncInvoke0.exceptionally(throwable -> {
                if (logger.isWarnEnabled()) {
                    logger.warn("[AwsCapaHttp] async invoke error", throwable);
                }
                throw new CapaException(CapaErrorContext.DEPENDENT_SERVICE_ERROR, throwable);
            });
            return asyncInvoke0;
        }

        private <T> CompletableFuture<HttpResponse<T>> invokeHttp(
                String url,
                Object requestData,
                String httpMethod,
                Map<String, String> headers,
                TypeRef<T> type) {
            // generate http request body
            RequestBody body = getRequestBodyWithSerialize(requestData, headers);
            Headers header = getRequestHeaderWithParams(headers);

            // make http request
            Request request = new Request.Builder()
                    .url(url)
                    .headers(header)
                    .method(httpMethod, body)
                    .build();

            return doAsyncInvoke0(request, type);
        }
    }
}
