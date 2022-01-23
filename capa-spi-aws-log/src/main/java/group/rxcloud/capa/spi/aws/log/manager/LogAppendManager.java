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
import group.rxcloud.capa.spi.aws.log.appender.CapaLogEvent;
import group.rxcloud.capa.spi.aws.log.configuration.CapaComponentLogConfiguration;
import group.rxcloud.capa.spi.aws.log.handle.MessageConsumer;
import group.rxcloud.capa.spi.aws.log.handle.MessageManager;
import group.rxcloud.capa.spi.aws.log.service.LogMetrics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.Tracer;
import software.amazon.awssdk.utils.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class LogAppendManager {

    /**
     * Tag identifier prefix.
     */
    private static final String TAG_PREFIX = "[[";

    /**
     * Tag identifier suffix.
     */
    private static final String TAG_SUFFIX = "]]";

    /**
     * The name of log source data.
     */
    private static final String LOG_DATA_NAME = "logData";

    private static final String ERROR_NAME = "errorName";

    /**
     * The name of log level.
     */
    private static final String LOG_LEVEL_NAME = "logLevel";

    /**
     * The name of logger.
     */
    private static final String LOGGER_NAME = "loggerName";

    /**
     * The name of thread.
     */
    private static final String TRACE_ID_NAME = "_trace_id";

    /**
     * The name of log's _trace_id.
     */
    private static final String THREAD_NAME = "threadName";

    /**
     * The time of log.
     */
    private static final String LOG_TIME = "logTime";

    private static final String APP_ID_NAME = "appId";

    private static final String PUT_LOG_ASYNC_SWITCH = "putLogAsyncSwitch";


    /**
     * Init a {@link Gson} instance.
     */
    private static final Gson GSON = new Gson();


    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");

    private LogAppendManager() {
    }

    private static Map<String, String> parseTags(String message, int tagsEndIndex) {
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

    private static Map<String, String> appendMDCTags(Map<String, String> tags, Map<String, String> MDCTags) {
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

    public static void appendLogs(CapaLogEvent event) {
        Map<String, String> logMessageMap = parseLogs(event.getMessage(), event.getMDCTags(), event.getLogLevel(),
                event.getThrowable());
        logMessageMap.put(LOGGER_NAME, event.getLoggerName());
        logMessageMap.put(THREAD_NAME, event.getThreadName());
        logMessageMap.put(LOG_TIME,
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getTime()), ZoneId.systemDefault())
                             .format(FORMATTER));
        // put logs to CloudWatchLogs
        if (!logMessageMap.isEmpty()) {
            String logMessage = GSON.toJson(logMessageMap);
            Optional<CapaComponentLogConfiguration> configuration = CapaComponentLogConfiguration.getInstanceOpt();
            if (!configuration.isPresent()
                    || !configuration.get().containsKey(PUT_LOG_ASYNC_SWITCH)
                    || Boolean.FALSE.toString().equalsIgnoreCase(configuration.get().get(PUT_LOG_ASYNC_SWITCH))) {
                System.out.println(logMessage);
            } else {
                MessageConsumer consumer = MessageManager.getInstance().getConsumer();
                consumer.processLogEvent(logMessage);
            }
        }
    }

    public static Map<String, String> parseLogs(String message, Map<String, String> MDCTags, String logLevel,
                                                Throwable throwable) {
        if (StringUtils.isBlank(message)) {
            message = "";
        }
        Map<String, String> tags = new HashMap<>();
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
            StringWriter sw = new StringWriter(200 * 1024);
            PrintWriter pw = new PrintWriter(sw);
            pw.print(message);
            throwable.printStackTrace(pw);
            message = sw.toString();
            logMessageMap.put(ERROR_NAME, throwable.getClass().getName());
        }
        if (StringUtils.isNotBlank(message)) {
            logMessageMap.put(LOG_DATA_NAME, message);
        }
        Map<String, String> defaultTags = getDefaultTags();
        if (defaultTags != null && !defaultTags.isEmpty()) {
            logMessageMap.putAll(defaultTags);
        }
        if (tags != null && !tags.isEmpty()) {
            logMessageMap.putAll(tags);
        }
        return logMessageMap;
    }

    private static Map<String, String> getDefaultTags() {
        Map<String, String> defaultTags = new HashMap<>();
        // traceId
        String traceId = CapaContext.getTraceId();
        if (StringUtils.isNotBlank(traceId) || TraceId.getInvalid().equals(traceId)) {
            Optional<Tracer> tracer = LogMetrics.getTracer();
            if (tracer.isPresent()) {
                Span span = tracer.get().spanBuilder("CapaLog").startSpan();
                traceId = span.getSpanContext().getTraceId();
                span.end();
            }
        }
        if (StringUtils.isNotBlank(traceId)) {
            defaultTags.put(TRACE_ID_NAME, traceId);
        }

        // appId
        String appId = CapaFoundation.getAppId(FoundationType.TRIP);
        if (StringUtils.isNotBlank(appId)) {
            defaultTags.put(APP_ID_NAME, appId);
        }
        return defaultTags;
    }
}
