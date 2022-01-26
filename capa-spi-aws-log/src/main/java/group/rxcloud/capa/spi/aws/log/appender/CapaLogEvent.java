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
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Capa log event.
 */
public class CapaLogEvent {

    /**
     * Tag identifier prefix.
     */
    private static final String TAG_PREFIX = "[[";

    /**
     * Tag identifier suffix.
     */
    private static final String TAG_SUFFIX = "]]";

    private Optional<CapaLogLevel> capaLogLevel;

    private String message;

    private String loggerName;

    private String threadName;

    private long time;

    private Throwable throwable;

    private Map<String, String> tags;

    private boolean throttle;

    public CapaLogEvent(LogEvent event) {
        capaLogLevel = CapaLogLevel.toCapaLogLevel(event.getLevel().name());
        String originMessage = event.getMessage().getFormattedMessage();
        message = deleteTags(originMessage);
        loggerName = event.getLoggerName();
        throwable = event.getThrown();
        threadName = event.getThreadName();
        time = event.getTimeMillis();
        ReadOnlyStringMap contextData = event.getContextData();
        Map<String, String> MDCTags = contextData == null ? null : contextData.toMap();
        tags = mergeTags(originMessage, MDCTags);
    }

    public CapaLogEvent(String loggerName, CapaLogLevel level, String message, Throwable throwable) {
        capaLogLevel = Optional.ofNullable(level);
        this.message = deleteTags(message);
        this.loggerName = loggerName;
        this.throwable = throwable;
        threadName = Thread.currentThread().getName();
        time = System.currentTimeMillis();
        tags = mergeTags(message, null);
    }

    public CapaLogEvent(ILoggingEvent event) {
        capaLogLevel = CapaLogLevel.toCapaLogLevel(event.getLevel().levelStr);
        loggerName = event.getLoggerName();
        String originMessage = event.getFormattedMessage();
        message = deleteTags(originMessage);
        ThrowableProxy throwableProxy = (ThrowableProxy) event.getThrowableProxy();
        if (throwableProxy != null) {
            throwable = throwableProxy.getThrowable();
        }
        threadName = event.getThreadName();
        time = event.getTimeStamp();
        tags = mergeTags(originMessage, event.getMDCPropertyMap());
    }

    private static String deleteTags(String message) {
        if (message.startsWith(TAG_PREFIX)) {
            int tagsEndIndex = message.indexOf(TAG_SUFFIX);
            if (tagsEndIndex > 0) {
                return message.substring(tagsEndIndex + 2);
            }
        }
        return message;
    }

    private static Map<String, String> mergeTags(String message, Map<String, String> MDCTags) {
        Map<String, String> tags = new HashMap<>();
        if (message.startsWith(TAG_PREFIX)) {
            int tagsEndIndex = message.indexOf(TAG_SUFFIX);
            if (tagsEndIndex > 0) {
                parseTags(message, tagsEndIndex, tags);
            }
        }
        if (MDCTags != null && !MDCTags.isEmpty()) {
            tags.putAll(MDCTags);
        }
        return tags;
    }

    private static void parseTags(String message, int tagsEndIndex, Map<String, String> tags) {
        int tagStart = 2;
        while (tagStart < tagsEndIndex) {
            int tagEnd = message.indexOf(',', tagStart);
            if (tagEnd < 0 || tagEnd > tagsEndIndex) {
                tagEnd = tagsEndIndex;
            }
            int equalIndex = message.indexOf('=', tagStart);
            if (equalIndex > tagStart && equalIndex < tagEnd - 1) {
                String key = message.substring(tagStart, equalIndex);
                String value = message.substring(equalIndex + 1, tagEnd);
                tags.put(key, value);
            }
            tagStart = tagEnd + 1;
        }
    }

    public boolean isThrottle() {
        return throttle;
    }

    public void setThrottle(boolean throttle) {
        this.throttle = throttle;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getTime() {
        return time;
    }

    public Optional<CapaLogLevel> getCapaLogLevel() {
        return capaLogLevel;
    }

    @Nonnull
    public String getLogLevel() {
        return capaLogLevel.map(CapaLogLevel::getLevelName).orElse("UNDEFINED");
    }

    public String getMessage() {
        return message;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
