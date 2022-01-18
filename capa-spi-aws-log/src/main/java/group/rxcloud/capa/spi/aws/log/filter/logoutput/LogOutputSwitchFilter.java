package group.rxcloud.capa.spi.aws.log.filter.logoutput;

import group.rxcloud.capa.spi.aws.log.configuration.CapaComponentLogConfiguration;
import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.LogOutputFilter;

import java.util.Optional;

public class LogOutputSwitchFilter implements LogOutputFilter {
    /**
     * Dynamically adjust the log level switch name.
     */
    private static final String LOG_SWITCH_NAME = "logSwitch";

    private final Optional<CapaComponentLogConfiguration> capaComponentLogConfiguration = Optional.ofNullable(CapaComponentLogConfiguration.getInstance());

    @Override
    public boolean logCanOutput(CapaLogLevel level) {
        // Determine whether the log output switch is turned on, if it is turned off, the log will not be output.
        return !capaComponentLogConfiguration.isPresent()
                || !capaComponentLogConfiguration.get().containsKey(LOG_SWITCH_NAME)
                || !String.valueOf(Boolean.FALSE).equalsIgnoreCase(capaComponentLogConfiguration.get().get(LOG_SWITCH_NAME));
    }
}
