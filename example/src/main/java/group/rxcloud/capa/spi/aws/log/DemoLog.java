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
package group.rxcloud.capa.spi.aws.log;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
public class DemoLog {
    public static void main(String[] args) {
        MDC.put("requestId","123456");
        log.info("test:", new RuntimeException());

        /*for (int i = 0; i < 50; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < 10; j++) {
                        log.info("[[messageId=11234567]]Test +" +Thread.currentThread().getName());
                    }
                }
            }, "Thread_" + i).start();
           // log.info("Test");
        }*/
        try {
            Thread.sleep(60 * 1000);
        } catch (InterruptedException e) {

        }
    }
}
