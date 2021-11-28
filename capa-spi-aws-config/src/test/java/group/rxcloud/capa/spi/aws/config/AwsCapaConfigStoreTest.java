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
package group.rxcloud.capa.spi.aws.config;

import com.google.common.collect.Lists;
import group.rxcloud.capa.component.configstore.ConfigurationItem;
import group.rxcloud.capa.component.configstore.StoreConfig;
import group.rxcloud.capa.component.configstore.SubscribeResp;
import group.rxcloud.capa.infrastructure.serializer.DefaultObjectSerializer;
import group.rxcloud.capa.spi.aws.config.entity.Configuration;
import group.rxcloud.capa.spi.aws.config.serializer.SerializerProcessor;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.ArgumentMatchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfig.AppConfigAsyncClient;
import software.amazon.awssdk.services.appconfig.model.GetConfigurationRequest;
import software.amazon.awssdk.services.appconfig.model.GetConfigurationResponse;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Reckless Xu
 * 2021/11/25
 */
@FixMethodOrder(MethodSorters.JVM)
@RunWith(PowerMockRunner.class)
class AwsCapaConfigStoreTest {

    AwsCapaConfigStore ins;

    @BeforeEach
    public void setUp() {
        ins = new AwsCapaConfigStore(new DefaultObjectSerializer());
        StoreConfig storeConfig = new StoreConfig();
        storeConfig.setStoreName(AwsCapaConfigurationProperties.AppConfigProperties.Settings.getAwsAppConfigName());

        AppConfigAsyncClient client = PowerMockito.mock(AppConfigAsyncClient.class);
        Whitebox.setInternalState(ins, "appConfigAsyncClient", client);
        PowerMockito.when(client.getConfiguration(ArgumentMatchers.any(GetConfigurationRequest.class))).thenReturn(mockGetConfigurationRespV1());


        SerializerProcessor serializerProcessor = PowerMockito.mock(SerializerProcessor.class);
        Whitebox.setInternalState(ins, "serializerProcessor", serializerProcessor);
        User user = new User();
        user.setName("reckless");
        user.setAge(28);
        PowerMockito.when(serializerProcessor.deserialize(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(user);
    }

    @AfterEach
    void restoreVersionMap() throws NoSuchFieldException, IllegalAccessException {
        Class<AwsCapaConfigStore> clazz = AwsCapaConfigStore.class;
        Field versionMap = clazz.getDeclaredField("versionMap");
        versionMap.setAccessible(true);
        ((Map<String, ConcurrentHashMap<String, Configuration<?>>>)versionMap.get(ins)).remove("100012345_FAT");
    }

    @Test
    void testStopSubscribe_Success() {
        String result = ins.stopSubscribe();
        Assertions.assertEquals("success", result);
    }

    @Test
    void testClose_Success() {
        ins.close();
        Assertions.assertTrue(true);
    }

    @Test
    void testDoGet_SuccessWithInitialization() {
        Mono<List<ConfigurationItem<User>>> listMono = ins.doGet("100012345", "", "", Lists.newArrayList("test.json"), null, TypeRef.get(User.class));
        List<ConfigurationItem<User>> block = listMono.block();
        Assertions.assertNotNull(block);
        Assertions.assertEquals("test.json", block.get(0).getKey());
        Assertions.assertEquals("reckless", block.get(0).getContent().getName());
        Assertions.assertEquals(new Integer(28), block.get(0).getContent().getAge());
    }

    @Test
    void testDoGet_SuccessWithGetInitialization_VersionNotChange() {
        Mono<List<ConfigurationItem<User>>> listMono2 = ins.doGet("100012345", "", "", Lists.newArrayList("test.json"), null, TypeRef.get(User.class));
        List<ConfigurationItem<User>> block2 = listMono2.block();
        Assertions.assertNotNull(block2);
        Assertions.assertEquals("test.json", block2.get(0).getKey());
        Assertions.assertEquals("reckless", block2.get(0).getContent().getName());
        Assertions.assertEquals(new Integer(28), block2.get(0).getContent().getAge());
    }


    @Test
    void testDoSubscribe_SuccessInitialization() {
        Flux<SubscribeResp<User>> subscribeRespFlux = ins.doSubscribe("100012345", "", "", Lists.newArrayList("test.json"), null, TypeRef.get(User.class));
        SubscribeResp<User> userSubscribeResp = subscribeRespFlux.blockFirst();
        Assertions.assertNotNull(userSubscribeResp);
        Assertions.assertEquals("100012345", userSubscribeResp.getAppId());
        Assertions.assertEquals("AWS AppConfig", userSubscribeResp.getStoreName());
        Assertions.assertEquals("test.json", userSubscribeResp.getItems().get(0).getKey());
        Assertions.assertEquals("reckless", userSubscribeResp.getItems().get(0).getContent().getName());
        Assertions.assertEquals(new Integer(28), userSubscribeResp.getItems().get(0).getContent().getAge());
    }

    @Test
    void testDoGet_SuccessWithGetInitialization_VersionChanges() throws IllegalAccessException, NoSuchFieldException {
        Class<AwsCapaConfigStore> clazz = AwsCapaConfigStore.class;
        Field versionMap = clazz.getDeclaredField("versionMap");
        versionMap.setAccessible(true);

        Map<String, ConcurrentHashMap<String, Configuration<?>>> map = (Map<String, ConcurrentHashMap<String, Configuration<?>>>) versionMap.get(ins);
        ConcurrentHashMap<String, Configuration<?>> configMap = new ConcurrentHashMap<>();

        Configuration<User> configuration = new Configuration<>();
        configuration.setClientConfigurationVersion("5");

        ConfigurationItem<User> configurationItem = new ConfigurationItem<>();
        configurationItem.setKey("test.json");

        User user = new User();
        user.setAge(28);
        user.setName("reckless");

        configurationItem.setContent(user);
        configuration.setConfigurationItem(configurationItem);
        configuration.getInitialized().set(true);

        configMap.put("test.json", configuration);
        map.put("100012345_FAT", configMap);

        Mono<List<ConfigurationItem<User>>> listMono = ins.doGet("100012345", "", "", Lists.newArrayList("test.json"), null, TypeRef.get(User.class));
        List<ConfigurationItem<User>> block = listMono.block();
        Assertions.assertNotNull(block);
        Assertions.assertEquals("test.json", block.get(0).getKey());
        Assertions.assertEquals("reckless", block.get(0).getContent().getName());
        Assertions.assertEquals(new Integer(28), block.get(0).getContent().getAge());

    }


    @Test
    void testDoSubscribe_SuccessSubscribeChanges() throws NoSuchFieldException, IllegalAccessException {
        Class<AwsCapaConfigStore> clazz = AwsCapaConfigStore.class;
        Field versionMap = clazz.getDeclaredField("versionMap");
        versionMap.setAccessible(true);

        Map<String, ConcurrentHashMap<String, Configuration<?>>> map = (Map<String, ConcurrentHashMap<String, Configuration<?>>>) versionMap.get(ins);
        ConcurrentHashMap<String, Configuration<?>> configMap = new ConcurrentHashMap<>();

        Configuration<User> configuration = new Configuration<>();
        configuration.setClientConfigurationVersion("5");

        ConfigurationItem<User> configurationItem = new ConfigurationItem<>();
        configurationItem.setKey("test.json");

        User user = new User();
        user.setAge(27);
        user.setName("reckless");

        configurationItem.setContent(user);
        configuration.setConfigurationItem(configurationItem);
        configuration.getInitialized().set(true);

        configMap.put("test.json", configuration);
        map.put("100012345_FAT", configMap);

        Flux<SubscribeResp<User>> subscribeRespFlux = ins.doSubscribe("100012345", "", "", Lists.newArrayList("test.json"), null, TypeRef.get(User.class));
        SubscribeResp<User> userSubscribeResp = subscribeRespFlux.skip(1L).blockFirst();
        Assertions.assertNotNull(userSubscribeResp);
        Assertions.assertEquals("100012345", userSubscribeResp.getAppId());
        Assertions.assertEquals("AWS AppConfig", userSubscribeResp.getStoreName());
        Assertions.assertEquals("test.json", userSubscribeResp.getItems().get(0).getKey());
        Assertions.assertEquals("reckless", userSubscribeResp.getItems().get(0).getContent().getName());
        Assertions.assertEquals(new Integer(28), userSubscribeResp.getItems().get(0).getContent().getAge());
    }

    private CompletableFuture<GetConfigurationResponse> mockGetConfigurationRespV1() {
        GetConfigurationResponse response = GetConfigurationResponse.builder().configurationVersion("1").content(SdkBytes.fromUtf8String("{\n" +
                "\t\"name\":\"reckless\",\n" +
                "\t\"age\":28\n" +
                "}")).build();

        return CompletableFuture.supplyAsync(() -> response);
    }
}