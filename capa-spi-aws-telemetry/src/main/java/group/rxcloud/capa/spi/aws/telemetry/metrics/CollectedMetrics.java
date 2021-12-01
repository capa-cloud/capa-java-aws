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
package group.rxcloud.capa.spi.aws.telemetry.metrics;

import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.StatisticSet;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class CollectedMetrics {

    String nameSpace;

    String metricName;

    Instant instant;

    List<Dimension> dimensions;

    Map<Double, AtomicInteger> metricPointCount;

    StatisticSet statisticSet;

    CollectedMetrics(String nameSpace, String metricName, long millis, List<Dimension> dimensions) {
        this.nameSpace = nameSpace;
        this.metricName = metricName;
        this.dimensions = dimensions;
        instant = Instant.ofEpochMilli(millis);
        metricPointCount = new ConcurrentHashMap<>();
    }

    public void setStatisticSet(StatisticSet statisticSet) {
        this.statisticSet = statisticSet;
    }

    public void addPoint(double value) {
        metricPointCount.computeIfAbsent(value, k -> new AtomicInteger()).incrementAndGet();
    }
}
