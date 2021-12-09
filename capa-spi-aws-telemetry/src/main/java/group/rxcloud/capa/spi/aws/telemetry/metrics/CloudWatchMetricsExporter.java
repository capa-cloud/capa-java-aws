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

import group.rxcloud.capa.addons.foundation.trip.Foundation;
import group.rxcloud.capa.component.telemetry.SamplerConfig;
import group.rxcloud.capa.spi.telemetry.CapaMetricsExporterSpi;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.cloudwatch.model.StatisticSet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Metrics Exporter which export collected data to AWS Cloud Watch.
 */
public class CloudWatchMetricsExporter extends CapaMetricsExporterSpi {

    private static final Logger log = LoggerFactory.getLogger(CloudWatchMetricsExporter.class);

    private static final int MAX_METRIC_DATUM = 20;

    private static final int MAX_METRIC_VALUE = 150;

    private static final int MAX_VALUE_LENGTH = 256;

    private static final MetricsCache METRICS_CACHE = new MetricsCache();

    private static final String APPID = "AppId";

    private static final String UNKNOWN = "UNKNOWN";

    public CloudWatchMetricsExporter(Supplier<SamplerConfig> samplerConfig) {
        super(samplerConfig);
    }

    private static String getNamespace(MetricData data) {
        return data.getInstrumentationLibraryInfo().getName();
    }

    private static String getMetricName(MetricData data) {
        return data.getName();
    }

    private static String getAppId() {
        try {
            String appId = Foundation.app().getAppId();
            return appId == null ? UNKNOWN : appId;
        } catch (Throwable e) {
            return UNKNOWN;
        }
    }

    static List<Dimension> buildDimension(Attributes attributes) {
        List<Dimension> dimensions = new ArrayList<>();
        dimensions.add(Dimension.builder()
                                .name(APPID)
                                .value(getAppId())
                                .build());
        if (attributes.isEmpty()) {
            return dimensions;
        }
        attributes.forEach((key, value) -> {
            String valueStr = String.valueOf(value);
            if (valueStr.length() > MAX_VALUE_LENGTH) {
                valueStr = valueStr.substring(0, MAX_VALUE_LENGTH);
            }
            dimensions.add(Dimension.builder()
                                    .name(key.getKey())
                                    .value(valueStr)
                                    .build());
        });
        dimensions.sort(new Comparator<Dimension>() {
            @Override
            public int compare(Dimension o1, Dimension o2) {
                return o1.name().compareTo(o2.name());
            }
        });
        return dimensions;
    }

    static Map<String, List<CollectedMetrics>> collectedMetricsByNamespace(Collection<MetricData> metricData) {
        Map<String, CollectedMetrics> metricsMap = new HashMap<>();
        metricData.forEach(m -> {
            String namespace = getNamespace(m);
            String metricName = getMetricName(m);
            MetricDataType type = m.getType();
            if (type == MetricDataType.LONG_SUM) {
                processLongPoint(namespace, metricName, metricsMap, m.getLongSumData().getPoints());
            } else if (type == MetricDataType.LONG_GAUGE) {
                processLongPoint(namespace, metricName, metricsMap, m.getLongGaugeData().getPoints());
            } else if (type == MetricDataType.DOUBLE_SUM) {
                processDoublePoint(namespace, metricName, metricsMap, m.getDoubleSumData().getPoints());
            } else if (type == MetricDataType.DOUBLE_GAUGE) {
                processDoublePoint(namespace, metricName, metricsMap, m.getDoubleGaugeData().getPoints());
            } else if (type == MetricDataType.SUMMARY) {
                processDoubleSummary(namespace, metricName, metricsMap, m.getDoubleSummaryData().getPoints());
            }
        });

        Map<String, List<CollectedMetrics>> metricsMapGroupByNamespace = new HashMap<>();
        metricsMap.values()
                  .forEach(m -> metricsMapGroupByNamespace.computeIfAbsent(m.nameSpace, k -> new ArrayList<>()).add(m));
        return metricsMapGroupByNamespace;
    }

    static void recordHistogram(String namespace, String metricName, Attributes attributes, double data) {
        METRICS_CACHE.recordHistogram(namespace, metricName, attributes, data);
    }

    static void recordHistogram(String namespace, String metricName, Attributes attributes, long data) {
        METRICS_CACHE.recordHistogram(namespace, metricName, attributes, data);
    }

    private static void processLongPoint(String namespace, String metricName, Map<String, CollectedMetrics> metricsMap,
                                         Collection<LongPointData> data) {
        data.forEach(p -> {
            long millis = TimeUnit.NANOSECONDS.toMillis(p.getEpochNanos());
            metricsMap.computeIfAbsent(
                    getKey(namespace, metricName, millis, p.getAttributes()),
                    k -> new CollectedMetrics(namespace, metricName, millis, buildDimension(p.getAttributes())))
                      .addPoint(BigDecimal.valueOf(p.getValue()).doubleValue());
        });
    }

    private static void processDoublePoint(String namespace, String metricName,
                                           Map<String, CollectedMetrics> metricsMap, Collection<DoublePointData> data) {
        data.forEach(p -> {
            long millis = TimeUnit.NANOSECONDS.toMillis(p.getEpochNanos());
            metricsMap.computeIfAbsent(getKey(namespace, metricName, millis, p.getAttributes()),
                    k -> new CollectedMetrics(namespace, metricName, millis, buildDimension(p.getAttributes())))
                      .addPoint(p.getValue());
        });
    }

    private static void processDoubleSummary(String namespace, String metricName,
                                             Map<String, CollectedMetrics> metricsMap,
                                             Collection<DoubleSummaryPointData> data) {
        data.forEach(d -> {
            long millis = TimeUnit.NANOSECONDS.toMillis(d.getEpochNanos());
            StatisticSet.Builder setBuilder = StatisticSet.builder()
                                                          .sum(d.getSum())
                                                          .sampleCount(BigDecimal.valueOf(d.getCount()).doubleValue());
            if (d.getPercentileValues() != null) {
                d.getPercentileValues().forEach(percentile -> {
                    if (Double.compare(0, percentile.getPercentile()) == 0) {
                        setBuilder.minimum(percentile.getValue());
                    } else if (Double.compare(100, percentile.getPercentile()) == 0) {
                        setBuilder.maximum(percentile.getValue());
                    }
                });
            }
            metricsMap.computeIfAbsent(getKey(namespace, metricName, millis, d.getAttributes()),
                    k -> new CollectedMetrics(namespace, metricName, millis, buildDimension(d.getAttributes())))
                      .setStatisticSet(setBuilder.build());
        });
    }

    private static void send(String namespace, List<MetricDatum> data) {
        if (data != null && !data.isEmpty()) {
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                                                               .namespace(namespace)
                                                               .metricData(data).build();
            PutMetricDataResponse response = CloudWatchClientProvider.get().putMetricData(request);
            if (!response.sdkHttpResponse().isSuccessful()) {
                log.info("Fail to export metrics to cloud watch. statusCode={}, msg={}.",
                        response.sdkHttpResponse().statusCode(), response.sdkHttpResponse().statusText().orElse(""));
            }
        }
    }

    private static String getKey(String nameSpace, String metricName, long epocheMillis, Attributes attributes) {
        StringBuilder builder = new StringBuilder(nameSpace + ':' + metricName + ':' + epocheMillis);
        if (attributes != null && !attributes.isEmpty()) {
            builder.append(':');
            List<String> attrs = new ArrayList<>();
            attributes.forEach((k, v) -> {
                attrs.add(k.getKey() + '=' + v);
            });
            attrs.sort(String::compareTo);
            attrs.forEach(s -> builder.append(s).append('&'));
        }
        return builder.toString();
    }

    private static MetricDatum build(CollectedMetrics c, List<Double> values, List<Double> counts) {
        return MetricDatum.builder()
                          .metricName(c.metricName)
                          .unit(StandardUnit.NONE)
                          .timestamp(c.instant)
                          .dimensions(c.dimensions)
                          .statisticValues(c.statisticSet)
                          .values(values)
                          .counts(counts).build();
    }

    private static void convertAndSend(String namespace, List<CollectedMetrics> list) {
        List<MetricDatum> data = new ArrayList<>();

        for (CollectedMetrics c : list) {

            List<Double> values = new ArrayList<>();
            List<Double> counts = new ArrayList<>();
            for (Map.Entry<Double, AtomicInteger> entry : c.metricPointCount.entrySet()) {
                values.add(entry.getKey());
                counts.add(Double.valueOf(entry.getValue().get()));

                if (values.size() >= MAX_METRIC_VALUE) {
                    data.add(build(c, values, counts));
                    values = new ArrayList<>();
                    counts = new ArrayList<>();

                    if (data.size() >= MAX_METRIC_DATUM) {
                        send(namespace, data);
                        data = new ArrayList<>();
                    }
                }
            }

            if (!values.isEmpty()) {
                data.add(build(c, values, counts));
                if (data.size() >= MAX_METRIC_DATUM) {
                    send(namespace, data);
                    data = new ArrayList<>();
                }
            }
        }

        if (!data.isEmpty()) {
            send(namespace, data);
        }
    }

    @Nullable
    @Override
    public AggregationTemporality getPreferredTemporality() {
        return AggregationTemporality.DELTA;
    }

    @Override
    protected CompletableResultCode doExport(Collection<MetricData> metrics) {
        Map<String, List<CollectedMetrics>> collectedMetrics = collectedMetricsByNamespace(metrics);
        METRICS_CACHE.collectAllByNamespace(collectedMetrics);

        collectedMetrics.forEach(CloudWatchMetricsExporter::convertAndSend);

        return CompletableResultCode.ofSuccess();
    }

    @Override
    protected CompletableResultCode doFlush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    /**
     * Cache for histogram data.
     */
    static final class MetricsCache {

        private final Map<String, CollectedMetrics>[] histogramCache = new ConcurrentHashMap[]{
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>()};

        private final AtomicInteger index = new AtomicInteger();

        private final ReadWriteLock[] locks = new ReadWriteLock[]{new ReentrantReadWriteLock(),
                new ReentrantReadWriteLock()};

        MetricsCache() {
        }

        <T> void recordHistogram(String namespace, String metricName, Attributes attributes, T data) {
            Double value = null;
            if (data instanceof Double) {
                value = (Double) data;
            } else if (data instanceof Long) {
                value = BigDecimal.valueOf((Long) data).doubleValue();
            }

            if (value == null) {
                return;
            }

            // do not need to record time.
            int currentIndex = index.get();
            Lock readLock = locks[currentIndex].readLock();
            while (!readLock.tryLock()) {
                currentIndex = index.get();
                readLock = locks[currentIndex].readLock();
            }

            try {
                long millis = 0L;
                histogramCache[currentIndex].computeIfAbsent(getKey(namespace, metricName, millis, attributes),
                        k ->
                                new CollectedMetrics(namespace, metricName, millis, buildDimension(attributes)))
                                            .addPoint(value);
            } finally {
                readLock.unlock();
            }
        }


        void collectAllByNamespace(Map<String, List<CollectedMetrics>> result) {
            synchronized (index) {
                int currentIndex = changeCache();
                Map<String, CollectedMetrics> cache = histogramCache[currentIndex];
                if (!cache.isEmpty()) {
                    Instant instant = Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(Clock.getDefault().now()));

                    Lock writeLock = locks[currentIndex].writeLock();
                    writeLock.lock();

                    try {
                        cache.values().forEach(metrics -> {
                            metrics.instant = instant;
                            result.computeIfAbsent(metrics.nameSpace, key -> new ArrayList<>()).add(metrics);
                        });
                        cache.clear();
                    } finally {
                        writeLock.unlock();
                    }
                }
            }
        }

        private int changeCache() {
            int current = index.get();
            boolean again = !index.compareAndSet(current, (current + 1) % 2);

            while (again) {
                current = index.get();
                again = !index.compareAndSet(current, (current + 1) % 2);
            }
            return current;
        }
    }
}
