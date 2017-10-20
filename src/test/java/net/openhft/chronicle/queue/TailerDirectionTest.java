/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.queue;

import net.openhft.chronicle.bytes.BytesUtil;
import net.openhft.chronicle.core.OS;
import net.openhft.chronicle.core.time.SetTimeProvider;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/*
 * Created by Marcus Spiegel on 29/09/16.
 */
public class TailerDirectionTest extends ChronicleQueueTestBase {

    public static final int MILLIS = 86_400_000;
    private static final String TEST_MESSAGE_PREFIX = "Test entry: ";

    /**
     * Return a test message string for a specific ID.
     */
    private String testMessage(int id) {
        return TEST_MESSAGE_PREFIX + id;
    }

    /**
     * Add a test message with the given ExcerptAppender and return the index position of the entry
     *
     * @param appender ExceptAppender
     * @param msg      test message
     * @return index position of the entry
     */
    private long appendEntry(@NotNull final ExcerptAppender appender, String msg) {
        DocumentContext dc = appender.writingDocument();
        try {
            dc.wire().write().text(msg);
            return dc.index();
        } finally {
            dc.close();
        }
    }

    /**
     * Read next message, forward or backward, depending on the settings of the Tailer
     *
     * @param tailer ExcerptTailer
     * @return entry or null, if no entry available
     */
    private String readNextEntry(@NotNull final ExcerptTailer tailer) {
        DocumentContext dc = tailer.readingDocument();
        try {
            if (dc.isPresent()) {
                Object parent = dc.wire().parent();
                assert parent == tailer;
                return dc.wire().read().text();
            }
            return null;
        } finally {
            dc.close();
        }
    }

    //
    // Test procedure:
    // 1) Read in FORWARD direction 2 of the 4 entries
    // 2) Toggle Tailer direction and read in BACKWARD direction 2 entries
    // 3) Redo step 1)
    //
    @Test
    public void testTailerForwardBackwardRead() throws Exception {
        String basePath = OS.TARGET + "/tailerForwardBackward-" + System.nanoTime();

        ChronicleQueue queue = SingleChronicleQueueBuilder.binary(basePath)
                .testBlockSize()
                .rollCycle(RollCycles.HOURLY)
                .build();
        ExcerptAppender appender = queue.acquireAppender();
        ExcerptTailer tailer = queue.createTailer();

        //
        // Prepare test messages in queue
        //
        // Map of test messages with their queue index position
        HashMap<String, Long> msgIndexes = new HashMap<>();

        for (int i = 0; i < 4; i++) {
            String msg = testMessage(i);
            long idx = appendEntry(appender, msg);
            msgIndexes.put(msg, idx);
        }

        assertEquals("[Forward 1] Wrong message 0", testMessage(0), readNextEntry(tailer));
        assertEquals("[Forward 1] Wrong Tailer index after reading msg 0", msgIndexes.get(testMessage(1)).longValue(), tailer.index());
        assertEquals("[Forward 1] Wrong message 1", testMessage(1), readNextEntry(tailer));
        assertEquals("[Forward 1] Wrong Tailer index after reading msg 1", msgIndexes.get(testMessage(2)).longValue(), tailer.index());

        tailer.direction(TailerDirection.BACKWARD);

        assertEquals("[Backward] Wrong message 2", testMessage(2), readNextEntry(tailer));
        assertEquals("[Backward] Wrong Tailer index after reading msg 2", msgIndexes.get(testMessage(1)).longValue(), tailer.index());
        assertEquals("[Backward] Wrong message 1", testMessage(1), readNextEntry(tailer));
        assertEquals("[Backward] Wrong Tailer index after reading msg 1", msgIndexes.get(testMessage(0)).longValue(), tailer.index());
        assertEquals("[Backward] Wrong message 0", testMessage(0), readNextEntry(tailer));

        String res = readNextEntry(tailer);
        assertTrue("Backward: res is" + res, res == null);

        tailer.direction(TailerDirection.FORWARD);

        res = readNextEntry(tailer);
        assertTrue("Forward: res is" + res, res == null);

        assertEquals("[Forward 2] Wrong message 0", testMessage(0), readNextEntry(tailer));
        assertEquals("[Forward 2] Wrong Tailer index after reading msg 0", msgIndexes.get(testMessage(1)).longValue(), tailer.index());
        assertEquals("[Forward 2] Wrong message 1", testMessage(1), readNextEntry(tailer));
        assertEquals("[Forward 2] Wrong Tailer index after reading msg 1", msgIndexes.get(testMessage(2)).longValue(), tailer.index());

        queue.close();
    }

    @Test
    public void testTailerBackwardsReadBeyondCycle() throws Exception {
        File basePath = DirectoryUtils.tempDir("tailerForwardBackwardBeyondCycle");
        SetTimeProvider timeProvider = new SetTimeProvider();
        ChronicleQueue queue = SingleChronicleQueueBuilder.binary(basePath)
                .testBlockSize()
                .timeProvider(timeProvider)
                .build();
        ExcerptAppender appender = queue.acquireAppender();

        //
        // Prepare test messages in queue
        //
        // List of test messages with their queue index position
        List<Long> indexes = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        for (int d = -7; d <= 0; d++) {
            if (d == -5 || d == -4)
                continue; // nothing on those days.
            timeProvider.currentTimeMillis(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(d));
            for (int i = 0; i < 3; i++) {
                String msg = testMessage(indexes.size());
                long idx = appendEntry(appender, msg);
                messages.add(msg);
                indexes.add(idx);
            }
        }
        ExcerptTailer tailer = queue.createTailer()
                .direction(TailerDirection.BACKWARD)
                .toEnd();

        for (int i = indexes.size() - 1; i >= 0; i--) {
            long index = indexes.get(i);
            String msg = messages.get(i);

            assertEquals("[Backward] Wrong index " + i, index, tailer.index());
            assertEquals("[Backward] Wrong message " + i, msg, readNextEntry(tailer));
        }
        queue.close();
    }

    @Override
    @After
    public void checkRegisteredBytes() {
        BytesUtil.checkRegisteredBytes();
    }
}
