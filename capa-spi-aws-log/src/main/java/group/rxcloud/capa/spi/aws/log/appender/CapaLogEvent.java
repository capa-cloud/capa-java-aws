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
 * @author: chenyijiang
 * @date: 2022/1/23 15:45
 */
public class CapaLogEvent {

    private Optional<CapaLogLevel> capaLogLevel;

    private String message;

    private String loggerName;

    private String threadName;

    private long time;

    private Throwable throwable;

    private Map<String, String> MDCTags;

    public CapaLogEvent(LogEvent event) {
        capaLogLevel = CapaLogLevel.toCapaLogLevel(event.getLevel().name());
        message = event.getMessage().getFormattedMessage();
        loggerName = event.getLoggerName();
        throwable = event.getThrown();
        threadName = event.getThreadName();
        time = event.getTimeMillis();
        ReadOnlyStringMap contextData = event.getContextData();
        MDCTags = contextData == null ? new HashMap<>() : contextData.toMap();
    }

    public CapaLogEvent(ILoggingEvent event) {
        capaLogLevel = CapaLogLevel.toCapaLogLevel(event.getLevel().levelStr);
        loggerName = event.getLoggerName();
        message = event.getFormattedMessage();
        ThrowableProxy throwableProxy = (ThrowableProxy) event.getThrowableProxy();
        if (throwableProxy != null) {
            throwable = throwableProxy.getThrowable();
        }
        threadName = event.getThreadName();
        time = event.getTimeStamp();
        MDCTags = event.getMDCPropertyMap();
    }

    public Map<String, String> getMDCTags() {
        return MDCTags;
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
