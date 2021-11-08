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

import group.rxcloud.cloudruntimes.utils.TypeRef;
import software.amazon.awssdk.core.SdkClient;

import java.util.Map;

/**
 * TODO load class from spi
 */
public abstract class AwsClientProviderLoader {

    static Map<String, AwsClientProvider> clientProviders;

    public static <T extends SdkClient> T load(TypeRef<T> typeRef) {
        AwsClientProvider provider = clientProviders.get(typeRef.getClass().getSimpleName());
        SdkClient sdkClient = provider.provideAwsClient();
        return (T) sdkClient;
    }

    interface AwsClientProvider<T extends SdkClient> {

        T provideAwsClient();
    }
}
