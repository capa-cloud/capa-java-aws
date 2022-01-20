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

import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.factory.LogOutputFactoryFilter;
import group.rxcloud.capa.spi.aws.log.manager.CustomLogManager;
import group.rxcloud.capa.spi.aws.log.manager.LogAppendManager;
import group.rxcloud.capa.spi.log.CapaLog4jAppenderSpi;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class CapaAwsLog4jAppender extends CapaLog4jAppenderSpi {

    /**
     * The error type name of the log4j appender.
     */
    protected static final String LOG_LOG4J_APPENDER_ERROR_TYPE = "Log4jAppendLogsError";

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

    static {
        PluginManager.addPackage("group.rxcloud.capa.spi.aws.log.appender");
    }

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
    public void appendLog(LogEvent event) {
        try {
            if (event == null
                    || event.getLevel() == null
                    || event.getMessage() == null) {
                return;
            }
            Optional<CapaLogLevel> capaLogLevel = CapaLogLevel.toCapaLogLevel(event.getLevel().name());
            if (capaLogLevel.isPresent() && LogOutputFactoryFilter.logCanOutput(capaLogLevel.get())) {
                String message = event.getMessage().getFormattedMessage();
                ReadOnlyStringMap contextData = event.getContextData();
                Map<String, String> MDCTags = contextData == null ? new HashMap<>() : contextData.toMap();
                LogAppendManager.appendLogs(message, MDCTags, event.getLoggerName(), event.getThreadName(),
                        event.getLevel().name(), event.getTimeMillis(), event.getThrown());
            }
        } catch (Exception e) {
            try {
                CustomLogManager.error("CapaAwsLog4jAppender appender log error.", e);
                //Enhance function without affecting function
                getCounterOpt().ifPresent(longCounter -> {
                    longCounter.bind(Attributes
                            .of(AttributeKey.stringKey(LOG_LOG4J_APPENDER_ERROR_TYPE), e.getClass().getName()))
                               .add(COUNTER_NUM);
                });
            } catch (Throwable ex) {
            }

        }
    }
}
