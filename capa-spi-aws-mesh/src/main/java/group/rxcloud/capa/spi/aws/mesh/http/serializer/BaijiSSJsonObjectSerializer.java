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

import group.rxcloud.capa.addons.serializer.CapaSerializer;
import group.rxcloud.capa.addons.serializer.baiji.ssjson.SSJsonSerializer;
import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.cloudruntimes.utils.TypeRef;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Serializes and deserializes the object with baiji.
 */
public class BaijiSSJsonObjectSerializer implements CapaObjectSerializer {

    private static final SSJsonSerializer SERIALIZER = CapaSerializer.ssJsonSerializer;

    /**
     * Serializes a given state object into byte array.
     *
     * @param o State object to be serialized.
     * @return Array of bytes[] with the serialized content.
     * @throws IOException In case state cannot be serialized.
     */
    @Override
    public byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        SERIALIZER.serialize(stream, o);
        return stream.toByteArray();
    }

    /**
     * Deserializes the byte array into the original object.
     *
     * @param data Content to be parsed.
     * @param type Type of the object being deserialized.
     * @param <T>  Generic type of the object being deserialized.
     * @return Object of type T.
     * @throws IOException In case content cannot be deserialized.
     */
    @Override
    public <T> T deserialize(byte[] data, TypeRef<T> type) throws IOException {
        Class<?> clazz = (Class<?>) type.getType();
        Object deserialize = SERIALIZER.deserialize(new ByteArrayInputStream(data), clazz);
        return (T) deserialize;
    }

    /**
     * Returns the content type of the request.
     *
     * @return content type of the request
     */
    @Override
    public String getContentType() {
        return SERIALIZER.contentType();
    }
}
