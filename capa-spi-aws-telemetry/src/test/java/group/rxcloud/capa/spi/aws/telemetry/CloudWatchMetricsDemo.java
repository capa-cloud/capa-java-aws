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
package group.rxcloud.capa.spi.aws.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundDoubleHistogram;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demo
 */
public class CloudWatchMetricsDemo {

    //@BeforeEach
    public void before() {
    }

    //@Test
    public void trace() {
        Tracer tracer = ClientHolder.getOrCreate().buildTracer("capa").block();
        Span rootSpan = tracer.spanBuilder("rootSpan")
                              .setSpanKind(SpanKind.SERVER)
                              .setAttribute("type", "root")
                              .startSpan();
        rootSpan.end();

        Span subSpan = tracer.spanBuilder("subSpan")
                             .setSpanKind(SpanKind.INTERNAL)
                             .setAttribute("type", "sub")
                             .setParent(Context.current().with(rootSpan))
                             .startSpan();
        subSpan.addEvent("myEvent", Attributes.of(AttributeKey.stringKey("key"), "value"));
        subSpan.end();
    }

    //@Test
    public void metrics() throws InterruptedException {
        // init client.
        Meter meter = ClientHolder.getOrCreate().buildMeter("capa").block();

        ExecutorService executorService = Executors.newFixedThreadPool(1);
        // gauge
        meter.gaugeBuilder("gauge_test")
             .ofLongs()
             .buildWithCallback(observer -> {
                 int value = new Random().nextInt(100);
                 System.out.println("gauge " + value);
                 observer.observe(value);
             });

        executorService.submit(() -> {
            // histogram
            Random random = new Random();

            DoubleHistogram doubleHistogram = meter.histogramBuilder("histogram_test")
                                                   .build();
            BoundDoubleHistogram boundDoubleHistogram = doubleHistogram
                    .bind(Attributes.of(AttributeKey.stringKey("type"), "histogram"));
            for (int i = 0; i < 5000; i++) {
                int num = random.nextInt(50);
                doubleHistogram.record(num);
                boundDoubleHistogram.record(num + 2);
                //System.out.println("histogram " + num);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });


        // counter
        // counter with different attrs will counting seperately. attrs will be attached to data points.
        LongCounter counter = meter.counterBuilder("counter_test")
                                   .build();

        BoundLongCounter boundLongCounter1 = counter.bind(
                Attributes.of(AttributeKey.stringKey("type"), "id_1"));
        BoundLongCounter boundLongCounter2 = counter.bind(Attributes.of(AttributeKey.stringKey("type"), "id_2"));

        LongUpDownCounter upDownCounter = meter.upDownCounterBuilder("counter_test")
                                               .build();
        BoundLongUpDownCounter boundLongUpDownCounter = upDownCounter
                .bind(Attributes.of(AttributeKey.stringKey("type"), "dec"));

        Random random = new Random();
        for (int i = 0; i < 120; i++) {
            int num1 = random.nextInt(5);
            int num2 = random.nextInt(5);
            boundLongCounter1.add(num1);
            boundLongCounter2.add(num2);
            boundLongUpDownCounter.add(num1);
            boundLongUpDownCounter.add(-num2);
            Thread.sleep(1000);
        }

        Thread.sleep(10 * 60 * 1000);
    }
}
