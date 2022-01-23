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

import group.rxcloud.capa.spi.aws.log.filter.factory.LogOutputFactoryFilter;
import group.rxcloud.capa.spi.aws.log.manager.CustomLogManager;
import group.rxcloud.capa.spi.aws.log.manager.LogAppendManager;
import group.rxcloud.capa.spi.aws.log.service.LogMetrics;
import group.rxcloud.capa.spi.log.CapaLog4jAppenderSpi;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;

public class CapaAwsLog4jAppender extends CapaLog4jAppenderSpi {

    /**
     * The error type name of the log4j appender.
     */
    protected static final String LOG_LOG4J_APPENDER_ERROR_TYPE = "Log4jAppendLogsError";

    static {
        PluginManager.addPackage("group.rxcloud.capa.spi.aws.log.appender");
    }

    @Override
    public void appendLog(LogEvent event) {
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
                CustomLogManager.error("CapaAwsLog4jAppender appender log error.", e);
                //Enhance function without affecting function
                LogMetrics.recordLogError(LOG_LOG4J_APPENDER_ERROR_TYPE, e.getClass().getCanonicalName());
            } catch (Throwable ex) {
            }
        }
    }
}
