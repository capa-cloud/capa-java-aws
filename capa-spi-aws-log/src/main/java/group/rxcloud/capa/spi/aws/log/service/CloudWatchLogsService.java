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
import group.rxcloud.capa.addons.foundation.trip.Foundation;
import group.rxcloud.capa.infrastructure.exceptions.CapaException;
import group.rxcloud.capa.infrastructure.hook.Mixer;
import group.rxcloud.capa.infrastructure.hook.TelemetryHooks;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CloudWatchLogsService {

    private static final CloudWatchLogsClient CLOUD_WATCH_LOGS_CLIENT;
    private static final String APP_ID;
    private static final String APP_ENV;
    private static final String LOG_GROUP_NAME;
    private static final String LOG_GROUP_FORMAT = "application/%s/%s";
    /**
     * Log Stream format is appid/ip/count
     */
    private static final String LOG_STREAM_FORMAT = "%s/%s/%s";
    private static final String CLOUD_WATCH_LOGS_ERROR_NAMESPACE = "CloudWatchLogs";
    private static final String CLOUD_WATCH_LOGS_ERROR_METRIC_NAME = "LogsError";
    private static final String CLOUD_WATCH_LOGS_PUT_LOG_EVENT_ERROR_TYPE = "PutLogEventError";
    private static final String CLOUD_WATCH_LOGS_PUT_LOG_EVENTS_ERROR_TYPE = "PutLogEventsError";
    private static final String CLOUD_WATCH_LOGS_RESPONSE_NULL_VALUE = "NULL";
    private static final Integer COUNTER_NUM = 1;

    private static final Optional<TelemetryHooks> TELEMETRY_HOOKS;
    private static final int DEFAULT_MAX_LOG_STREAM_COUNT = 10;
    private static final String UNKNOWN = "UNKNOWN";
    private static Optional<LongCounter> LONG_COUNTER = Optional.empty();

    static {
        APP_ENV = buildApplicationEnv();
        APP_ID = buildAppId();
        LOG_GROUP_NAME = String.format(LOG_GROUP_FORMAT, APP_ENV, APP_ID);
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
    public static void putLogEvent(String message, String logStreamName) {
        Objects.requireNonNull(logStreamName, "Log stream name is null");
        // Get sequence token
        DescribeLogStreamsRequest logStreamsRequest = DescribeLogStreamsRequest.builder()
                .logGroupName(LOG_GROUP_NAME)
                .logStreamNamePrefix(logStreamName)
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
                .logGroupName(LOG_GROUP_NAME)
                .logStreamName(logStreamName)
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
                    longCounter.bind(Attributes.of(AttributeKey.stringKey(CLOUD_WATCH_LOGS_PUT_LOG_EVENT_ERROR_TYPE), CLOUD_WATCH_LOGS_PUT_LOG_EVENT_ERROR_TYPE))
                            .add(COUNTER_NUM);
                });
            } finally {
            }
        }
    }

    public static void putLogEvents(List<String> messages, String logStreamName) {
        // Get sequence token
        Objects.requireNonNull(logStreamName, "Log stream name is null");
        DescribeLogStreamsRequest logStreamsRequest = DescribeLogStreamsRequest.builder()
                .logGroupName(LOG_GROUP_NAME)
                .logStreamNamePrefix(logStreamName)
                .build();
        DescribeLogStreamsResponse describeLogStreamsResponse = CLOUD_WATCH_LOGS_CLIENT.describeLogStreams(logStreamsRequest);
        String sequenceToken = "";
        if (describeLogStreamsResponse != null
                && !CollectionUtils.isNullOrEmpty(describeLogStreamsResponse.logStreams())) {
            sequenceToken = describeLogStreamsResponse.logStreams()
                    .get(0)
                    .uploadSequenceToken();
        }
        List<InputLogEvent> inputLogEvents = new ArrayList<>();
        for (String message : messages) {
            InputLogEvent inputLogEvent = InputLogEvent.builder()
                    .timestamp(System.currentTimeMillis())
                    .message(message)
                    .build();
            inputLogEvents.add(inputLogEvent);
        }

        PutLogEventsRequest putLogEventsRequest = PutLogEventsRequest.builder()
                .logGroupName(LOG_GROUP_NAME)
                .logStreamName(logStreamName)
                .logEvents(inputLogEvents)
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
                    longCounter.bind(Attributes.of(AttributeKey.stringKey(CLOUD_WATCH_LOGS_PUT_LOG_EVENTS_ERROR_TYPE), CLOUD_WATCH_LOGS_PUT_LOG_EVENTS_ERROR_TYPE))
                            .add(COUNTER_NUM);
                });
            } finally {
            }
        }
    }

    public static List<String> getLogStreamNames() {
        String ip = Foundation.net().getHostAddress() == null
                ? UNKNOWN
                : Foundation.net().getHostAddress();
        List<String> logStreamNames = new ArrayList<>();
        for (int i = 0; i < DEFAULT_MAX_LOG_STREAM_COUNT; i++) {
            logStreamNames.add(String.format(LOG_STREAM_FORMAT, APP_ID, ip, i));
        }
        return logStreamNames;
    }

    private static String buildAppId() {
        return CapaFoundation.getAppId(FoundationType.TRIP);
    }

    private static String buildApplicationEnv() {
        return CapaFoundation.getEnv(FoundationType.TRIP) == "" ? "DEFAULT" : CapaFoundation.getEnv(FoundationType.TRIP);
    }

    private static void createLogGroup() {
        try {
            //Describe log group to confirm whether the log group has been created..
            DescribeLogGroupsRequest describeLogGroupsRequest = DescribeLogGroupsRequest.builder()
                    .logGroupNamePrefix(LOG_GROUP_NAME)
                    .build();
            DescribeLogGroupsResponse describeLogGroupsResponse = CLOUD_WATCH_LOGS_CLIENT.describeLogGroups(describeLogGroupsRequest);
            // If the log group is not created, then create the log group.
            List<LogGroup> logGroups = describeLogGroupsResponse.logGroups();
            Boolean hasLogGroup = Boolean.FALSE;
            if (!CollectionUtils.isNullOrEmpty(logGroups)) {
                Optional<LogGroup> logGroupOptional = logGroups.stream()
                        .filter(logGroup -> LOG_GROUP_NAME.equalsIgnoreCase(logGroup.logGroupName()))
                        .findAny();
                if (logGroupOptional.isPresent()) {
                    hasLogGroup = Boolean.TRUE;
                }
            }
            if (!describeLogGroupsResponse.hasLogGroups() || !hasLogGroup) {
                CreateLogGroupRequest createLogGroupRequest = CreateLogGroupRequest.builder()
                        .logGroupName(LOG_GROUP_NAME)
                        .build();
                CLOUD_WATCH_LOGS_CLIENT.createLogGroup(createLogGroupRequest);
            }
        } catch (Throwable e) {
            // TODO change to ErrorCodeContext. Eg: throw new CapaException(CapaErrorContext.CREATE_LOG_GROUP_ERROR);
            throw new CapaException(e);
        }
    }

    private static void createLogStream() {
        try {
            List<String> logStreamNames = getLogStreamNames();
            for (String logStreamName : logStreamNames) {
                //Describe log stream to confirm whether the log stream has been created.
                DescribeLogStreamsRequest describeLogStreamsRequest = DescribeLogStreamsRequest.builder()
                        .logGroupName(LOG_GROUP_NAME)
                        .logStreamNamePrefix(logStreamName)
                        .build();
                DescribeLogStreamsResponse describeLogStreamsResponse = CLOUD_WATCH_LOGS_CLIENT.describeLogStreams(describeLogStreamsRequest);
                // If the log stream is not created, then create the log stream.
                List<LogStream> logStreams = describeLogStreamsResponse.logStreams();
                Boolean hasLogStream = Boolean.FALSE;
                if (!CollectionUtils.isNullOrEmpty(logStreams)) {
                    Optional<LogStream> logStreamOptional = logStreams.stream()
                            .filter(logStream -> logStreamName.equalsIgnoreCase(logStream.logStreamName()))
                            .findAny();
                    if (logStreamOptional.isPresent()) {
                        hasLogStream = Boolean.TRUE;
                    }
                }
                if (!describeLogStreamsResponse.hasLogStreams() || !hasLogStream) {
                    CreateLogStreamRequest createLogStreamRequest = CreateLogStreamRequest.builder()
                            .logGroupName(LOG_GROUP_NAME)
                            .logStreamName(logStreamName)
                            .build();
                    CLOUD_WATCH_LOGS_CLIENT.createLogStream(createLogStreamRequest);
                }
            }
        } catch (Throwable e) {
            // TODO change to ErrorCodeContext. Eg: throw new CapaException(CapaErrorContext.CREATE_LOG_STREAM_ERROR);
            throw new CapaException(e);
        }
    }
}
