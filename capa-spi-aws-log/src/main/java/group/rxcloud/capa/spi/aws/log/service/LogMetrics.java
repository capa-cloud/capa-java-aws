package group.rxcloud.capa.spi.aws.log.service;

import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.spi.aws.log.manager.CustomLogManager;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author: chenyijiang
 * @date: 2022/1/19 13:42
 */
public final class LogMetrics {

    /**
     * The namespace for logging error.
     * TODO Set variables to common variables
     */
    private static final String LOG_NAMESPACE = "Fx.Log";

    /**
     * The metric name for logging error.
     * TODO Set variables to common variables
     */
    private static final String LOG_ERROR_METRIC_NAME = "log_failure_count";

    /**
     * The metric name for logging error.
     * TODO Set variables to common variables
     */
    private static final String LOG_ALERT_METRIC_NAME = "circuit_break_log_count";

    /**
     * The attribute key for appender type.
     */
    private static final String APPENDER_KEY = "appender";

    /**
     * The attribute key for ERROR name.
     */
    private static final String ERROR_KEY = "error_name";

    /**
     * The attribute key for ERROR name.
     */
    private static final String LOGGER_NAME_KEY = "logger_name";


    /**
     * The attribute key for ERROR name.
     */
    private static final String ERROR_HASH_KEY = "error_hash";

    private static final String SDK_INFO_KEY = "sdk_info";

    private static final String SDK_VERSION_KEY = "sdk_version";

    /**
     * The attribute key for ERROR name.
     */
    private static final String LOG_LEVEL_KEY = "log_level";

    private static final AtomicBoolean METRIC_INIT = new AtomicBoolean(false);

    /**
     * Init an instance of {@link LongCounter}.
     */
    private static Optional<LongCounter> errorCounter = Optional.empty();

    /**
     * Init an instance of {@link LongCounter}.
     */
    private static Optional<LongCounter> alertCounter = Optional.empty();

    private static Optional<Tracer> tracer = Optional.empty();

    private LogMetrics() {
    }

    public static Optional<Tracer> getTracer() {
        return tracer;
    }

    public static void recordLogError(String appenderName, String errorName) {
        try {
            getErrorCounter().ifPresent(counter -> {
                Attributes attributes = Attributes.builder()
                                                  .put(APPENDER_KEY, appenderName)
                                                  .put(ERROR_KEY, errorName)
                                                  .build();
                counter.add(1, attributes);
            });
        } catch (Throwable throwable) {
            // ignore any ERROR to keep the log function running.
        }
    }

    public static void alertErrorLogLimiting(String logLevel, String loggerName, String errorName, long hash, int count) {
        try {
            getAlertCounter().ifPresent(counter -> {
                Attributes attributes = Attributes.builder()
                                                  .put(ERROR_KEY, errorName)
                                                  .put(LOGGER_NAME_KEY, loggerName)
                                                  .put(LOG_LEVEL_KEY, logLevel)
                                                  .put(ERROR_HASH_KEY, hash)
                                                  .put(SDK_INFO_KEY, "capa_java_v1.11.13.4")
                                                  .build();
                counter.add(count, attributes);
            });
        } catch (Throwable throwable) {
            // ignore any ERROR to keep the log function running.
        }
    }

    static Optional<LongCounter> getErrorCounter() {
        if (!METRIC_INIT.get()) {
            init();
        }
        return errorCounter;
    }

    static Optional<LongCounter> getAlertCounter() {
        if (!METRIC_INIT.get()) {
            init();
        }
        return alertCounter;
    }

    private static void init() {
        synchronized (METRIC_INIT) {
            if (METRIC_INIT.compareAndSet(false, true)) {
                Mixer.telemetryHooksNullable().ifPresent(telemetryHooks -> {
                    try {
                        Meter meter = telemetryHooks.buildMeter(LOG_NAMESPACE).block();
                        errorCounter = Optional.ofNullable(meter.counterBuilder(LOG_ERROR_METRIC_NAME).build());
                        alertCounter = Optional.ofNullable(meter.counterBuilder(LOG_ALERT_METRIC_NAME).build());
                        tracer = Optional.ofNullable(telemetryHooks.buildTracer(LOG_NAMESPACE).block());
                    } catch (Throwable ex) {
                        CustomLogManager.warn("Fail to init telemetry components.", ex);
                    }
                });
            }
        }
    }

}
