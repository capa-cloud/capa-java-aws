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
import java.util.Map;
import java.util.Optional;

public final class LogAppendManager {

    /**
     * The name of log source data.
     */
    private static final String LOG_DATA_NAME = "_log_data";

    private static final String ERROR_NAME = "_error_name";

    /**
     * The name of log level.
     */
    private static final String LOG_LEVEL_NAME = "_log_level";

    /**
     * The name of logger.
     */
    private static final String LOGGER_NAME = "_logger_name";

    /**
     * The name of thread.
     */
    private static final String TRACE_ID_NAME = "_trace_id";

    /**
     * The name of log's _trace_id.
     */
    private static final String THREAD_NAME = "_thread_name";

    /**
     * closure log tag.
     */
    private static final String LOG_EVENT_NAME = "_log_event";

    /**
     * closure log tag.
     */
    private static final String THROTTLE = "throttle";

    /**
     * The time of log.
     */
    private static final String LOG_TIME = "_log_time";

    private static final String PUT_LOG_ASYNC_SWITCH = "putLogAsyncSwitch";

    /**
     * Init a {@link Gson} instance.
     */
    private static final Gson GSON = new Gson();


    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");

    private LogAppendManager() {
    }

    public static void appendLogs(CapaLogEvent event) {
        String logMessage = buildLog(event);
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

    public static String buildLog(CapaLogEvent event) {
        Map<String, String> tags = event.getTags();
        appendDefaultTags(event, tags);
        // put logs to CloudWatchLogs
        return GSON.toJson(tags);
    }

    private static void appendDefaultTags(CapaLogEvent event, Map<String, String> tags) {
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
            tags.put(TRACE_ID_NAME, traceId);
        }

        String message = event.getMessage();
        Throwable throwable = event.getThrowable();
        if (throwable != null) {
            StringWriter sw = new StringWriter(200 * 1024);
            PrintWriter pw = new PrintWriter(sw);
            pw.print(message);
            throwable.printStackTrace(pw);
            message = sw.toString();
            tags.put(ERROR_NAME, throwable.getClass().getName());
        }

        if (StringUtils.isNotBlank(message)) {
            tags.put(LOG_DATA_NAME, message);
        }

        tags.put(LOGGER_NAME, event.getLoggerName());
        tags.put(THREAD_NAME, event.getThreadName());
        tags.put(LOG_LEVEL_NAME, event.getLogLevel());
        tags.put(LOG_TIME,
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.getTime()), ZoneId.systemDefault())
                             .format(FORMATTER));
        if (event.isThrottle()) {
            tags.put(LOG_EVENT_NAME, THROTTLE);
        }
    }
}
