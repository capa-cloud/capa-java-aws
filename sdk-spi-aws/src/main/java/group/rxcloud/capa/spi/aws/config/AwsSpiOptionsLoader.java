package group.rxcloud.capa.spi.aws.config;

import group.rxcloud.capa.infrastructure.env.CapaEnvironment;
import group.rxcloud.capa.spi.config.CapaSpiOptionsLoader;

import java.util.Objects;

public class AwsSpiOptionsLoader implements CapaSpiOptionsLoader<AwsRpcServiceOptions> {

    @Override
    public AwsRpcServiceOptions loadRpcServiceOptions(String appId) {
        Objects.requireNonNull(appId, "appId");
        AwsRpcServiceOptions rpcServiceOptions = new AwsRpcServiceOptions(appId, AwsRpcServiceOptions.ServiceRpcInvokeMode.AWS_TO_AWS);
        CapaEnvironment.DeployVpcEnvironment deployVpcEnvironment = CapaEnvironment.getDeployVpcEnvironment();
        AwsRpcServiceOptions.AwsToAwsServiceOptions awsToAwsServiceOptions =
                new AwsRpcServiceOptions.AwsToAwsServiceOptions("", "", deployVpcEnvironment);
        rpcServiceOptions.setAwsToAwsServiceOptions(awsToAwsServiceOptions);
        return rpcServiceOptions;
    }
}
