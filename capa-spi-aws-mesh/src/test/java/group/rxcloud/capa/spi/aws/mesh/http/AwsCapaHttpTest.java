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
import group.rxcloud.capa.infrastructure.exceptions.CapaException;
import group.rxcloud.capa.spi.aws.mesh.http.config.AwsRpcServiceOptions;
import group.rxcloud.capa.spi.aws.mesh.http.config.AwsSpiOptionsLoader;
import group.rxcloud.capa.spi.aws.mesh.http.serializer.BaijiSSJsonObjectSerializer;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class AwsCapaHttpTest {

    private OkHttpClient okHttpClient;

    private BaijiSSJsonObjectSerializer baijiSSJsonObjectSerializer;

    private AwsCapaHttp awsCapaHttp;

    @BeforeEach
    public void setUp() {
        okHttpClient = new OkHttpClient.Builder().build();

        baijiSSJsonObjectSerializer = new BaijiSSJsonObjectSerializer();

        awsCapaHttp = new AwsCapaHttp(okHttpClient, baijiSSJsonObjectSerializer);
    }

    @Test
    public void testInvokeSpiApi_Success() {
        AwsSpiOptionsLoader awsSpiOptionsLoader = new AwsSpiOptionsLoader();
        AwsRpcServiceOptions awsRpcServiceOptions = awsSpiOptionsLoader.loadRpcServiceOptions("appId");

        CompletableFuture<HttpResponse<String>> responseCompletableFuture = awsCapaHttp.invokeSpiApi("appId",
                "method",
                "requestData",
                new HashMap<>(),
                TypeRef.STRING,
                awsRpcServiceOptions);

        responseCompletableFuture.cancel(true);
    }

    @Test
    public void testInvokeSpiApi_FailWhenServiceIdIsNull() {
        AwsSpiOptionsLoader awsSpiOptionsLoader = new AwsSpiOptionsLoader();
        AwsRpcServiceOptions awsRpcServiceOptions = awsSpiOptionsLoader.loadRpcServiceOptions("");

        Assertions.assertThrows(CapaException.class, () -> {
            awsCapaHttp.invokeSpiApi("appId",
                    "method",
                    "requestData",
                    new HashMap<>(),
                    TypeRef.STRING,
                    awsRpcServiceOptions);
        });
    }
}
