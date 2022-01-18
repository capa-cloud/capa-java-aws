package group.rxcloud.capa.spi.aws.log.filter.factory;

import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;
import group.rxcloud.capa.spi.aws.log.filter.LogOutputFilter;
import group.rxcloud.capa.spi.aws.log.filter.logoutput.LogOutputLevelFilter;
import group.rxcloud.capa.spi.aws.log.filter.logoutput.LogOutputSwitchFilter;
import group.rxcloud.capa.spi.aws.log.filter.logoutput.LogOutputTimeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogOutputFactoryFilter {
    private static final AtomicBoolean FILTER_INIT = new AtomicBoolean(false);
    private static final Logger log = LoggerFactory.getLogger(LogOutputFactoryFilter.class);
    private static Optional<List<LogOutputFilter>> logOutputFilterList = Optional.empty();

    /**
     * To judge whether the output can be output, several conditions need to be considered.
     * 1. Whether the global log output switch is on, if it is off, all logs are not output.
     * 2. The log output switch is on, and judge capa-component-log-configuration.properties (configuration file ConfigFileA)
     * The relationship between the file configuration output log level and the configuration log output level of capa-log-configuration.properties (ConfigFileB)
     * 2.1 The log output level of ConfigFileA is higher than or equal to that of ConfigFileB, and the log is output directly.
     * 2.2 The log output level of ConfigFileB is lower than the log output level of ConfigFileB,
     * and the Japanese-style output expiration time processing is performed.
     * 2.2.1 Calculate the log level expiration time according to the configuration log output time of ConfigFileB (there is no configuration to set a default expiration time, unit: minutes )
     * 2.2.2 If the current time is less than or equal to the expiration time, it can be output.
     * 2.2.3 If the current time is greater than the expiration time, it cannot be output.
     * 2.2.4 When the expiration time and log output level of ConfigFileB change, reset the log output level and expiration time.
     * 2.2.5 When the log output level of ConfigFileA changes, reset the log output expiration time.
     * 3 The expiration time field is local.
     * The output level is reset when the output level of the configuration file changes compared to the configuration file.
     * The default log output level is error. Since the info log needs to be enabled, the log output level is set to info.
     * 1.1. When the code obtains a change in the configured log level, the expiration time will be reset, and the error and the following ones less than the expiration time will be reset.
     * And the logs of info and above can be output, the logs below info are not output, and the logs below error greater than the expiration time are not output
     * 1.2 When the user configures an output level of info,
     * and then after a period of time, he wants to output the info level log ,
     * then you need to re-modify the configuration file,
     * first set the error level to error or warn,
     * and then change it back to info to take effect
     *
     * @param outputLogLevel
     * @return
     */
    public static boolean logCanOutput(CapaLogLevel outputLogLevel) {
        if (getLogOutputFilterList().isPresent()) {
            for (LogOutputFilter logOutputFilter : logOutputFilterList.get()) {
                if (!logOutputFilter.logCanOutput(outputLogLevel)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Optional<List<LogOutputFilter>> getLogOutputFilterList() {
        if (FILTER_INIT.get()) {
            return logOutputFilterList;
        }
        synchronized (FILTER_INIT) {
            if (FILTER_INIT.compareAndSet(false, true)) {
                try {
                    List<LogOutputFilter> logOutputFilters = new ArrayList<>();
                    logOutputFilters.add(new LogOutputSwitchFilter());
                    logOutputFilters.add(new LogOutputLevelFilter());
                    logOutputFilters.add(new LogOutputTimeFilter());
                    logOutputFilterList = Optional.of(logOutputFilters);
                } catch (Throwable e) {
                    log.error("Create logOutputFilter error.", e);
                }
            }
        }
        return logOutputFilterList;
    }
}
