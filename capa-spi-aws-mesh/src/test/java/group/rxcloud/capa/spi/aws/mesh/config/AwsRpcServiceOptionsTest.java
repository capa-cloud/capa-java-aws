/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package group.rxcloud.capa.spi.aws.mesh.config;

import group.rxcloud.capa.infrastructure.env.CapaEnvironment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AwsRpcServiceOptionsTest {

    private AwsRpcServiceOptions awsRpcServiceOptions;

    private AwsRpcServiceOptions.AwsToAwsServiceOptions awsToAwsServiceOptions;

    @BeforeEach
    public void setUp() {
        awsRpcServiceOptions = new AwsRpcServiceOptions("appId",
                AwsRpcServiceOptions.ServiceRpcInvokeMode.AWS_TO_AWS);

        awsToAwsServiceOptions = new AwsRpcServiceOptions.AwsToAwsServiceOptions(
                "appId",
                8080,
                "FWS",
                CapaEnvironment.DeployVpcEnvironment.FWS);
        awsRpcServiceOptions.setAwsToAwsServiceOptions(awsToAwsServiceOptions);
    }

    @Test
    public void testGetAppId_Success() {
        String appId = awsRpcServiceOptions.getAppId();
        Assertions.assertEquals("appId", appId);
    }

    @Test
    public void testGetRpcInvokeMode_Success() {
        AwsRpcServiceOptions.ServiceRpcInvokeMode rpcInvokeMode = awsRpcServiceOptions.getRpcInvokeMode();
        Assertions.assertEquals(AwsRpcServiceOptions.ServiceRpcInvokeMode.AWS_TO_AWS, rpcInvokeMode);
    }

    @Test
    public void testGetAwsToAwsServiceOptions_Success() {
        AwsRpcServiceOptions.AwsToAwsServiceOptions awsToAwsServiceOptions
                = awsRpcServiceOptions.getAwsToAwsServiceOptions();

        Assertions.assertEquals("appId", awsToAwsServiceOptions.getServiceId());
        Assertions.assertEquals(8080, awsToAwsServiceOptions.getServicePort());
        Assertions.assertEquals("FWS", awsToAwsServiceOptions.getNamespace());
        Assertions.assertEquals(CapaEnvironment.DeployVpcEnvironment.FWS, awsToAwsServiceOptions.getServiceEnv());
    }

    @Test
    public void testEquals_SuccessWhenTrue() {
        boolean result = awsRpcServiceOptions.equals(awsRpcServiceOptions);
        Assertions.assertTrue(result);

        AwsRpcServiceOptions newServiceOptions = new AwsRpcServiceOptions("appId",
                AwsRpcServiceOptions.ServiceRpcInvokeMode.AWS_TO_AWS);
        newServiceOptions.setAwsToAwsServiceOptions(awsToAwsServiceOptions);

        boolean newResult = awsRpcServiceOptions.equals(newServiceOptions);
        Assertions.assertTrue(newResult);
    }

    @Test
    public void testEquals_SuccessWhenFalse() {
        boolean result = awsRpcServiceOptions.equals(new AwsSpiOptionsLoader());
        Assertions.assertFalse(result);

        AwsRpcServiceOptions newServiceOptions = new AwsRpcServiceOptions("appId",
                AwsRpcServiceOptions.ServiceRpcInvokeMode.AWS_TO_AWS);
        boolean newResult = awsRpcServiceOptions.equals(newServiceOptions);
        Assertions.assertFalse(newResult);
    }

    @Test
    public void testHashCode_Success() {
        int hashCode = awsRpcServiceOptions.hashCode();
        Assertions.assertNotNull(hashCode);
    }

    @Test
    public void testToString_Success() {
        String toString = awsRpcServiceOptions.toString();
        Assertions.assertNotNull(toString);
    }
}
