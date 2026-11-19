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

    public List<Subtitle> getSubtitles(File video, Consumer<List<Subtitle>> onChunk) throws Exception {
        List<Subtitle> subtitles = new ArrayList<>();
        // Break the sound into 20-second MP3 files to avoid the 25 MB API limit.
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(video)) {
            grabber.start();
            if (grabber.getAudioChannels() == 0) {
                return subtitles;
            }
            FFmpegFrameRecorder recorder = null;
            File audioFile = null;
            long chunkStart = -1;
            try {
                Frame frame;
                while (!Thread.currentThread().isInterrupted() && (frame = grabber.grabSamples()) != null) {
                    if (frame.samples == null) {
                        continue;
                    }
                    long timestamp = grabber.getTimestamp();
                    if (recorder != null && timestamp - chunkStart >= CHUNK_MICROSECONDS) {
                        recorder.stop();
                        recorder.release();
                        recorder = null;
                        subtitles.addAll(sendAudio(audioFile, chunkStart / 1_000_000.0));
                        onChunk.accept(List.copyOf(subtitles));
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
                    recorder.recordSamples(grabber.getSampleRate(), grabber.getAudioChannels(), frame.samples);
                }
                if (recorder != null && !Thread.currentThread().isInterrupted()) {
                    recorder.stop();
                    recorder.release();
                    recorder = null;
                    subtitles.addAll(sendAudio(audioFile, chunkStart / 1_000_000.0));
                    onChunk.accept(List.copyOf(subtitles));
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

    // Forward one MP3 chunk to our Spring Boot server, not directly to OpenAI.
    private List<Subtitle> sendAudio(File audio, double offset) throws Exception {
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