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

import io.opentelemetry.api.common.Attributes;
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
        StringBuilder builder = new StringBuilder("[[traceId=").append(spanData.getTraceId());
        appendTag(builder, "spanId", spanData.getSpanId());
        appendTag(builder, "parentSpanId", spanData.getParentSpanId());
        appendTag(builder, "spanName", spanData.getName());
        appendTag(builder, "status", spanData.getStatus().getStatusCode().name());
        if (spanData.getKind() != null) {
            appendTag(builder, "spanKind", spanData.getKind().name());
        }
        InstrumentationLibraryInfo libraryInfo = spanData.getInstrumentationLibraryInfo();
        if (libraryInfo != null) {
            appendTag(builder, "tracerName", libraryInfo.getName());
            appendTag(builder, "schemaUrl", libraryInfo.getSchemaUrl());
            appendTag(builder, "version", libraryInfo.getVersion());
        }
        builder.append("]]");
        return builder;
    }

    private static void appendTag(StringBuilder builder, String key, String value) {
        if (key != null && value != null) {
            builder.append(", ").append(key).append('=').append(value);
        }
    }

    private static void appendAttributes(String blankPrefix, StringBuilder builder, Attributes attributes) {
        builder.append('\n').append(blankPrefix).append("\"attributes\" = {");
        if (!attributes.isEmpty()) {
            attributes.forEach((k, v) -> {
                builder.append("\n\t").append(blankPrefix).append(k.getKey()).append('=').append(v).append(',');
            });
            builder.deleteCharAt(builder.length() - 1);
        }
        builder.append('\n').append(blankPrefix).append('}');
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
        appendAttributes("", builder, spanData.getAttributes());
        builder.append(",\n\"startNanos\": ").append(spanData.getStartEpochNanos());
        builder.append(",\n\"endNanos\": ").append(spanData.getEndEpochNanos());
        builder.append(",\n\"latencyNanos\": ").append(spanData.getEndEpochNanos() - spanData.getStartEpochNanos());
        if (!spanData.getLinks().isEmpty()) {
            builder.append(",\n\"links\": [");
            spanData.getLinks().forEach(l -> builder.append("\n\t").append(l).append(','));
            builder.deleteCharAt(builder.length() - 1);
            builder.append("\n]");
        }
        if (!spanData.getEvents().isEmpty()) {
            builder.append(",\n\"events\": [");
            for (EventData l : spanData.getEvents()) {
                builder.append("\n\t").append("{\n\t\t\"name\": \"").append(l.getName())
                       .append("\",\n\t\t\"time\": ")
                       .append(Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(l.getEpochNanos())).toString());
                appendAttributes("\t\t", builder, l.getAttributes());
                if ("exception".equals(l.getName())) {
                    failed = true;
                }
                builder.append("\n\t},");
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.append("\n]");
        }

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
