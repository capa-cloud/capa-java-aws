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
package group.rxcloud.capa.spi.aws.log.service;

import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.spi.aws.log.manager.CustomLogManager;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Log metrics helper.
 */
public final class LogMetrics {

    /**
     * The namespace for logging error.
     */
    private static final String LOG_NAMESPACE = "Fx.Log";

    /**
     * The metric name for logging error.
     */
    private static final String LOG_ERROR_METRIC_NAME = "log_failure_count";

    /**
     * The attribute key for appender type.
     */
    private static final String APPENDER_KEY = "appender";

    /**
     * The attribute key for ERROR name.
     */
    private static final String ERROR_KEY = "error_name";

    private static final AtomicBoolean METRIC_INIT = new AtomicBoolean(false);

    /**
     * Init an instance of {@link LongCounter}.
     */
    private static Optional<LongCounter> errorCounter = Optional.empty();

    private static Optional<Tracer> tracer = Optional.empty();

    private LogMetrics() {
    }

    public static Optional<Tracer> getTracer() {
        return tracer;
    }

    public static void recordLogError(String appenderName, String errorName) {
        try {
            getErrorCounter().ifPresent(counter -> {
                Attributes attributes = Attributes.builder()
                                                  .put(APPENDER_KEY, appenderName)
                                                  .put(ERROR_KEY, errorName)
                                                  .build();
                counter.add(1, attributes);
            });
        } catch (Throwable throwable) {
            // ignore any ERROR to keep the log function running.
        }
    }

    static Optional<LongCounter> getErrorCounter() {
        if (!METRIC_INIT.get()) {
            init();
        }
        return errorCounter;
    }

    private static void init() {
        synchronized (METRIC_INIT) {
            if (METRIC_INIT.compareAndSet(false, true)) {
                Mixer.telemetryHooksNullable().ifPresent(telemetryHooks -> {
                    try {
                        Meter meter = telemetryHooks.buildMeter(LOG_NAMESPACE).block();
                        errorCounter = Optional.ofNullable(meter.counterBuilder(LOG_ERROR_METRIC_NAME).build());
                        tracer = Optional.ofNullable(telemetryHooks.buildTracer(LOG_NAMESPACE).block());
                    } catch (Throwable ex) {
                        CustomLogManager.warn("Fail to init telemetry components.", ex);
                    }
                });
            }
        }
    }

}
