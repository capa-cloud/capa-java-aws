package group.rxcloud.capa.spi.aws.infrastructure;

import group.rxcloud.capa.infrastructure.CapaEnvironment;
import group.rxcloud.capa.infrastructure.CapaProperties;

import java.util.Properties;

/**
 * The Aws capa environment.
 */
public class AwsCapaEnvironment implements CapaEnvironment {

    @Override
    public String getDeployCloud() {
        return "AWS";
    }

    @Override
    public String getDeployRegion() {
        String regionKey = Settings.getRegionKey();
        return System.getProperty(regionKey);
    }

    @Override
    public String getDeployEnv() {
        String envKey = Settings.getEnvKey();
        return System.getProperty(envKey);
    }

    abstract static class Settings {

        private static String regionKey = "default";
        private static String envKey = "default";

        private static final String INFRASTRUCTURE_CLOUD_REGION_KEY = "INFRASTRUCTURE_CLOUD_REGION_KEY";
        private static final String INFRASTRUCTURE_CLOUD_ENV_KEY = "INFRASTRUCTURE_CLOUD_ENV_KEY";

        static {
            Properties properties = CapaProperties.INFRASTRUCTURE_PROPERTIES_SUPPLIER.apply("cloud-aws");

            regionKey = properties.getProperty(INFRASTRUCTURE_CLOUD_REGION_KEY, regionKey);

            envKey = properties.getProperty(INFRASTRUCTURE_CLOUD_ENV_KEY, envKey);
        }

        public static String getRegionKey() {
            return regionKey;
        }

        public static String getEnvKey() {
            return envKey;
        }

        private Settings() {
        }
    }
}
