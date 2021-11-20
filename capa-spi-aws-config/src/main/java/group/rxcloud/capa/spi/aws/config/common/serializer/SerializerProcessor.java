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
package group.rxcloud.capa.spi.aws.config.common.serializer;

import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.capa.spi.aws.config.common.enums.FileType;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import software.amazon.awssdk.core.SdkBytes;

/**
 * @author Reckless Xu
 * @date 2021/11/18
 */
public class SerializerProcessor {
    private final JsonSerializer jsonSerializer;

    private final PropertiesSerializer propertiesSerializer;

    private final DefaultSerializer defaultSerializer;

    public SerializerProcessor(CapaObjectSerializer objectSerializer) {
        jsonSerializer = new JsonSerializer(objectSerializer);
        propertiesSerializer = new PropertiesSerializer();
        defaultSerializer = new DefaultSerializer(objectSerializer);
    }

    public <T> T deserialize(SdkBytes contentSdkBytes, TypeRef<T> type, String configurationName) {
        if (configurationName.endsWith(FileType.PROPERTIES.suffix())) {
            return propertiesSerializer.deserialize(contentSdkBytes, type);
        } else if (configurationName.endsWith(FileType.JSON.suffix())) {
            return jsonSerializer.deserialize(contentSdkBytes, type);
        } else {
            return defaultSerializer.deserialize(contentSdkBytes, type);
        }
    }
}
