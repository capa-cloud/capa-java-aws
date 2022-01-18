package group.rxcloud.capa.spi.aws.log.filter.logoutput;

import group.rxcloud.capa.spi.aws.log.configuration.CapaComponentLogConfiguration;
import group.rxcloud.capa.spi.aws.log.configuration.CapaLogConfiguration;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.LogOutputFilter;
import group.rxcloud.cloudruntimes.domain.core.configuration.SubConfigurationResp;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class LogOutputTimeFilter implements LogOutputFilter {
    private static final String OUTPUT_LOG_LEVEL_CONFIG_KEY = "outputLogLevel";
    private static final Integer DEFAULT_OUTPUT_LOG_EFFECTIVE_TIME = 30;
    private static final String OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY = "outputLogEffectiveTime";
    private final Optional<CapaComponentLogConfiguration> capaComponentLogConfiguration = Optional.ofNullable(CapaComponentLogConfiguration.getInstance());
    private final Optional<CapaLogConfiguration> capaLogConfiguration = Optional.ofNullable(CapaLogConfiguration.getInstance());
    private Long currentOutputLogValidTime = System.currentTimeMillis();
    private volatile CapaLogLevel currentLogLevel = CapaLogLevel.ERROR;
    private volatile Integer currentLogEffectiveTime = 30;

    public LogOutputTimeFilter() {
        Consumer<SubConfigurationResp<Map>> capaComponentLogConfigurationConsumer = resp -> {
            Map<String, String> config = new HashMap<>();
            if (!resp.getItems().isEmpty()) {
                config = resp.getItems().get(0).getContent();
            }
            // update current output log level valid time
            if (config.isEmpty()) {
                this.currentLogLevel = CapaLogLevel.ALL;
            } else if (config.containsKey(OUTPUT_LOG_LEVEL_CONFIG_KEY)) {
                Optional.ofNullable(CapaLogLevel.valueOf(config.get(OUTPUT_LOG_LEVEL_CONFIG_KEY)))
                        .ifPresent(outputLogLevel -> {
                            if (!this.currentLogLevel.equals(outputLogLevel)) {
                                this.updateOutputLogValidTime();
                            }
                        });
            }
        };
        Consumer<SubConfigurationResp<Map>> capaLogConfigurationConsumer = resp -> {
            Map<String, String> config = new HashMap<>();
            if (!resp.getItems().isEmpty()
                    && !CollectionUtils.isNullOrEmpty(resp.getItems().get(0).getContent())) {
                config.putAll(resp.getItems().get(0).getContent());
            }
            // update output log level and the valid time
            if (!config.isEmpty()
                    && config.containsKey(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)) {
                Optional.ofNullable(Integer.parseInt(config.get(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)))
                        .ifPresent(outputLogEffectiveTime -> {
                            if (!this.currentLogEffectiveTime.equals(outputLogEffectiveTime)) {
                                this.updateOutputLogValidTime();
                            }
                        });
            }
        };
        CapaComponentLogConfiguration.getInstance().registerConfigCallback(capaComponentLogConfigurationConsumer);
        capaComponentLogConfiguration.ifPresent(capaComponentLogConfiguration -> {
            capaComponentLogConfiguration.registerConfigCallback(capaComponentLogConfigurationConsumer);
        });
        capaLogConfiguration.ifPresent(capaLogConfiguration -> {
            capaLogConfiguration.registerConfigCallback(capaLogConfigurationConsumer);
        });
        this.updateOutputLogValidTime();

    }

    private void updateOutputLogValidTime() {
        this.currentLogEffectiveTime = DEFAULT_OUTPUT_LOG_EFFECTIVE_TIME;
        if (CapaComponentLogConfiguration.getInstance() != null
                && CapaComponentLogConfiguration.getInstance().containsKey(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY)) {
            this.currentLogEffectiveTime = Integer.parseInt(CapaComponentLogConfiguration.getInstance().get(OUTPUT_LOG_EFFECTIVE_TIME_CONFIG_KEY));
        }

        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.MINUTE, this.currentLogEffectiveTime);
        currentOutputLogValidTime = instance.getTimeInMillis();
    }

    @Override
    public boolean logCanOutput(CapaLogLevel level) {
        return System.currentTimeMillis() < currentOutputLogValidTime;
    }
}
