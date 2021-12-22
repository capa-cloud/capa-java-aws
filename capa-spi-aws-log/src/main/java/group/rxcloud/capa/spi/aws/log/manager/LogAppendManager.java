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
package group.rxcloud.capa.spi.aws.log.manager;

import com.google.gson.Gson;
import group.rxcloud.capa.addons.foundation.CapaFoundation;
import group.rxcloud.capa.addons.foundation.FoundationType;
import group.rxcloud.capa.component.telemetry.context.CapaContext;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.infrastructure.hook.TelemetryHooks;
import group.rxcloud.capa.spi.aws.log.configuration.LogConfiguration;
import group.rxcloud.capa.spi.aws.log.handle.MessageConsumer;
import group.rxcloud.capa.spi.aws.log.handle.MessageManager;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import software.amazon.awssdk.utils.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LogAppendManager {
    /**
     * Tag identifier prefix.
     */
    protected static final String TAG_PREFIX = "[[";
    /**
     * Tag identifier suffix.
     */
    protected static final String TAG_SUFFIX = "]]";
    /**
     * The name of log source data.
     */
    protected static final String LOG_DATA_NAME = "logData";
    protected static final String ERROR_NAME = "errorName";
    protected static final String ERROR_MESSAGE = "errorMessage";


    /**
     * The name of log level.
     */
    protected static final String LOG_LEVEL_NAME = "logLevel";
    /**
     * The name of log's _trace_id.
     */
    protected static final String TRACE_ID_NAME = "_trace_id";

    protected static final String APP_ID_NAME = "appId";

    /**
     * Number of counts each time.
     */
    protected static final Integer COUNTER_NUM = 1;
    /**
     * Init a {@link Gson} instance.
     */
    private static final Gson GSON = new Gson();
    /**
     * The instance of the {@link TelemetryHooks}.
     */
    private static final Optional<TelemetryHooks> TELEMETRY_HOOKS;
    /**
     * The namespace for logging error.
     */
    private static final String LOG_ERROR_NAMESPACE = "CloudWatchLogs";
    /**
     * The metric name for logging error.
     */
    private static final String LOG_ERROR_METRIC_NAME = "LogError";
    private static final String CLOUD_WATCH_AGENT_SWITCH_NAME = "cloudWatchAgentSwitch";
    /**
     * Init an instance of {@link LongCounter}.
     */
    protected static Optional<LongCounter> LONG_COUNTER = Optional.empty();

    /**
     * Init telemetry hooks and longCounter.
     */
    static {
        TELEMETRY_HOOKS = Mixer.telemetryHooksNullable();
        TELEMETRY_HOOKS.ifPresent(telemetryHooks -> {
            Meter meter = telemetryHooks.buildMeter(LOG_ERROR_NAMESPACE).block();
            LongCounter longCounter = meter.counterBuilder(LOG_ERROR_METRIC_NAME).build();
            LONG_COUNTER = Optional.ofNullable(longCounter);
        });
    }

    protected static Map<String, String> parseTags(String message, int tagsEndIndex) {
        Map<String, String> tags = null;
        int tagStart = 2;
        while (tagStart < tagsEndIndex) {
            int tagEnd = message.indexOf(',', tagStart);
            if (tagEnd < 0 || tagEnd > tagsEndIndex) {
                tagEnd = tagsEndIndex;
            }
            int equalIndex = message.indexOf('=', tagStart);
            if (equalIndex > tagStart && equalIndex < tagEnd - 1) {
                String key = message.substring(tagStart, equalIndex);
                String value = message.substring(equalIndex + 1, tagEnd);
                if (tags == null) {
                    tags = new HashMap<>();
                }
                tags.put(key, value);
            }
            tagStart = tagEnd + 1;
        }
        return tags;
    }

    protected static Map<String, String> appendMDCTags(Map<String, String> tags, Map<String, String> MDCTags) {
        if (MDCTags != null && !MDCTags.isEmpty()) {
            if (tags == null) {
                return new HashMap<String, String>(MDCTags);
            } else {
                tags.putAll(MDCTags);
                return tags;
            }
        }
        return tags;
    }

    public static void appendLogs(String message, Map<String, String> MDCTags, String logLevel, Throwable throwable) {
        if (StringUtils.isBlank(message)) {
            message = "";
        }
        Map<String, String> tags = new HashMap<>();
        //tags.put(LOG_LEVEL_NAME, logLevel);
        if (message.startsWith(TAG_PREFIX)) {
            int tagsEndIndex = message.indexOf(TAG_SUFFIX);
            if (tagsEndIndex > 0) {
                tags = parseTags(message, tagsEndIndex);
                if (tags != null) {
                    message = message.substring(tagsEndIndex + 2);
                }
            }
        }
        tags = appendMDCTags(tags, MDCTags);
        Map<String, String> logMessageMap = new HashMap<>();
        logMessageMap.put(LOG_LEVEL_NAME, logLevel);
        if (throwable != null) {
            StringWriter sw = new StringWriter(256 * 1024);
            PrintWriter pw = new PrintWriter(sw);
            pw.print(message);
            throwable.printStackTrace(pw);
            message = sw.toString();
            logMessageMap.put(ERROR_NAME, throwable.getClass().getName());
        }
        /*if (StringUtils.isNotBlank(message)) {
            logMessageMap.put(LOG_DATA_NAME, message);
        }*/
        Map<String, String> defaultTags = getDefaultTags();
        if (defaultTags != null && !defaultTags.isEmpty()) {
            logMessageMap.putAll(defaultTags);
        }
        if (tags != null && !tags.isEmpty()) {
            logMessageMap.putAll(tags);
        }
        // put logs to CloudWatchLogs
        putLogToCloudWatch(message, logMessageMap);
       /* if (!logMessageMap.isEmpty()) {
            String logMessage = GSON.toJson(logMessageMap);
            MessageConsumer consumer = MessageManager.getInstance().getConsumer();
            consumer.processLogEvent(logMessage);
        }*/
    }

    private static void putLogToCloudWatch(String message, Map<String, String> tags) {
        if (!LogConfiguration.containsKey(CLOUD_WATCH_AGENT_SWITCH_NAME)
                || Boolean.TRUE.toString().equalsIgnoreCase(LogConfiguration.get(CLOUD_WATCH_AGENT_SWITCH_NAME))) {
            //put logs by agent
            putLogsByAgent(message, tags);
        } else {
            //put logs by api
            putLogsByApi(message, tags);
        }
    }

    private static void putLogsByApi(String message, Map<String, String> tags) {
        if (StringUtils.isNotBlank(message)) {
            tags.put(LOG_DATA_NAME, message);
        }
        if (!tags.isEmpty()) {
            String logMessage = GSON.toJson(tags);
            MessageConsumer consumer = MessageManager.getInstance().getConsumer();
            consumer.processLogEvent(logMessage);
        }
    }

    private static void putLogsByAgent(String message, Map<String, String> tags) {
        StringBuilder logData = new StringBuilder();
        if (tags != null && !tags.isEmpty()) {
            logData.append("tags:[");
            tags.forEach((key, value) -> {
                logData.append(key)
                        .append("=")
                        .append(value)
                        .append(",");
            });
            logData.append("] ");
        }
        if (StringUtils.isNotBlank(message)) {
            logData.append("logMessage:[")
                    .append(message)
                    .append("]");
        }
        System.out.println(logData);
    }

    protected static Map<String, String> getDefaultTags() {
        Map<String, String> defaultTags = new HashMap<>();
        try {
            // traceId
            if (StringUtils.isNotBlank(CapaContext.getTraceId())) {
                defaultTags.put(TRACE_ID_NAME, CapaContext.getTraceId());
            }
            // appId
            String appId = CapaFoundation.getAppId(FoundationType.TRIP);
            if(StringUtils.isNotBlank(appId)){
                defaultTags.put(APP_ID_NAME, appId);
            }
        }catch (Throwable e){

        }
        return defaultTags;
    }
}
