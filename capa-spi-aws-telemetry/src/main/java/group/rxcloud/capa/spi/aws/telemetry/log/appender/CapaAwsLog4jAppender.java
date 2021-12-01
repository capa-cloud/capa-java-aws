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
package group.rxcloud.capa.spi.aws.telemetry.log.appender;

import group.rxcloud.capa.component.log.agent.CapaLog4jAppenderAgent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import java.util.HashMap;
import java.util.Map;

public class CapaAwsLog4jAppender extends AbstractCapaAwsLogAppender
        implements CapaLog4jAppenderAgent.CapaLog4jAppender {
    /**
     * The error type name of the log4j appender.
     */
    protected static final String LOG_LOG4J_APPENDER_ERROR_TYPE = "Log4jAppendLogsError";

    static {
        PluginManager.addPackage("group.rxcloud.capa.spi.aws.telemetry.log.appender");
    }

    @Override
    public void appendLog(LogEvent event) {
        try {
            if (event == null
                    || event.getLevel() == null
                    || event.getMessage() == null) {
                return;
            }
            String message = event.getMessage().getFormattedMessage();
            ReadOnlyStringMap contextData = event.getContextData();
            Map<String, String> MDCTags = contextData == null ? new HashMap<>() : contextData.toMap();
            super.appendLogs(message, MDCTags, event.getLevel().name());
        } catch (Exception e) {
            LONG_COUNTER.ifPresent(longCounter -> {
                longCounter.bind(Attributes.of(AttributeKey.stringKey(LOG_LOG4J_APPENDER_ERROR_TYPE), e.getMessage()))
                        .add(COUNTER_NUM);
            });
        }
    }
}
