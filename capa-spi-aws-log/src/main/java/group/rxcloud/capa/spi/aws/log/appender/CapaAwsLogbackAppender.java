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
import group.rxcloud.capa.spi.aws.log.filter.factory.LogOutputFactoryFilter;
import group.rxcloud.capa.spi.aws.log.manager.CustomLogManager;
import group.rxcloud.capa.spi.aws.log.manager.LogAppendManager;
import group.rxcloud.capa.spi.aws.log.service.LogMetrics;
import group.rxcloud.capa.spi.log.CapaLogbackAppenderSpi;

public class CapaAwsLogbackAppender extends CapaLogbackAppenderSpi {

    /**
     * The error type name of the logback appender.
     */
    protected static final String LOG_LOGBACK_APPENDER_ERROR_TYPE = "LogbackAppendLogsError";

    @Override
    public void appendLog(ILoggingEvent event) {
        try {
            if (event == null || event.getLevel() == null) {
                return;
            }

            CapaLogEvent capaLogEvent = new CapaLogEvent(event);
            if (LogOutputFactoryFilter.logCanOutput(capaLogEvent)) {
                LogAppendManager.appendLogs(capaLogEvent);
            }
        } catch (Exception e) {
            try {
                CustomLogManager.error("CapaAwsLogbackAppender appender log error.", e);
                LogMetrics.recordLogError(LOG_LOGBACK_APPENDER_ERROR_TYPE, e.getClass().getCanonicalName());
            } catch (Throwable ex) {
            }
        }
    }

}
