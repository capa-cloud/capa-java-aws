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
package group.rxcloud.capa.spi.aws.telemetry.log.service;

import group.rxcloud.capa.addons.foundation.CapaFoundation;
import group.rxcloud.capa.addons.foundation.FoundationType;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.infrastructure.hook.TelemetryHooks;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamResponse;

import java.util.Optional;

public class CloudWatchLogsService {
    private static final CloudWatchLogsClient CLOUD_WATCH_LOGS_CLIENT;
    private static final String APP_ID;
    private static final String APPLICATION_ENV;
    private static final String APPLICATION_ENV_FORMAT = "application/%s";
    private static final String CLOUD_WATCH_LOGS_ERROR_NAMESPACE = "CloudWatchLogs";
    private static final String CLOUD_WATCH_LOGS_ERROR_METRIC_NAME = "LogsError";
    private static final String CLOUD_WATCH_LOGS_CREATE_LOG_GROUP_ERROR_TYPE = "CreateLogGroupError";
    private static final String CLOUD_WATCH_LOGS_CREATE_LOG_STREAM_ERROR_TYPE = "CreateLogStreamError";
    private static final String CLOUD_WATCH_LOGS_PUT_LOG_EVENT_ERROR_TYPE = "PutLogEventError";
    private static final String CLOUD_WATCH_LOGS_RESPONSE_NULL_VALUE = "NULL";
    private static final Integer COUNTER_NUM = 1;

    private static final Optional<TelemetryHooks> TELEMETRY_HOOKS;
    private static Optional<LongCounter> LONG_COUNTER = Optional.empty();

    static {
        APP_ID = buildAppId();
        APPLICATION_ENV = buildApplicationEnv();
        CLOUD_WATCH_LOGS_CLIENT = CloudWatchLogsClient.builder()
                .build();
        createLogGroup();
        createLogStream();
        TELEMETRY_HOOKS = Mixer.telemetryHooksNullable();
        TELEMETRY_HOOKS.ifPresent(telemetryHooks -> {
            Meter meter = telemetryHooks.buildMeter(CLOUD_WATCH_LOGS_ERROR_NAMESPACE).block();
            LongCounter longCounter = meter.counterBuilder(CLOUD_WATCH_LOGS_ERROR_METRIC_NAME).build();
            LONG_COUNTER = Optional.ofNullable(longCounter);
        });
    }

    //Synchronously put log event
    public static void putLogEvent(String message) {
        // Get sequence token
        DescribeLogStreamsRequest logStreamsRequest = DescribeLogStreamsRequest.builder()
                .logGroupName(APPLICATION_ENV)
                .logStreamNamePrefix(APP_ID)
                .build();
        DescribeLogStreamsResponse describeLogStreamsResponse = CLOUD_WATCH_LOGS_CLIENT.describeLogStreams(logStreamsRequest);
        String sequenceToken = StringUtils.EMPTY;
        if (describeLogStreamsResponse != null
                && CollectionUtils.isNotEmpty(describeLogStreamsResponse.logStreams())) {
            sequenceToken = describeLogStreamsResponse.logStreams()
                    .get(0)
                    .uploadSequenceToken();
        }

        InputLogEvent inputLogEvent = InputLogEvent.builder()
                .timestamp(System.currentTimeMillis())
                .message(message)
                .build();

        PutLogEventsRequest putLogEventsRequest = PutLogEventsRequest.builder()
                .logGroupName(APPLICATION_ENV)
                .logStreamName(APP_ID)
                .logEvents(inputLogEvent)
                .sequenceToken(sequenceToken)
                .build();
        PutLogEventsResponse putLogEventsResponse = CLOUD_WATCH_LOGS_CLIENT.putLogEvents(putLogEventsRequest);
        // If the response is abnormal, try counting.
        if (putLogEventsResponse == null
                || putLogEventsResponse.sdkHttpResponse() == null
                || !putLogEventsResponse.sdkHttpResponse().isSuccessful()) {
            String statusCode = putLogEventsResponse == null || putLogEventsResponse.sdkHttpResponse() == null
                    ? CLOUD_WATCH_LOGS_RESPONSE_NULL_VALUE
                    : String.valueOf(putLogEventsResponse.sdkHttpResponse().statusCode());
            LONG_COUNTER.ifPresent(longCounter -> {
                longCounter.bind(Attributes.of(AttributeKey.stringKey(CLOUD_WATCH_LOGS_PUT_LOG_EVENT_ERROR_TYPE), statusCode))
                        .add(COUNTER_NUM);
            });
        }
    }

    private static String buildAppId() {
        return CapaFoundation.getAppId(FoundationType.TRIP);
    }

    private static String buildApplicationEnv() {
        return String.format(APPLICATION_ENV_FORMAT, CapaFoundation.getEnv(FoundationType.TRIP));
    }

    private static void createLogGroup() {
        CreateLogGroupRequest createLogGroupRequest = CreateLogGroupRequest.builder()
                .logGroupName(APPLICATION_ENV)
                .build();
        CreateLogGroupResponse createLogGroupResponse = CLOUD_WATCH_LOGS_CLIENT.createLogGroup(createLogGroupRequest);
        // If the response is abnormal, try counting.
        if (createLogGroupResponse == null
                || createLogGroupResponse.sdkHttpResponse() == null
                || !createLogGroupResponse.sdkHttpResponse().isSuccessful()) {
            String statusCode = createLogGroupResponse == null || createLogGroupResponse.sdkHttpResponse() == null
                    ? CLOUD_WATCH_LOGS_RESPONSE_NULL_VALUE
                    : String.valueOf(createLogGroupResponse.sdkHttpResponse().statusCode());
            LONG_COUNTER.ifPresent(longCounter -> {
                longCounter.bind(Attributes.of(AttributeKey.stringKey(CLOUD_WATCH_LOGS_CREATE_LOG_GROUP_ERROR_TYPE), statusCode))
                        .add(COUNTER_NUM);
            });
        }
    }

    private static void createLogStream() {
        CreateLogStreamRequest createLogStreamRequest = CreateLogStreamRequest.builder()
                .logGroupName(APPLICATION_ENV)
                .logStreamName(APP_ID)
                .build();
        CreateLogStreamResponse createLogStreamResponse = CLOUD_WATCH_LOGS_CLIENT.createLogStream(createLogStreamRequest);
        // If the response is abnormal, try counting.
        if (createLogStreamResponse == null
                || createLogStreamResponse.sdkHttpResponse() == null
                || !createLogStreamResponse.sdkHttpResponse().isSuccessful()) {
            String statusCode = createLogStreamResponse == null || createLogStreamResponse.sdkHttpResponse() == null
                    ? CLOUD_WATCH_LOGS_RESPONSE_NULL_VALUE
                    : String.valueOf(createLogStreamResponse.sdkHttpResponse().statusCode());
            LONG_COUNTER.ifPresent(longCounter -> {
                longCounter.bind(Attributes.of(AttributeKey.stringKey(CLOUD_WATCH_LOGS_CREATE_LOG_STREAM_ERROR_TYPE), statusCode))
                        .add(COUNTER_NUM);
            });
        }
    }
}
