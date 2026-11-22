package comp3011;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
// This runs on a background thread. It must not block JavaFX video playback.
public class SubtitleClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final long CHUNK_MICROSECONDS = 20_000_000L;

    // Keep the original method for code that loads subtitles from the beginning.
    public List<Subtitle> getSubtitles(File video, Consumer<List<Subtitle>> onChunk) throws Exception {
        return getSubtitles(video, 0, onChunk);
    }

    // Find the correct audio chunks for subtitles enabled mid-playback.
    public List<Subtitle> getSubtitles(File video, long startUs,
            Consumer<List<Subtitle>> onChunk) throws Exception {
        List<Subtitle> subtitles = new ArrayList<>();

        // Break the sound into small MP3 files to avoid the 25 MB API limit.
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video)) {

            // Convert the decoded audio to the format needed by Whisper.
            grabber.setSampleRate(16000);
            grabber.setAudioChannels(1);
            grabber.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);

            grabber.start();
            if (grabber.getAudioChannels() == 0) {
                return subtitles;
            }

            // Always read from the beginning so each run uses the same
            // 20-second chunk boundaries, even if S is pressed later.
            // Only send chunks that could still contain useful subtitles.
            long firstChunkToSend = Math.max(0, startUs / CHUNK_MICROSECONDS);

            FFmpegFrameRecorder recorder = null;
            File audioFile = null;
            long chunkStart = -1;
            long chunkNumber = 0;

            try {
                Frame frame;
                while (!Thread.currentThread().isInterrupted()
                        && (frame = grabber.grabSamples()) != null) {
                    if (frame.samples == null) {
                        continue;
                    }

                    long timestamp = grabber.getTimestamp();
                    if (recorder != null && timestamp - chunkStart >= CHUNK_MICROSECONDS) {
                        recorder.stop();
                        recorder.release();
                        recorder = null;

                        // Earlier chunks have already passed on screen. Avoid
                        // uploading them, but keep the chunk positions the same.
                        if (chunkNumber >= firstChunkToSend) {
                            addChunkIfAvailable(audioFile, chunkStart / 1_000_000.0,
                                    subtitles, onChunk);
                        }
                        chunkNumber++;
                        Files.deleteIfExists(audioFile.toPath());
                        audioFile = null;
                    }

                    if (recorder == null) {
                        chunkStart = timestamp;
                        audioFile = File.createTempFile("subtitle-audio-", ".mp3");
                        recorder = new FFmpegFrameRecorder(audioFile, 1);
                        recorder.setFormat("mp3");
                        recorder.setAudioCodec(avcodec.AV_CODEC_ID_MP3);
                        recorder.setSampleRate(16000);
                        recorder.setAudioChannels(1);
                        recorder.setAudioBitrate(48000);
                        recorder.start();
                    }
                    recorder.recordSamples(
                            frame.sampleRate,
                            frame.audioChannels,
                            frame.samples
                    );
                }

                if (recorder != null && !Thread.currentThread().isInterrupted()) {
                    recorder.stop();
                    recorder.release();
                    recorder = null;
                    if (chunkNumber >= firstChunkToSend) {
                        addChunkIfAvailable(audioFile, chunkStart / 1_000_000.0,
                                subtitles, onChunk);
                    }
                }
            } finally {
                if (recorder != null) {
                    try { recorder.stop(); } catch (Exception ignored) { }
                    try { recorder.release(); } catch (Exception ignored) { }
                }
                if (audioFile != null) {
                    Files.deleteIfExists(audioFile.toPath());
                }
            }
        }
        return subtitles;
    }

    // If the server is temporarily unavailable, try the chunk again.
    // A failed chunk should not prevent later audio from being transcribed.
    void addChunkIfAvailable(File audio, double offset, List<Subtitle> subtitles,
            Consumer<List<Subtitle>> onChunk) throws InterruptedException {
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Subtitle loading stopped");
            }

            try {
                subtitles.addAll(sendAudio(audio, offset));
                break;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw error;
            } catch (Exception error) {
                System.err.println("Subtitle chunk failed (attempt " + attempt + ")");
                System.err.println("Error type: " + error.getClass().getName());
                System.err.println("Error message: " + error.getMessage());

                error.printStackTrace();

                // Give the server a short chance to recover before retrying.
                if (attempt == 1) {
                    Thread.sleep(1000);
                }
            }
        }

        // Always update the controller, even if this chunk had no subtitles.
        onChunk.accept(List.copyOf(subtitles));
    }

    // Forward one MP3 chunk to our Spring Boot server, not directly to OpenAI.
    // Protected so a test can replace the network call with a fake response.
    protected List<Subtitle> sendAudio(File audio, double offset) throws Exception {
        String boundary = "VideoUpload" + System.nanoTime();
        byte[] header = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"audio.mp3\"\r\n"
                + "Content-Type: audio/mpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8080/api/v1/subtitles"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(150))
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(header),
                        HttpRequest.BodyPublishers.ofFile(audio.toPath()),
                        HttpRequest.BodyPublishers.ofByteArray(footer)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Subtitle server returned HTTP " + response.statusCode());
        }
        JsonNode lines = mapper.readTree(response.body());
        List<Subtitle> result = new ArrayList<>();
        for (JsonNode line : lines) {
            result.add(new Subtitle(line.path("start").asDouble() + offset,
                    line.path("end").asDouble() + offset, line.path("text").asText()));
        }
        return result;
    }
}