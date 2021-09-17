package group.rxcloud.capa.spi.aws.config;

import group.rxcloud.cloudruntimes.utils.TypeRef;
import software.amazon.awssdk.core.SdkClient;

import java.util.Map;

/**
 * TODO load class from spi
 */
public abstract class AwsClientProviderLoader {

    static Map<String, AwsClientProvider> clientProviders;

    public static <T extends SdkClient> T load(TypeRef<T> typeRef) {
        AwsClientProvider provider = clientProviders.get(typeRef.getClass().getSimpleName());
        SdkClient sdkClient = provider.provideAwsClient();
        return (T) sdkClient;
    }

    interface AwsClientProvider<T extends SdkClient> {

        T provideAwsClient();
    }
}
