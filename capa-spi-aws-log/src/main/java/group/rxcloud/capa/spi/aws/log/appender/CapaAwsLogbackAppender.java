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
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.factory.LogOutputFactoryFilter;
import group.rxcloud.capa.spi.aws.log.manager.CustomLogManager;
import group.rxcloud.capa.spi.aws.log.manager.LogAppendManager;
import group.rxcloud.capa.spi.log.CapaLogbackAppenderSpi;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * The namespace for logging error.
     * TODO Set variables to common variables
     */
    private static final String LOG_ERROR_NAMESPACE = "CloudWatchLogs";

    /**
     * The metric name for logging error.
     * TODO Set variables to common variables
     */
    private static final String LOG_ERROR_METRIC_NAME = "LogError";

    private static final AtomicBoolean METRIC_INIT = new AtomicBoolean(false);

    /**
     * Init an instance of {@link LongCounter}.
     */
    protected static Optional<LongCounter> LONG_COUNTER = Optional.empty();

    static Optional<LongCounter> getCounterOpt() {
        if (METRIC_INIT.get()) {
            return LONG_COUNTER;
        }
        synchronized (METRIC_INIT) {
            if (METRIC_INIT.compareAndSet(false, true)) {
                Mixer.telemetryHooksNullable().ifPresent(telemetryHooks -> {
                    Meter meter = telemetryHooks.buildMeter(LOG_ERROR_NAMESPACE).block();
                    LongCounter longCounter = meter.counterBuilder(LOG_ERROR_METRIC_NAME).build();
                    LONG_COUNTER = Optional.ofNullable(longCounter);
                });
            }
        }
        return LONG_COUNTER;
    }

    @Override
    public void appendLog(ILoggingEvent event) {
        try {
            if (event == null || event.getLevel() == null) {
                return;
            }
            Optional<CapaLogLevel> capaLogLevel = CapaLogLevel.toCapaLogLevel(event.getLevel().levelStr);
            if (capaLogLevel.isPresent() && LogOutputFactoryFilter.logCanOutput(capaLogLevel.get())) {
                String message = event.getFormattedMessage();
                Map<String, String> MDCTags = event.getMDCPropertyMap();
                LogAppendManager.appendLogs(message, MDCTags, event.getLoggerName(), event.getThreadName(),
                        event.getLevel().levelStr, event.getTimeStamp(), getThrowable(event));
            }
        } catch (Exception e) {
            CustomLogManager.error("CapaAwsLogbackAppender appender log error.", e);
            getCounterOpt().ifPresent(longCounter -> {
                try {
                    //Enhance function without affecting function
                    longCounter.bind(Attributes
                            .of(AttributeKey.stringKey(LOG_LOGBACK_APPENDER_ERROR_TYPE), e.getClass().getName()))
                               .add(COUNTER_NUM);
                } catch (Throwable ex) {
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
