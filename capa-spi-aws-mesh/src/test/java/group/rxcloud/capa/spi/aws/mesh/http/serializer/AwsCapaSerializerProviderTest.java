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
package group.rxcloud.capa.spi.aws.mesh.http.serializer;

import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.capa.infrastructure.serializer.DefaultObjectSerializer;
import group.rxcloud.capa.spi.aws.mesh.AwsCapaRpcProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AwsCapaSerializerProviderTest {

    @AfterEach
    public void resetSerializerSetting() {
        AwsCapaRpcProperties.SerializerProperties.Settings.setRpcAwsAppMeshSerializer("default");
    }

    @Test
    public void testGetSerializerOrDefault_UsesDefaultWhenConfiguredSerializerIsUnavailable() {
        AwsCapaRpcProperties.SerializerProperties.Settings.setRpcAwsAppMeshSerializer("unavailable");

        CapaObjectSerializer serializerOrDefault = AwsCapaSerializerProvider.getSerializerOrDefault(null);

        Assertions.assertEquals("application/json", serializerOrDefault.getContentType());
    }

    @Test
    public void testGetSerializerOrDefault_UsesProvidedSerializer() {
        AwsCapaRpcProperties.SerializerProperties.Settings.setRpcAwsAppMeshSerializer("custom");
        CapaObjectSerializer serializer = new DefaultObjectSerializer();

        CapaObjectSerializer serializerOrDefault = AwsCapaSerializerProvider.getSerializerOrDefault(serializer);

        Assertions.assertSame(serializer, serializerOrDefault);
    }
}
