package group.rxcloud.capa.spi.aws.log.configuration;


import group.rxcloud.capa.component.CapaLogProperties;
import group.rxcloud.capa.infrastructure.hook.ConfigurationHooks;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.cloudruntimes.domain.core.configuration.SubConfigurationResp;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.*;
import java.util.function.Consumer;

public class CapaLogConfiguration {
    private static final String CAPA_LOG_CONFIGURATION_FILE_NAME = "capa-log-configuration.properties";
    private static CapaLogConfiguration instance;
    private final Logger log = LoggerFactory.getLogger(CapaLogConfiguration.class);
    private static final Object lock = new Object();
    private final List<Consumer<SubConfigurationResp<Map>>> customConfigCallbackList;
    private volatile Map<String, String> capaLogConfiguration;

    private CapaLogConfiguration() {
        capaLogConfiguration = new HashMap<>();
        customConfigCallbackList = new ArrayList<>();
        Mixer.configurationHooksNullable().ifPresent(hooks -> {
            // subscribe capa-log-configuration.properties
            try {
                subscribeCapaLogConfigurationByAppId(hooks, CapaLogProperties.Settings.getCenterConfigAppId());
            } catch (Throwable throwable) {
                log.warn("Fail to subscribe config for app id " + CapaLogProperties.Settings.getCenterConfigAppId(), throwable);
            }
        });
    }

    public static CapaLogConfiguration getInstance() {
        if (instance != null) {
            return instance;
        }
        synchronized (lock) {
            if (instance == null) {
                instance = new CapaLogConfiguration();
            }
        }
        return instance;
    }

    public void registerConfigCallback(Consumer<SubConfigurationResp<Map>> consumer) {
        synchronized (customConfigCallbackList) {
            customConfigCallbackList.add(consumer);
        }
    }

    public boolean containsKey(String key) {
        try {
            return capaLogConfiguration != null
                    && capaLogConfiguration.containsKey(key);
        } catch (Exception e) {
            return false;
        }
    }

    public String get(String key) {
        try {
            return capaLogConfiguration == null
                    ? ""
                    : capaLogConfiguration.get(key);
        } catch (Exception e) {
            return "";
        }
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
                for (Consumer<SubConfigurationResp<Map>> subConfigurationRespConsumer : customConfigCallbackList) {
                    subConfigurationRespConsumer.accept(resp);
                }
                Map<String, String> config = new HashMap<>();
                if (!resp.getItems().isEmpty()
                        && !CollectionUtils.isNullOrEmpty(resp.getItems().get(0).getContent())) {
                    config.putAll(resp.getItems().get(0).getContent());
                }
                this.capaLogConfiguration = config;
            }
        });
    }
}
