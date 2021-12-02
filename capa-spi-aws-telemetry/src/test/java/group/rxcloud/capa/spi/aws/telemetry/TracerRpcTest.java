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

import group.rxcloud.capa.telemetry.CapaTelemetryClient;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * @author: chenyijiang
 * @date: 2021/11/11 11:53
 */
public class TracerRpcTest {

    private Properties getFile() {
        try (InputStream in = new FileInputStream("D://context-test.properties")) {
            InputStreamReader inputStreamReader = new InputStreamReader(in, StandardCharsets.UTF_8);
            Properties properties = new Properties();
            properties.load(inputStreamReader);
            return properties;
        } catch (IOException e) {
            throw new IllegalArgumentException("context-test.properties" + " file not found.");
        }
    }

    //@Test
    public void server() throws InterruptedException {
        CapaTelemetryClient client = ClientHolder.getOrCreate();

        Tracer tracerAnother = client.buildTracer("Tracer.Demo.Rpc.Server").block();

        Properties properties = getFile();
        client.getContextPropagators().block().getTextMapPropagator()
              .extract(Context.current(), properties, new TextMapGetter<Properties>() {
                  @Override
                  public Iterable<String> keys(Properties carrier) {
                      return carrier.stringPropertyNames();
                  }

                  @Nullable
                  @Override
                  public String get(@Nullable Properties carrier, String key) {
                      String v = carrier.getProperty(key);
                      System.out.println("Server: extract " + key + '=' + v);
                      return v;
                  }
              }).makeCurrent();

        Span spanToLink = tracerAnother.spanBuilder("MyServerSpan")
                                       .setAttribute("type", "server")
                                       .startSpan();
        try (Scope serverScope = spanToLink.makeCurrent()) {
        } finally {
            spanToLink.end();
        }
        Thread.sleep(100 * 1000);
    }

    /**
     * Demo test with normal nested spans, attributes events and remote links,
     * including the batch event and the size event of CAT.
     *
     * @throws InterruptedException
     */
    //@Test
    public void client() throws InterruptedException {
        // default config.
        CapaTelemetryClient client = ClientHolder.getOrCreate();

        Tracer tracerAnother = client.buildTracer("Tracer.Demo.Rpc.Client").block();

        Span spanToLink = tracerAnother.spanBuilder("MyClientSpan")
                                       .setAttribute("type", "client")
                                       .startSpan();
        try (Scope clientScope = spanToLink.makeCurrent()) {
            Properties properties = getFile();

            client.getContextPropagators().block().getTextMapPropagator()
                  .inject(Context.current(), properties, (c, k, v) -> {
                      try (OutputStream out = new FileOutputStream("D://context-test.properties")) {
                          properties.setProperty(k, v);
                          properties.store(out, "Client: inject " + k + '=' + v);
                          out.flush();
                      } catch (IOException e) {
                          throw new IllegalArgumentException("context-test.properties" + " file not found.");
                      }
                      System.out.println("Client: inject " + k + '=' + v);
                  });

        } finally {
            spanToLink.end();
        }

        Thread.sleep(100 * 1000);
    }
}