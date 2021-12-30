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

import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Reckless Xu
 */
public class PropertiesSerializer implements Serializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesSerializer.class);

    @Override
    public <T> T deserialize(SdkBytes contentSdkBytes, TypeRef<T> type) {
        Map<String, String> map = parsePropertiesToMap(contentSdkBytes.asInputStream());
        return (T) map;
    }

    private Map<String, String> parsePropertiesToMap(InputStream inputStream) {
        Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            LOGGER.error("properties load error", e);
        }
        Map<String, String> map = new HashMap<>(properties.size());
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return map;
    }
}
