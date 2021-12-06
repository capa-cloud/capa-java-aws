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
package group.rxcloud.capa.spi.aws.telemetry.trace;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Configured in capa-tracer.json
 */
public class LogSpanProcessor implements SpanProcessor {

    private static final Logger log = LoggerFactory.getLogger(LogSpanProcessor.class);

    private static StringBuilder addBasicTags(SpanData spanData) {
        StringBuilder builder = new StringBuilder("[[_trace_id=").append(spanData.getTraceId());
        appendTag(builder, "_span_id", spanData.getSpanId());
        appendTag(builder, "_parent_span_id", spanData.getParentSpanId());
        appendTag(builder, "_span_name", spanData.getName());
        appendTag(builder, "_span_status", spanData.getStatus().getStatusCode().name());
        if (spanData.getKind() != null) {
            appendTag(builder, "_span_kind", spanData.getKind().name());
        }
        InstrumentationLibraryInfo libraryInfo = spanData.getInstrumentationLibraryInfo();
        if (libraryInfo != null) {
            appendTag(builder, "_tracer_name", libraryInfo.getName());
            appendTag(builder, "_tracer_schema_url", libraryInfo.getSchemaUrl());
            appendTag(builder, "_tracer_version", libraryInfo.getVersion());
        }
        appendTag(builder, "_span_start_nanos", String.valueOf(spanData.getStartEpochNanos()));
        appendTag(builder, "_span_end_nanos", String.valueOf(spanData.getEndEpochNanos()));
        appendTag(builder, "_span_latency_nanos", String.valueOf(spanData.getEndEpochNanos() - spanData.getStartEpochNanos()));
        if (!spanData.getAttributes().isEmpty()) {
            spanData.getAttributes().forEach((k, v) -> {
                appendTag(builder, "attr." + k.getKey(), String.valueOf(v));
            });
        }
        builder.append("]]");
        return builder;
    }

    private static void appendTag(StringBuilder builder, String key, String value) {
        if (key != null && value != null) {
            builder.append(',').append(key).append('=').append(value);
        }
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        SpanData spanData = span.toSpanData();
        boolean failed = spanData.getStatus().getStatusCode() == StatusCode.ERROR;

        StringBuilder builder = addBasicTags(spanData);
        builder.append('{');
        String sep = "";
        if (!spanData.getLinks().isEmpty()) {
            builder.append("\"links\": [");
            spanData.getLinks().forEach(l -> builder.append(' ').append(l).append(','));
            builder.deleteCharAt(builder.length() - 1);
            builder.append(" ]");
            sep = ", ";
        }
        if (!spanData.getEvents().isEmpty()) {
            builder.append(sep).append("\"events\": [");
            for (EventData l : spanData.getEvents()) {
                builder.append(' ').append("{ \"name\": \"").append(l.getName())
                       .append("\", \"time\": \"")
                       .append(Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(l.getEpochNanos())).toString()).append('"');
                if (!l.getAttributes().isEmpty()) {
                    builder.append(", \"attributes\": {");
                    l.getAttributes().forEach((k,v) -> builder.append('"').append(k.getKey()).append("\": \"").append(v).append("\","));
                    builder.deleteCharAt(builder.length() - 1)
                    .append('}');
                }
                if ("exception".equals(l.getName())) {
                    failed = true;
                }
                builder.append(" },");
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.append(" ]");
        }

        builder.append('}');
        if (failed) {
            log.error(builder.toString());
        } else {
            log.info(builder.toString());
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }
}
