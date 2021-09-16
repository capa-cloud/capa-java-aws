package group.rxcloud.capa.spi.aws.http;

import group.rxcloud.capa.component.http.HttpResponse;
import group.rxcloud.capa.infrastructure.serializer.CapaObjectSerializer;
import group.rxcloud.capa.spi.config.RpcServiceOptions;
import group.rxcloud.capa.spi.http.CapaSerializeHttpSpi;
import group.rxcloud.cloudruntimes.utils.TypeRef;
import okhttp3.OkHttpClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AwsCapaHttp extends CapaSerializeHttpSpi {

    /**
     * Instantiates a new Capa serialize http spi.
     *
     * @param httpClient       the http client
     * @param objectSerializer the object serializer
     */
    public AwsCapaHttp(OkHttpClient httpClient, CapaObjectSerializer objectSerializer) {
        super(httpClient, objectSerializer);
    }

    @Override
    protected <T> CompletableFuture<HttpResponse<T>> invokeSpiApi(String appId, String method, Object requestData, Map<String, String> headers, TypeRef<T> type, RpcServiceOptions rpcServiceOptions) {
        return null;
    }
}
