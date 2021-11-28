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
package group.rxcloud.capa.spi.aws.config.serializer;

import group.rxcloud.capa.infrastructure.serializer.DefaultObjectSerializer;
import group.rxcloud.capa.spi.aws.config.User;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;

/**
 * @author Reckless Xu
 * 2021/11/25
 */
class DefaultSerializerTest {

    @Test
    void testDeserialize_Success(){
        SdkBytes sdkBytes = SdkBytes.fromUtf8String("{\n" +
                "\t\"name\":\"reckless\",\n" +
                "\t\"age\":28\n" +
                "}");
        DefaultSerializer ins = new DefaultSerializer(new DefaultObjectSerializer());
        User deserialize = ins.deserialize(sdkBytes, TypeRef.get(User.class));
        Assertions.assertEquals("reckless",deserialize.getName());
        Assertions.assertEquals(new Integer(28),deserialize.getAge());
    }
}