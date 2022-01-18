package group.rxcloud.capa.spi.aws.log.filter.logoutput;

import group.rxcloud.capa.spi.aws.log.configuration.CapaComponentLogConfiguration;
import group.rxcloud.capa.spi.aws.log.configuration.CapaLogConfiguration;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.LogOutputFilter;

import java.util.Optional;

public class LogOutputLevelFilter implements LogOutputFilter {
    private static final String OUTPUT_LOG_LEVEL_NAME = "outputLogLevel";
    private final Optional<CapaComponentLogConfiguration> capaComponentLogConfiguration = Optional.ofNullable(CapaComponentLogConfiguration.getInstance());
    private final Optional<CapaLogConfiguration> capaLogConfiguration = Optional.ofNullable(CapaLogConfiguration.getInstance());

    public LogOutputLevelFilter() {
    }

    @Override
    public boolean logCanOutput(CapaLogLevel outputLogLevel) {
        // 1. Check whether the output log level is higher than or equal to the number of log output levels configured by the application.
        // If it is lower than the configuration, return false directly.
        // Whether the log level is higher than or equal to the log output level configured by the application.
        if (capaComponentLogConfiguration.isPresent()
                && capaComponentLogConfiguration.get().containsKey(OUTPUT_LOG_LEVEL_NAME)) {
            Optional<CapaLogLevel> capaLogLevel = CapaLogLevel.toCapaLogLevel(capaComponentLogConfiguration.get().get(OUTPUT_LOG_LEVEL_NAME));
            if (capaLogLevel.isPresent()
                    && outputLogLevel.getLevel() < capaLogLevel.get().getLevel()) {
                return false;
            }
        }
        // 2. Check whether the output log level is higher than or equal to the log output level configured by capa-log, and return true if it is higher than or equal to.
        if (capaLogConfiguration.isPresent()
                && capaLogConfiguration.get().containsKey(OUTPUT_LOG_LEVEL_NAME)) {
            Optional<CapaLogLevel> capaLogLevel = CapaLogLevel.toCapaLogLevel(capaLogConfiguration.get().get(OUTPUT_LOG_LEVEL_NAME));
            if (capaLogLevel.isPresent()) {
                return outputLogLevel.getLevel() >= capaLogLevel.get().getLevel();
            }
        }
        return true;
    }
}
