package group.rxcloud.capa.spi.aws.log.configuration;


import com.google.common.collect.Lists;
import group.rxcloud.capa.component.CapaLogProperties;
import group.rxcloud.capa.infrastructure.hook.ConfigurationHooks;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.cloudruntimes.domain.core.configuration.SubConfigurationResp;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

public class CapaComponentLogConfiguration {
    private static final String CAPA_COMPONENT_LOG_CONFIGURATION_FILE_NAME = "capa-component-log-configuration.properties";
    private static CapaComponentLogConfiguration instance;
    private final Logger log = LoggerFactory.getLogger(CapaComponentLogConfiguration.class);
    private static final Object lock = new Object();
    private final AtomicReferenceArray<Map> capaComponentLogConfigurationProperties;
    private final List<Consumer<SubConfigurationResp<Map>>> customConfigCallbackList;
    private volatile Map<String, String> capaComponentLogConfiguration;

    private CapaComponentLogConfiguration() {
        capaComponentLogConfigurationProperties = new AtomicReferenceArray<>(2);
        capaComponentLogConfiguration = new HashMap<>();
        customConfigCallbackList = new ArrayList<>();
        Mixer.configurationHooksNullable().ifPresent(hooks -> {
            // subscribe capa-compoment-log-configuration.properties
            List<String> appIds = Lists.newArrayList(hooks.defaultConfigurationAppId(), CapaLogProperties.Settings.getCenterConfigAppId());
            for (int i = 0; i < appIds.size(); i++) {
                try {
                    subscribeCapaComponentLogConfigurationByAppId(hooks, appIds.get(i), i);
                } catch (Throwable throwable) {
                    log.warn("Fail to subscribe config for app id " + appIds.get(i) + ", index " + i, throwable);
                }
            }
        });
    }

    public static CapaComponentLogConfiguration getInstance() {
        if (instance != null) {
            return instance;
        }
        synchronized (lock) {
            if (instance == null) {
                instance = new CapaComponentLogConfiguration();
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
            return capaComponentLogConfiguration != null
                    && capaComponentLogConfiguration.containsKey(key);
        } catch (Exception e) {
            return false;
        }
    }

    public String get(String key) {
        try {
            return capaComponentLogConfiguration == null
                    ? ""
                    : capaComponentLogConfiguration.get(key);
        } catch (Exception e) {
            return "";
        }
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
            for (Consumer<SubConfigurationResp<Map>> subConfigurationRespConsumer : customConfigCallbackList) {
                subConfigurationRespConsumer.accept(resp);
            }
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
            }
        });
    }
}
