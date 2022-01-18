package group.rxcloud.capa.spi.aws.log.filter;

import group.rxcloud.capa.spi.aws.log.enums.CapaLogLevel;

public interface LogOutputFilter {

    boolean logCanOutput(CapaLogLevel level);
}
