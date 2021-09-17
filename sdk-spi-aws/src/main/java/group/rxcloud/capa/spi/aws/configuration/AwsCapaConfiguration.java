package group.rxcloud.capa.spi.aws.configuration;

import group.rxcloud.capa.component.configstore.CapaConfigStore;
import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.capa.spi.aws.config.AwsClientProviderLoader;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import software.amazon.awssdk.services.appconfig.AppConfigAsyncClient;

/**
 * TODO load aws client from spi
 */
public class AwsCapaConfiguration extends CapaConfigStore {

    private AppConfigAsyncClient appConfigAsyncClient;

    /**
     * Instantiates a new Capa configuration.
     *
     * @param objectSerializer Serializer for transient request/response objects.
     */
    public AwsCapaConfiguration(CapaObjectSerializer objectSerializer) {
        super(objectSerializer);

        TypeRef<AppConfigAsyncClient> ref = TypeRef.get(AppConfigAsyncClient.class);
        appConfigAsyncClient = AwsClientProviderLoader.load(ref);
    }

    @Override
    public void close() throws Exception {

    }
}
