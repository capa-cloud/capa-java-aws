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
package group.rxcloud.capa.spi.aws.log.service;

import group.rxcloud.capa.addons.foundation.CapaFoundation;
import group.rxcloud.capa.addons.foundation.FoundationType;
import group.rxcloud.capa.infrastructure.exceptions.CapaException;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.infrastructure.hook.TelemetryHooks;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.List;
import java.util.Optional;

public class CloudWatchLogsService {

    private static final CloudWatchLogsClient CLOUD_WATCH_LOGS_CLIENT;
    private static final String APP_ID;
    private static final String APPLICATION_ENV;
    private static final String APPLICATION_ENV_FORMAT = "application/%s";
    private static final String CLOUD_WATCH_LOGS_ERROR_NAMESPACE = "CloudWatchLogs";
    private static final String CLOUD_WATCH_LOGS_ERROR_METRIC_NAME = "LogsError";
    private static final String CLOUD_WATCH_LOGS_PUT_LOG_EVENT_ERROR_TYPE = "PutLogEventError";
    private static final String CLOUD_WATCH_LOGS_RESPONSE_NULL_VALUE = "NULL";
    private static final Integer COUNTER_NUM = 1;

    private static final Optional<TelemetryHooks> TELEMETRY_HOOKS;
    private static Optional<LongCounter> LONG_COUNTER = Optional.empty();

    static {
        APP_ID = buildAppId();
        APPLICATION_ENV = buildApplicationEnv();
        CLOUD_WATCH_LOGS_CLIENT = CloudWatchLogsClient.builder().build();
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
        String sequenceToken = "";
        if (describeLogStreamsResponse != null
                && !CollectionUtils.isNullOrEmpty(describeLogStreamsResponse.logStreams())) {
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
            try {
                //Enhance function without affecting function
                String statusCode = putLogEventsResponse == null || putLogEventsResponse.sdkHttpResponse() == null
                        ? CLOUD_WATCH_LOGS_RESPONSE_NULL_VALUE
                        : String.valueOf(putLogEventsResponse.sdkHttpResponse().statusCode());
                LONG_COUNTER.ifPresent(longCounter -> {
                    longCounter.bind(Attributes.of(AttributeKey.stringKey(CLOUD_WATCH_LOGS_PUT_LOG_EVENT_ERROR_TYPE), statusCode))
                            .add(COUNTER_NUM);
                });
            } finally {
            }
        }
    }

    private static String buildAppId() {
        return CapaFoundation.getAppId(FoundationType.TRIP);
    }

    private static String buildApplicationEnv() {
        return String.format(APPLICATION_ENV_FORMAT, CapaFoundation.getEnv(FoundationType.TRIP));
    }

    private static void createLogGroup() {
        try {
            //Describe log group to confirm whether the log group has been created..
            DescribeLogGroupsRequest describeLogGroupsRequest = DescribeLogGroupsRequest.builder()
                    .logGroupNamePrefix(APPLICATION_ENV)
                    .build();
            DescribeLogGroupsResponse describeLogGroupsResponse = CLOUD_WATCH_LOGS_CLIENT.describeLogGroups(describeLogGroupsRequest);
            // If the log group is not created, then create the log group.
            List<LogGroup> logGroups = describeLogGroupsResponse.logGroups();
            Boolean hasLogGroup = Boolean.FALSE;
            if (!CollectionUtils.isNullOrEmpty(logGroups)) {
                Optional<LogGroup> logGroupOptional = logGroups.stream()
                        .filter(logGroup -> APPLICATION_ENV.equalsIgnoreCase(logGroup.logGroupName()))
                        .findAny();
                if (logGroupOptional.isPresent()) {
                    hasLogGroup = Boolean.TRUE;
                }
            }
            if (!describeLogGroupsResponse.hasLogGroups() || !hasLogGroup) {
                CreateLogGroupRequest createLogGroupRequest = CreateLogGroupRequest.builder()
                        .logGroupName(APPLICATION_ENV)
                        .build();
                CLOUD_WATCH_LOGS_CLIENT.createLogGroup(createLogGroupRequest);
            }
        } catch (Throwable e) {
            throw new CapaException(e);
        }
    }

    private static void createLogStream() {
        try {
            //Describe log stream to confirm whether the log stream has been created.
            DescribeLogStreamsRequest describeLogStreamsRequest = DescribeLogStreamsRequest.builder()
                    .logGroupName(APPLICATION_ENV)
                    .logStreamNamePrefix(APP_ID)
                    .build();
            DescribeLogStreamsResponse describeLogStreamsResponse = CLOUD_WATCH_LOGS_CLIENT.describeLogStreams(describeLogStreamsRequest);
            // If the log stream is not created, then create the log stream.
            List<LogStream> logStreams = describeLogStreamsResponse.logStreams();
            Boolean hasLogStream = Boolean.FALSE;
            if (!CollectionUtils.isNullOrEmpty(logStreams)) {
                Optional<LogStream> logStreamOptional = logStreams.stream()
                        .filter(logStream -> APP_ID.equalsIgnoreCase(logStream.logStreamName()))
                        .findAny();
                if (logStreamOptional.isPresent()) {
                    hasLogStream = Boolean.TRUE;
                }
            }
            if (!describeLogStreamsResponse.hasLogStreams() || !hasLogStream) {
                CreateLogStreamRequest createLogStreamRequest = CreateLogStreamRequest.builder()
                        .logGroupName(APPLICATION_ENV)
                        .logStreamName(APP_ID)
                        .build();
                CLOUD_WATCH_LOGS_CLIENT.createLogStream(createLogStreamRequest);
            }
        } catch (Throwable e) {
            throw new CapaException(e);
        }
    }
}
