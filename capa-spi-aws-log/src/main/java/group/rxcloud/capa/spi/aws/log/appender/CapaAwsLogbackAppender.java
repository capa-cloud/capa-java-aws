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
package log.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import group.rxcloud.capa.component.log.CapaLogbackAppenderAgent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

import java.util.Map;

public class CapaAwsLogbackAppender extends AbstractCapaAwsLogAppender
        implements CapaLogbackAppenderAgent.CapaLogbackAppender {

    /**
     * The error type name of the logback appender.
     */
    protected static final String LOG_LOGBACK_APPENDER_ERROR_TYPE = "LogbackAppendLogsError";

    @Override
    public void append(ILoggingEvent event) {
        try {
            if (event == null || event.getLevel() == null) {
                return;
            }
            String message = event.getFormattedMessage();
            Map<String, String> MDCTags = event.getMDCPropertyMap();
            super.appendLogs(message, MDCTags, event.getLevel().levelStr);
        } catch (Exception e) {
            LONG_COUNTER.ifPresent(longCounter -> {
                longCounter.bind(Attributes.of(AttributeKey.stringKey(LOG_LOGBACK_APPENDER_ERROR_TYPE), e.getMessage()))
                        .add(COUNTER_NUM);
            });
        }
    }
}
