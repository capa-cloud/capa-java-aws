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
package group.rxcloud.capa.spi.aws.log.handle;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChunkQueue {
    private final int maxBytes;
    private final Queue<CompressedChunk> queue = new ConcurrentLinkedQueue<>();
    private int bytes;

    public ChunkQueue(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int drainTo(LinkedList<CompressedChunk> collection, int maxElements, int maxSize) {
        int elementCount = 0;
        int totalSize = 0;
        while (elementCount < maxElements) {
            CompressedChunk element = queue.peek();
            if (element == null || element.getSize() + totalSize > maxSize) {
                break;
            } else {
                queue.poll();
                collection.add(element);
                elementCount++;
                totalSize += element.getSize();
            }
        }
        return totalSize;
    }

    public synchronized boolean offer(CompressedChunk chunk) {
        int length = chunk.getSize();
        if (bytes >= maxBytes) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {

            }
        }
        bytes += length;
        return queue.offer(chunk);
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}

