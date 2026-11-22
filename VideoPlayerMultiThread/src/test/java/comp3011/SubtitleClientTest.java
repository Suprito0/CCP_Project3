package comp3011;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

// Test the retry logic without sending any requests to the real server.
class SubtitleClientTest {

    @Test
    void retriesAChunkWhenTheFirstRequestFails() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        SubtitleClient client = new SubtitleClient() {
            @Override
            protected List<Subtitle> sendAudio(File file, double offset) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("Server unavailable");
                }
                return List.of(new Subtitle(offset, offset + 2, "Hello"));
            }
        };

        List<Subtitle> subtitles = new ArrayList<>();
        AtomicReference<List<Subtitle>> received = new AtomicReference<>();
        client.addChunkIfAvailable(null, 20, subtitles, received::set);

        assertEquals(2, attempts.get());
        assertEquals(1, subtitles.size());
        assertEquals(20, received.get().get(0).start());
    }

    @Test
    void continuesToLaterChunksAfterTwoFailures() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        SubtitleClient client = new SubtitleClient() {
            @Override
            protected List<Subtitle> sendAudio(File file, double offset) {
                attempts.incrementAndGet();
                if (offset == 0) {
                    throw new IllegalStateException("Connection refused");
                }
                return List.of(new Subtitle(offset, offset + 2, "Next chunk"));
            }
        };

        List<Subtitle> subtitles = new ArrayList<>();
        List<List<Subtitle>> updates = new ArrayList<>();
        client.addChunkIfAvailable(null, 0, subtitles, updates::add);
        client.addChunkIfAvailable(null, 20, subtitles, updates::add);

        assertEquals(3, attempts.get());
        assertTrue(updates.get(0).isEmpty());
        assertEquals("Next chunk", updates.get(1).get(0).text());
    }

    @Test
    void stopsRetriesWhenTheThreadIsInterrupted() {
        SubtitleClient client = new SubtitleClient();
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class,
                    () -> client.addChunkIfAvailable(null, 0, new ArrayList<>(), lines -> {}));
        } finally {
            // JUnit reuses this thread for other tests.
            Thread.interrupted();
        }
    }
}