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

import java.util.HashMap;
import java.util.Map;

/**
 * The capa serializer provider.
 */
public interface AwsCapaSerializerProvider {

    /**
     * Gets serializer or default.
     *
     * @param originSerializer the origin serializer
     * @return the serializer or default
     */
    static CapaObjectSerializer getSerializerOrDefault(CapaObjectSerializer originSerializer) {
        final String serializerName = AwsCapaRpcProperties.SerializerProperties.Settings.getRpcAwsAppMeshSerializer();
        Map<String, CapaObjectSerializer> serializerFactory = AwsCapaSerializerFactory.SERIALIZER_FACTORY;
        return serializerFactory.getOrDefault(serializerName, originSerializer);
    }

    /**
     * The capa serializer factory.
     */
    final class AwsCapaSerializerFactory {

        private static final Map<String, CapaObjectSerializer> SERIALIZER_FACTORY;

        static {
            SERIALIZER_FACTORY = new HashMap<>(2, 1);

            SERIALIZER_FACTORY.put("default", new DefaultObjectSerializer());
            SERIALIZER_FACTORY.put("baiji", new BaijiSSJsonObjectSerializer());
        }
    }
}
