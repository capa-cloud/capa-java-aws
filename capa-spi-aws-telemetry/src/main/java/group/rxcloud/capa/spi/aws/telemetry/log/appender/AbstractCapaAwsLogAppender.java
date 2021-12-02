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
package group.rxcloud.capa.spi.aws.telemetry.log.appender;

import com.google.gson.Gson;
import group.rxcloud.capa.component.telemetry.context.CapaContext;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.infrastructure.hook.TelemetryHooks;
import group.rxcloud.capa.spi.aws.telemetry.log.service.CloudWatchLogsService;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import software.amazon.awssdk.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The abstract capa aws log appender.
 * <p>
 * TODO please move log to log module, not telemetry module
 */
public abstract class AbstractCapaAwsLogAppender {

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
    /**
     * The name of log level.
     */
    protected static final String LOG_LEVEL_NAME = "logLevel";
    /**
     * The name of log's _trace_id.
     */
    protected static final String TRACE_ID_NAME = "_trace_id";
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

    protected Map<String, String> parseTags(String message, int tagsEndIndex) {
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

    protected Map<String, String> appendMDCTags(Map<String, String> tags, Map<String, String> MDCTags) {
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

    protected void appendLogs(String message, Map<String, String> MDCTags, String logLevel) {
        if (StringUtils.isBlank(message)) {
            message = "";
        }
        Map<String, String> tags = new HashMap<>();
        tags.put(LOG_LEVEL_NAME, logLevel);
        if (message.startsWith(TAG_PREFIX)) {
            int tagsEndIndex = message.indexOf(TAG_SUFFIX);
            if (tagsEndIndex > 0) {
                tags = this.parseTags(message, tagsEndIndex);
                if (tags != null) {
                    message = message.substring(tagsEndIndex + 2);
                }
            }
        }
        tags = appendMDCTags(tags, MDCTags);

        Map<String, String> logMessageMap = new HashMap<>();
        if (StringUtils.isNotBlank(message)) {
            logMessageMap.put(LOG_DATA_NAME, message);
        }
        if (tags != null && !tags.isEmpty()) {
            logMessageMap.putAll(tags);
        }
        Map<String, String> defaultTags = this.getDefaultTags();
        if (defaultTags != null && !defaultTags.isEmpty()) {
            logMessageMap.putAll(defaultTags);
        }
        // put logs to CloudWatchLogs
        if (!logMessageMap.isEmpty()) {
            String logMessage = GSON.toJson(logMessageMap);
            CloudWatchLogsService.putLogEvent(logMessage);
        }
    }

    protected Map<String, String> getDefaultTags() {
        Map<String, String> defaultTags = new HashMap<>();
        // traceId
        if (StringUtils.isNotBlank(CapaContext.getTraceId())) {
            defaultTags.put(TRACE_ID_NAME, CapaContext.getTraceId());
        }
        return defaultTags;
    }
}
