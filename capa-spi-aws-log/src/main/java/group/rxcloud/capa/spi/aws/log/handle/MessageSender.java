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

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.spi.aws.log.configuration.LogConfiguration;
import group.rxcloud.capa.spi.aws.log.manager.CustomLogManager;
import group.rxcloud.capa.spi.aws.log.service.CloudWatchLogsService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class MessageSender extends Thread {
    private static final int MAX_COUNT_PER_CHUNK = 100;
    private static final long WAIT_INTERVAL = 20L;
    private static final int MAX_SIZE_PER_CHUNK = 1024 * 1024;
    private static final String PUT_LOG_EVENTS_RESOURCE_NAME = "CloudWatchLogs.putLogEvents";
    private static final String MESSAGE_SENDER_ERROR_NAMESPACE = "LogMessageSenderError";
    private static final String MESSAGE_SENDER_ERROR_METRIC_NAME = "LogsSenderError";
    private static final String LOG_STREAM_COUNT_NAME = "logStreamCount";
    private static final int DEFAULT_MAX_RULE_COUNT = 10;
    private static final String CLOUD_WATCH_AGENT_SWITCH_NAME = "cloudWatchAgentSwitch";
    private static Optional<LongCounter> LONG_COUNTER = Optional.empty();

    static {
        initFlowRules();
        Mixer.telemetryHooksNullable()
                .ifPresent(telemetryHooks -> {
                    Meter meter = telemetryHooks.buildMeter(MESSAGE_SENDER_ERROR_NAMESPACE).block();
                    LongCounter longCounter = meter.counterBuilder(MESSAGE_SENDER_ERROR_METRIC_NAME).build();
                    LONG_COUNTER = Optional.ofNullable(longCounter);
                });
    }

    private final ChunkQueue chunkQueue;
    private final LinkedList<CompressedChunk> readCompressedChunk;
    private volatile boolean running = true;
    private volatile CountDownLatch shutdownLatch;

    public MessageSender(ChunkQueue chunkQueue) {
        this.chunkQueue = chunkQueue;
        this.readCompressedChunk = new LinkedList<>();
    }

    private static void initFlowRules() {
        List<FlowRule> flowRules = new ArrayList<>();
        int ruleCount = LogConfiguration.containsKey(LOG_STREAM_COUNT_NAME)
                ? Integer.parseInt(LOG_STREAM_COUNT_NAME)
                : DEFAULT_MAX_RULE_COUNT;

        for (int i = 0; i < ruleCount; i++) {
            FlowRule flowRule = new FlowRule();
            flowRule.setResource(PUT_LOG_EVENTS_RESOURCE_NAME + "_" + i);
            flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            flowRule.setCount(5);
            flowRules.add(flowRule);
        }
        FlowRuleManager.loadRules(flowRules);
    }

    @Override
    public void run() {
        while (running) {
            try {
                buildCompressedChunk();
                if (readCompressedChunk != null && !readCompressedChunk.isEmpty()) {
                    List<String> messages = getMessage();
                    putLogToCloudWatch(messages);
                } else {
                    try {
                        Thread.sleep(WAIT_INTERVAL);
                    } catch (InterruptedException e) {
                        // ignore it
                    }
                }
            } catch (Throwable throwable) {
                CustomLogManager.error("MessageSender build chunk error.", throwable);
                LONG_COUNTER.ifPresent(longCounter -> {
                    longCounter.bind(Attributes.of(AttributeKey.stringKey("BuildCompressedChunkError"), throwable.getClass().getName()))
                            .add(1);
                });

            }
        }

        long timeOut = System.currentTimeMillis() + 60 * 1000;
        while (System.currentTimeMillis() <= timeOut) {
            buildCompressedChunk();
            if (readCompressedChunk != null && !readCompressedChunk.isEmpty()) {
                List<String> messages = this.getMessage();
                this.putLogToCloudWatch(messages);
            } else {
                break;
            }
        }
        this.shutdownLatch.countDown();
    }

    private void putLogToCloudWatch(List<String> logMessages) {
        if (!LogConfiguration.containsKey(CLOUD_WATCH_AGENT_SWITCH_NAME)
                || Boolean.TRUE.toString().equalsIgnoreCase(LogConfiguration.get(CLOUD_WATCH_AGENT_SWITCH_NAME))) {
            // put logs by agent
            putLogsByAgent(logMessages);
        } else {
            // put logs by api
            putLogsByApi(logMessages);
        }
    }

    private void putLogsByApi(List<String> logMessages) {
        try {
            List<String> logStreamNames = CloudWatchLogsService.getLogStreamNames();
            Random random = new Random();
            int index = random.nextInt(logStreamNames.size());
            try (Entry entry = SphU.entry(PUT_LOG_EVENTS_RESOURCE_NAME + '_' + index)) {
                CloudWatchLogsService.putLogEvents(logMessages, logStreamNames.get(index));
            } catch (BlockException blockException) {
                try {
                    Thread.sleep(WAIT_INTERVAL);
                    putLogsByApi(logMessages);
                } catch (Exception exception) {

                }
            }
        } catch (Throwable throwable) {
            CustomLogManager.error("MessageSender send message error.", throwable);
            LONG_COUNTER.ifPresent(longCounter -> {
                longCounter.bind(Attributes.of(AttributeKey.stringKey("SenderPutLogEventsError"), throwable.getClass().getName()))
                        .add(1);
            });
        }
    }

    private void putLogsByAgent(List<String> messages) {
        if (!CollectionUtils.isNullOrEmpty(messages)) {
            messages.forEach(message -> {
                System.out.println(message);
            });
        }
    }

    private List<String> getMessage() {
        List<String> messages = new ArrayList<>();
        CompressedChunk chunk;
        while ((chunk = pollChunk()) != null) {
            messages.add(chunk.getMessage());
        }
        return messages;
    }

    private boolean buildCompressedChunk() {
        if (!chunkQueue.isEmpty()) {
            chunkQueue.drainTo(readCompressedChunk, MAX_COUNT_PER_CHUNK, MAX_SIZE_PER_CHUNK);
            return true;
        }
        return false;
    }

    private CompressedChunk pollChunk() {
        return readCompressedChunk.poll();
    }

    public void shutdown() {
        this.shutdownLatch = new CountDownLatch(1);
        try {
            this.running = false;
            this.interrupt();
            shutdownLatch.await();
        } catch (InterruptedException e) {
        }
    }
}

