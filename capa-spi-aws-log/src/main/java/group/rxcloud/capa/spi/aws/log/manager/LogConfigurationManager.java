package group.rxcloud.capa.spi.aws.log.manager;


import com.google.common.collect.Lists;
import group.rxcloud.capa.component.CapaLogProperties;
import group.rxcloud.capa.infrastructure.hook.ConfigurationHooks;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.cloudruntimes.domain.core.configuration.SubConfigurationResp;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class LogConfigurationManager {
    private static final String OUTPUT_LOG_LEVEL_CONFIG_KEY = "outputLogLevel";

    private static final AtomicBoolean CONFIG_INIT = new AtomicBoolean(false);
    private static Optional<CapaLogConfiguration> capaLogConfiguration = Optional.empty();

    static Optional<CapaLogConfiguration> getCapaLogConfiguration() {
        if (CONFIG_INIT.get()) {
            return capaLogConfiguration;
        }
        synchronized (CONFIG_INIT) {
            if (CONFIG_INIT.compareAndSet(false, true)) {
                Mixer.configurationHooksNullable().ifPresent(hooks -> {
                    // TODO: 2021/12/3 Use Configuration extension api to get merged file.
                    try {
                        capaLogConfiguration = Optional.ofNullable(new CapaLogConfiguration(hooks.defaultConfigurationAppId(), CapaLogProperties.Settings.getCenterConfigAppId()));
                    } catch (Throwable throwable) {
                        CustomLogManager.warn("Mixer configurationHooksNullable error.", throwable);
                        Mixer.telemetryHooksNullable().ifPresent(telemetryHooks -> {
                            Meter meter = telemetryHooks.buildMeter("LogsConfiguration").block();
                            LongCounter longCounter = meter.counterBuilder("LogsError").build();
                            Optional<LongCounter> longCounterOptional = Optional.ofNullable(longCounter);
                            longCounterOptional.ifPresent(counter -> {
                                longCounter.bind(Attributes.of(AttributeKey.stringKey("LogsConfigurationError"), throwable.getMessage()))
                                        .add(1);
                            });
                        });
                    }
                });
            }
        }
        return capaLogConfiguration;
    }

    public static Long getLogOutputValidTime() {
        if (getCapaLogConfiguration().isPresent()
                && getCapaLogConfiguration().get().getOutputLogValidTime() != null) {
            return getCapaLogConfiguration().get().getOutputLogValidTime();
        }
        return System.currentTimeMillis();
    }

    public static CapaLogLevel getCustomOutputLogLevel() {
        if (getCapaLogConfiguration().isPresent()
                && getCapaLogConfiguration().get().getCapaLogConfiguration() != null
                && getCapaLogConfiguration().get().getCapaLogConfiguration().containsKey(OUTPUT_LOG_LEVEL_CONFIG_KEY)) {
            Optional<CapaLogLevel> capaLogLevel = Optional.ofNullable(CapaLogLevel.valueOf(capaLogConfiguration.get().getCapaLogConfiguration().get(OUTPUT_LOG_LEVEL_CONFIG_KEY)));
            if (capaLogLevel.isPresent()) {
                return capaLogLevel.get();
            }
        }
        return CapaLogLevel.ERROR;
    }


    public static boolean containsKey(String key) {
        try {
            return getCapaComponentConfiguration().containsKey(key);
        } catch (Exception e) {
            return false;
        }
    }

    public static String get(String key) {
        try {
            return getCapaComponentConfiguration().get(key);
        } catch (Exception e) {
            return "";
        }
    }

    private static Map<String, String> getCapaComponentConfiguration() {
        if (getCapaLogConfiguration().isPresent()
                && getCapaLogConfiguration().get().getCapaComponentLogConfiguration() != null) {
            return getCapaLogConfiguration().get().getCapaComponentLogConfiguration();
        }
        return new HashMap<>();
    }

    static class CapaLogConfiguration {
        private static final String CAPA_COMPONENT_LOG_CONFIGURATION_FILE_NAME = "capa-component-log-configuration.properties";
        private static final String CAPA_LOG_CONFIGURATION_FILE_NAME = "capa-log-configuration.properties";
        private static final String OUTPUT_LOG_LEVEL_CONFIG_KEY = "outputLogLevel";
        private static final String OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY = "outputLogEffectiveTime";
        private final Logger log = LoggerFactory.getLogger(CapaLogConfiguration.class);
        private final Object lock = new Object();
        private final AtomicReferenceArray<Map> capaComponentLogConfigurationProperties;
        private final Integer DEFAULT_OUTPUT_LOG_EFFECTIVE_TIME = 10;
        private Long outputLogValidTime = System.currentTimeMillis();
        private volatile Map<String, String> capaComponentLogConfiguration;
        private volatile Map<String, String> capaLogConfiguration;
        // currentLogLevel is configured by capa-component-log-configuration.properties, default is ERROR.
        private volatile CapaLogLevel currentLogLevel = CapaLogLevel.ERROR;
        private volatile Integer currentLogEffectiveTime = 10;

        public CapaLogConfiguration(String applicationAppId, String capaComponentAppId) {
            capaComponentLogConfigurationProperties = new AtomicReferenceArray<>(2);
            capaComponentLogConfiguration = new HashMap<>();
            capaLogConfiguration = new HashMap<>();
            Mixer.configurationHooksNullable().ifPresent(hooks -> {
                // subscribe capa-compoment-log-configuration.properties
                List<String> appIds = Lists.newArrayList(applicationAppId, capaComponentAppId);
                for (int i = 0; i < appIds.size(); i++) {
                    try {
                        subscribeCapaComponentLogConfigurationByAppId(hooks, appIds.get(i), i);
                    } catch (Throwable throwable) {
                        log.warn("Fail to subscribe config for app id " + appIds.get(i) + ", index " + i, throwable);
                    }
                }
                // subscribe capa-log-configuration.properties
                subscribeCapaLogConfigurationByAppId(hooks, capaComponentAppId);
            });
        }

        public Map<String, String> getCapaComponentLogConfiguration() {
            return capaComponentLogConfiguration;
        }

        public Map<String, String> getCapaLogConfiguration() {
            return capaLogConfiguration;
        }

        public Long getOutputLogValidTime() {
            return outputLogValidTime;
        }

        private void subscribeCapaComponentLogConfigurationByAppId(ConfigurationHooks configurationHooks, String appId, int index) {
            String storeName = configurationHooks.registryStoreNames().get(0);

            Flux<SubConfigurationResp<Map>> configFlux = configurationHooks.subscribeConfiguration(
                    storeName,
                    appId,
                    Collections.singletonList(CAPA_COMPONENT_LOG_CONFIGURATION_FILE_NAME),
                    null,
                    "",
                    "",
                    TypeRef.get(Map.class));

            // FIXME: 2021/12/3 random callback?
            configFlux.subscribe(resp -> {
                synchronized (lock) {
                    if (!resp.getItems().isEmpty()) {
                        capaComponentLogConfigurationProperties.set(index, resp.getItems().get(0).getContent());
                    } else {
                        capaComponentLogConfigurationProperties.set(index, null);
                    }

                    Map<String, String> merged = new HashMap<>();
                    for (int i = 0; i < capaComponentLogConfigurationProperties.length(); i++) {
                        Map item = capaComponentLogConfigurationProperties.get(i);
                        if (item != null) {
                            item.forEach((k, v) -> merged.putIfAbsent(String.valueOf(k), String.valueOf(v)));
                        }
                    }
                    this.capaComponentLogConfiguration = merged;

                    // update current output log level valid time
                    if (capaComponentLogConfiguration.isEmpty()) {
                        this.currentLogLevel = CapaLogLevel.ALL;
                    } else if (capaComponentLogConfiguration.containsKey(OUTPUT_LOG_LEVEL_CONFIG_KEY)) {
                        Optional.ofNullable(CapaLogLevel.valueOf(capaComponentLogConfiguration.get(OUTPUT_LOG_LEVEL_CONFIG_KEY)))
                                .ifPresent(outputLogLevel -> {
                                    if (!this.currentLogLevel.equals(outputLogLevel)) {
                                        this.updateOutputLogValidTime();
                                    }
                                });
                    }
                }
            });
        }

        private void subscribeCapaLogConfigurationByAppId(ConfigurationHooks configurationHooks, String appId) {
            String storeName = configurationHooks.registryStoreNames().get(0);
            Flux<SubConfigurationResp<Map>> configFlux = configurationHooks.subscribeConfiguration(
                    storeName,
                    appId,
                    Collections.singletonList(CAPA_LOG_CONFIGURATION_FILE_NAME),
                    null,
                    "",
                    "",
                    TypeRef.get(Map.class));

            configFlux.subscribe(resp -> {
                synchronized (lock) {
                    Map<String, String> merged = new HashMap<>();
                    if (!resp.getItems().isEmpty()
                            && !CollectionUtils.isNullOrEmpty(resp.getItems().get(0).getContent())) {
                        merged.putAll(resp.getItems().get(0).getContent());
                    }
                    this.capaLogConfiguration = merged;
                    // update output log level and the valid time
                    if (!capaLogConfiguration.isEmpty()
                            && capaComponentLogConfiguration.containsKey(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)) {
                        Optional.ofNullable(Integer.parseInt(capaComponentLogConfiguration.get(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)))
                                .ifPresent(outputLogEffectiveTime -> {
                                    if (!this.currentLogEffectiveTime.equals(outputLogEffectiveTime)) {
                                        this.updateOutputLogValidTime();
                                    }
                                });
                    }
                }
            });
        }

        private void updateOutputLogValidTime() {
            this.currentLogEffectiveTime = DEFAULT_OUTPUT_LOG_EFFECTIVE_TIME;
            if (capaLogConfiguration != null && capaLogConfiguration.containsKey(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)) {
                this.currentLogEffectiveTime = Integer.parseInt(capaLogConfiguration.get(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY));
            }

            Calendar instance = Calendar.getInstance();
            instance.add(Calendar.MINUTE, this.currentLogEffectiveTime);
            outputLogValidTime = instance.getTimeInMillis();
        }
    }
}
