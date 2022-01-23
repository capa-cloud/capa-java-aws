package group.rxcloud.capa.spi.aws.log.configuration;

import java.util.Map;

/**
 * @author: chenyijiang
 * @date: 2022/1/23 18:52
 */
public class EffectiveTimeChecker implements CapaComponentLogConfiguration.ConfigChangedCallback {

    private static volatile long levelChangedStart = -1;

    private static boolean isChanged(Map<String, String> oldConfig, Map<String, String> newConfig) {
        return !oldConfig.getOrDefault(LogConfig.LevelConfig.OUTPUT_LEVEL.configKey, "")
                         .equals(newConfig.getOrDefault(LogConfig.LevelConfig.OUTPUT_LEVEL.configKey, ""));
    }

    public static boolean isOutputLevelEffective() {
        if (levelChangedStart > 0) {
            long end = levelChangedStart + LogConfig.TimeConfig.OUTPUT_LOG_EFFECTIVE_TIME.get();
            return end > System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public void onChange(Map<String, String> oldConfig, Map<String, String> newConfig) {
        boolean isLevelChanged = isChanged(oldConfig, newConfig);
        if (isLevelChanged) {
            levelChangedStart = System.currentTimeMillis();
        }
    }
}
