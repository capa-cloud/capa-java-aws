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

import group.rxcloud.capa.spi.telemetry.CapaDoubleHistogramSpi;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundDoubleHistogram;
import io.opentelemetry.context.Context;

/**
 */
public class CloundWatchDoubleHistogram extends CapaDoubleHistogramSpi implements BoundDoubleHistogram {

    private Attributes bind = Attributes.empty();

    public CloundWatchDoubleHistogram(String meterName, String schemaUrl, String version, String name,
                                      String description, String unit) {
        super(meterName, schemaUrl, version, name, description, unit);
        if (schemaUrl != null) {
            bind = bind.toBuilder().put("schemaUrl", schemaUrl).build();
        }
        if (version != null) {
            bind = bind.toBuilder().put("version", version).build();
        }

    }

    @Override
    public void record(double value) {
        CloudWatchMetricsExporter.recordHistogram(meterName, name, bind, value);
    }

    @Override
    public void record(double value, Context context) {
        record(value);
    }

    @Override
    public void unbind() {
        bind = Attributes.empty();
    }

    @Override
    public void record(double value, Attributes attributes) {
        CloudWatchMetricsExporter.recordHistogram(meterName, name, attributes, value);
    }

    @Override
    public void record(double value, Attributes attributes, Context context) {
        record(value, attributes);
    }

    @Override
    public BoundDoubleHistogram bind(Attributes attributes) {
        CloundWatchDoubleHistogram copy = new CloundWatchDoubleHistogram(meterName, schemaUrl, version, name, description, unit);
        if (attributes != null) {
            copy.bind = copy.bind.toBuilder().putAll(attributes).build();
        }
        return copy;
    }
}
