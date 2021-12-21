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
package group.rxcloud.capa.spi.aws.log.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.infrastructure.hook.TelemetryHooks;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.manager.LogAppendManager;
import group.rxcloud.capa.spi.aws.log.manager.LogManager;
import group.rxcloud.capa.spi.log.CapaLogbackAppenderSpi;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Map;
import java.util.Optional;

public class CapaAwsLogbackAppender extends CapaLogbackAppenderSpi {

    /**
     * The error type name of the logback appender.
     */
    protected static final String LOG_LOGBACK_APPENDER_ERROR_TYPE = "LogbackAppendLogsError";

    /**
     * Number of counts each time.
     */
    protected static final Integer COUNTER_NUM = 1;
    /**
     * The instance of the {@link TelemetryHooks}.
     */
    private static final Optional<TelemetryHooks> TELEMETRY_HOOKS;
    /**
     * The namespace for logging error.
     * TODO Set variables to common variables
     */
    private static final String LOG_ERROR_NAMESPACE = "CloudWatchLogs";
    /**
     * The metric name for logging error.
     * TODO Set variables to common variables
     */
    private static final String LOG_ERROR_METRIC_NAME = "LogError";
    /**
     * Init an instance of {@link LongCounter}.
     */
    protected static Optional<LongCounter> LONG_COUNTER = Optional.empty();

    static {
        TELEMETRY_HOOKS = Mixer.telemetryHooksNullable();
        TELEMETRY_HOOKS.ifPresent(telemetryHooks -> {
            Meter meter = telemetryHooks.buildMeter(LOG_ERROR_NAMESPACE).block();
            LongCounter longCounter = meter.counterBuilder(LOG_ERROR_METRIC_NAME).build();
            LONG_COUNTER = Optional.ofNullable(longCounter);
        });
    }

    @Override
    public void appendLog(ILoggingEvent event) {
        try {
            if (event == null || event.getLevel() == null) {
                return;
            }
            Optional<CapaLogLevel> capaLogLevel = CapaLogLevel.toCapaLogLevel(event.getLevel().levelStr);
            if (capaLogLevel.isPresent() && LogManager.logsCanOutput(capaLogLevel.get())) {
                String message = event.getFormattedMessage();
                Map<String, String> MDCTags = event.getMDCPropertyMap();
                LogAppendManager.appendLogs(message, MDCTags, event.getLevel().levelStr, getThrowable(event));
            }
        } catch (Exception e) {
            LONG_COUNTER.ifPresent(longCounter -> {
                try {
                    //Enhance function without affecting function
                    longCounter.bind(Attributes.of(AttributeKey.stringKey(LOG_LOGBACK_APPENDER_ERROR_TYPE), e.getClass().getName()))
                            .add(COUNTER_NUM);
                } finally {
                }
            });
        }
    }

    private Throwable getThrowable(ILoggingEvent event) {
        ThrowableProxy throwableProxy = (ThrowableProxy) event.getThrowableProxy();
        if (throwableProxy != null) {
            return throwableProxy.getThrowable();
        }
        return null;
    }

}
