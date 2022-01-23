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
package group.rxcloud.capa.spi.aws.log.filter.logoutput;

import group.rxcloud.capa.spi.aws.log.appender.CapaLogEvent;
import group.rxcloud.capa.spi.aws.log.configuration.LogConfig;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.LogOutputFilter;
import group.rxcloud.capa.spi.aws.log.manager.CustomLogManager;
import group.rxcloud.capa.spi.aws.log.service.LogMetrics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LogOutputCountFilter implements LogOutputFilter {

    @Override
    public boolean logCanOutput(CapaLogEvent event) {
        return LogLimiter
                .logsCountLimit(event.getCapaLogLevel().orElse(null), event.getLoggerName(), event.getMessage(),
                        event.getThrowable());
    }


    static final class LogLimiter {

        private static final String UNDEFINED = "UNDEFINED";

        private static final ConcurrentHashMap<Long, OutputCount> COUNTER_MAP = new ConcurrentHashMap<>();

        private static final LinkedBlockingQueue<Long> KEYS = new LinkedBlockingQueue<>();

        private static final Timer CLEARNER = new Timer("log-alert-cleaner", true);

        static {
            CLEARNER.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        while (!KEYS.isEmpty() && isExpired(COUNTER_MAP.get(KEYS.peek()))) {
                            COUNTER_MAP.remove(KEYS.poll());
                        }
                    } catch (Throwable e) {
                        CustomLogManager.warn("Fail to clean alert log counter.", e);
                    }
                }
            }, TimeUnit.MINUTES.toMillis(1L), TimeUnit.MINUTES.toMillis(1L));
        }

        private LogLimiter() {
        }

        private static boolean isExpired(OutputCount outputCount) {
            long countMillis = LogConfig.TimeConfig.ALERT_LOG_COUNT_TIME.get();
            long alertMillis = LogConfig.TimeConfig.ALERT_LOG_IGNORE_IGNORE_TIME.get();

            return outputCount.alertTime() > alertMillis || outputCount.countTime() > countMillis;
        }

        public static boolean logsCountLimit(CapaLogLevel level, String loggerName, String message, Throwable ex) {
            CapaLogLevel restrictLevel = LogConfig.LevelConfig.ALERT_LOG_LEVEL.get();
            if (level.getLevel() < restrictLevel.getLevel()) {
                return true;
            }

            long key = encode(loggerName, message, ex);
            OutputCount outputCount = COUNTER_MAP.computeIfAbsent(key, k -> {
                KEYS.offer(k);
                return new OutputCount();
            });

            // check if the count is expired.
            if (isExpired(outputCount)) {
                outputCount.clear();
            }

            int restrictCount = LogConfig.IntConfig.ALERT_LOG_COUNT.get();
            int count = outputCount.increamentAndGet();
            if (count < restrictCount) {
                return true;
            }

            // need alert
            int alertCount = outputCount.startAlert() ? count : 1;
            LogMetrics.alertErrorLogLimiting(level.getLevelName(), loggerName,
                    ex == null ? UNDEFINED : ex.getClass().getCanonicalName(), key, alertCount);
            return false;
        }

        private static long encode(String loggerName, String message, Throwable ex) {
            StringWriter stringWriter = new StringWriter(256);
            PrintWriter writer = new PrintWriter(stringWriter);
            writer.print(loggerName);
            writer.print(message);
            if (ex != null) {
                writer.print(ex.getMessage());
                ex.printStackTrace(writer);
            }
            return stringWriter.toString().hashCode();
        }
    }

    static class OutputCount {

        final AtomicInteger count = new AtomicInteger();

        long startTimeMillis = System.currentTimeMillis();

        volatile long alertStartTimeMillis;

        void clear() {
            count.set(0);
            alertStartTimeMillis = 0L;
            startTimeMillis = System.currentTimeMillis();
        }

        long countTime() {
            return System.currentTimeMillis() - startTimeMillis;
        }

        long alertTime() {
            return alertStartTimeMillis > 0L ? System.currentTimeMillis() - alertStartTimeMillis : 0L;
        }

        boolean startAlert() {
            if (alertStartTimeMillis == 0L) {
                synchronized (count) {
                    if (alertStartTimeMillis == 0L) {
                        alertStartTimeMillis = System.currentTimeMillis();
                        return true;
                    }
                }
            }
            return false;
        }

        int increamentAndGet() {
            return count.incrementAndGet();
        }
    }
}
