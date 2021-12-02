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

import group.rxcloud.capa.addons.id.generator.TripTraceIdGeneratePolicy;
import group.rxcloud.capa.component.telemetry.SamplerConfig;
import group.rxcloud.capa.component.telemetry.context.CapaContext;
import group.rxcloud.capa.spi.aws.telemetry.AwsCapaTelemetryProperties;
import io.opentelemetry.api.internal.ImmutableSpanContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Context Propagator for trip trace id.
 */
public class TraceIdRpcContextPropagator implements TextMapPropagator {

    private static final String TRACE_ID_KEY = AwsCapaTelemetryProperties.Settings.getTelemetryAwsTraceIdKey();

    private static final List<String> FIELDS = Collections.singletonList(TRACE_ID_KEY);

    private static final TraceIdRpcContextPropagator INSTANCE = new TraceIdRpcContextPropagator();

    public static TraceIdRpcContextPropagator getInstance() {
        return INSTANCE;
    }

    @Override
    public Collection<String> fields() {
        return FIELDS;
    }

    @Override
    public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
        setter.set(carrier, TRACE_ID_KEY, CapaContext.getTraceId());
    }

    @Override
    public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
        // Context will be inject in W3C implementation.
        String traceId = getter.get(carrier, TRACE_ID_KEY);

        if (traceId != null && !traceId.isEmpty()) {
            SpanContext currentExtracted = Span.fromContext(context).getSpanContext();
            boolean sampled = currentExtracted.isSampled();
            if (currentExtracted == SpanContext.getInvalid()) {
                sampled = SamplerConfig.DEFAULT_SUPPLIER.get().isTraceEnable();
            }

            SpanContext spanContext = ImmutableSpanContext.create(
                    traceId,
                    TripTraceIdGeneratePolicy.generate(),
                    sampled ? TraceFlags.getSampled()
                            : TraceFlags.getDefault(),
                    currentExtracted.getTraceState(),
                    false,
                    true);

            return context.with(Span.wrap(spanContext));
        }

        return context;
    }
}
