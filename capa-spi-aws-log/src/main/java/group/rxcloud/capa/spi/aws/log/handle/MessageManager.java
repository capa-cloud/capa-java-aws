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
package group.rxcloud.capa.spi.aws.log.handle;

import group.rxcloud.capa.infrastructure.hook.Mixer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageManager {

    private static final Object lock = new Object();
    private static final float DEFAULT_CHUNK_QUEUE_MEM_PERCENT = 0.05f;
    private static final int DEFAULT_CHUNK_QUEUE_MEM_BYTES = 100 * 1024 * 1024;
    private static final String MESSAGE_MANAGER_ERROR_NAMESPACE = "LogMessageManagerError";
    private static final String MESSAGE_MANAGER_ERROR_METRIC_NAME = "LogsManagerError";
    private static MessageManager messageManager;
    private static Optional<LongCounter> LONG_COUNTER = Optional.empty();

    static {
        Mixer.telemetryHooksNullable()
                .ifPresent(telemetryHooks -> {
                    Meter meter = telemetryHooks.buildMeter(MESSAGE_MANAGER_ERROR_NAMESPACE).block();
                    LongCounter longCounter = meter.counterBuilder(MESSAGE_MANAGER_ERROR_METRIC_NAME).build();
                    LONG_COUNTER = Optional.ofNullable(longCounter);
                });
    }

    private final int chunkQueueMaxBytes;
    //private MonitorSender monitorSender;
    private final AtomicInteger senderNumber = new AtomicInteger(1);
    private final MessageConsumer consumer;
    private final MessageSender sender;
    private final ChunkQueue chunkQueue;
    private volatile boolean shutdownInProgress = false;

    private MessageManager() {
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new ClientFinalizer("AWSManager-ClientFinalizer"));

        long maxMemory = runtime.maxMemory();
        int defaultChunkQueueMaxBytes = (int) (maxMemory * DEFAULT_CHUNK_QUEUE_MEM_PERCENT);
        if (defaultChunkQueueMaxBytes > DEFAULT_CHUNK_QUEUE_MEM_BYTES) {
            defaultChunkQueueMaxBytes = DEFAULT_CHUNK_QUEUE_MEM_BYTES;
        }
        //TODO 设置队列最大值
        chunkQueueMaxBytes = defaultChunkQueueMaxBytes;
        chunkQueue = new ChunkQueue(chunkQueueMaxBytes);
        sender = new MessageSender(chunkQueue);
        startNewSender();
        consumer = createConsumer();
    }

    public static MessageManager getInstance() {
        synchronized (lock) {
            try {
                if (messageManager == null) {
                    messageManager = new MessageManager();
                }
            } catch (Throwable t) {
                LONG_COUNTER.ifPresent(longCounter -> {
                    longCounter.bind(Attributes.of(AttributeKey.stringKey("ManagerGetInstanceError"), "ManagerGetInstanceError"))
                            .add(1);
                });
            }
        }
        return messageManager;
    }

    public MessageConsumer getConsumer() {
        return consumer;
    }

    protected void startNewSender() {
        Thread t = new Thread(sender);
        t.setName("AWSManager-MessageSender" + "-" + senderNumber.getAndIncrement());
        t.setDaemon(true);
        t.start();
    }

    public MessageConsumer createConsumer() {
        MessageConsumer consumer = new MessageConsumer(chunkQueue);
        return consumer;
    }

    public void shutdown() {
        synchronized (lock) {
            if (this.shutdownInProgress) {
                return;
            }
            this.shutdownInProgress = true;
        }
        sender.shutdown();
    }

    class ClientFinalizer extends Thread {
        public ClientFinalizer(@NotNull String name) {
            super(name);
        }

        @Override
        public void run() {
            try {
                MessageManager.getInstance().shutdown();
            } catch (Exception e) {
                LONG_COUNTER.ifPresent(longCounter -> {
                    longCounter.bind(Attributes.of(AttributeKey.stringKey("ClientFinalizerError"), "ClientFinalizerError"))
                            .add(1);
                });
            }
        }
    }
}

