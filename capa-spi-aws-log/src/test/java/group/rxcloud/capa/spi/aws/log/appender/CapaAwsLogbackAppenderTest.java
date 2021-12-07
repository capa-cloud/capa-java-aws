package group.rxcloud.capa.spi.aws.log.appender;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class CapaAwsLogbackAppenderTest {
    @Test
    void appendLog() {
        for (int i = 0; i < 30; i++) {
            log.info("[[requestId=123456]]Test");
        }
        try {
            Thread.sleep(30 * 1000);
        } catch (InterruptedException e) {

        } finally {

        }
    }
}