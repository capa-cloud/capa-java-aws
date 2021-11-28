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
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Reckless Xu
 */

class AwsCapaConfigStoreIgnoreTest {

    AwsCapaConfigStore ins = new AwsCapaConfigStore(new DefaultObjectSerializer());

    @BeforeEach
    public void setUp(){
        ins.doInit(new StoreConfig());
    }

    @Disabled
    @Test
    void testDoGet(){
        Mono<List<ConfigurationItem<User>>> mono = ins.doGet("100012345", "", "", Lists.newArrayList("test1.json"), new HashMap<>(), TypeRef.get(User.class));
        mono.subscribe(resp->{
            System.out.println(resp.get(0).getContent().getAge());
        });
        List<ConfigurationItem<User>> block = mono.block();
        mono.subscribe(resp->{
            System.out.println(resp.get(0).getContent().getAge());
        });
        List<ConfigurationItem<User>> block2 = mono.block();
        System.out.println("");
        while (true){

        }
    }

    @Disabled
    @Test
    void testDoSubscribe(){
        Flux<SubscribeResp<User>> flux1 = ins.doSubscribe("100012345", "", "", Lists.newArrayList("test1.json"), new HashMap<>(), TypeRef.get(User.class));
        flux1.subscribe(resp -> {
            System.out.println("1:"+resp.getItems().get(0).getContent().getAge());
        });

        Flux<SubscribeResp<Map>> flux2 = ins.doSubscribe("100012345", "", "", Lists.newArrayList("test2.properties"), new HashMap<>(), TypeRef.get(Map.class));
        flux2.subscribe(resp -> {
            System.out.println("2:"+resp.getItems().get(0).getContent().get("age"));
        });

        Flux<SubscribeResp<User>> flux4 = ins.doSubscribe("100012345", "", "", Lists.newArrayList("test1.json"), new HashMap<>(), TypeRef.get(User.class));
        flux4.subscribe(resp -> {
            System.out.println("3:"+resp.getItems().get(0).getContent().getAge());
        });



        Flux<SubscribeResp<User>> flux3 = ins.doSubscribe("100012345", "", "", Lists.newArrayList("test3.xxx"), new HashMap<>(), TypeRef.get(User.class));
        flux3.subscribe(resp -> {
            System.out.println("4:"+resp.getItems().get(0).getContent().getAge());
        });
        long t1 = System.currentTimeMillis();
        while (System.currentTimeMillis()-t1<60000*2) {

        }
        ins.stopSubscribe();
        while (true){

        }
    }
}