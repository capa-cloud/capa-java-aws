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
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;

import java.io.IOException;

/**
 * @author Reckless Xu
 * @date 2021/11/18
 */
public class JsonSerializer implements Serializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonSerializer.class);

    final CapaObjectSerializer objectSerializer;

    public JsonSerializer(CapaObjectSerializer objectSerializer) {
        this.objectSerializer = objectSerializer;
    }

    @Override
    public <T> T deserialize(SdkBytes contentSdkBytes, TypeRef<T> type) {
        T content = null;
        try {
            content = objectSerializer.deserialize(contentSdkBytes.asByteArray(), type);
        } catch (IOException e) {
            LOGGER.error("error accurs when deserializing,content:{},typeName:{}", contentSdkBytes.asUtf8String(), type.getType().getTypeName(), e);
        }
        return content;
    }
}
